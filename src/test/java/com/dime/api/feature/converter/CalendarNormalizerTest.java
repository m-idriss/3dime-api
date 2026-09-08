package com.dime.api.feature.converter;

import com.dime.api.feature.shared.exception.ProcessingException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarNormalizerTest {

    private final CalendarNormalizer normalizer = new CalendarNormalizer();

    @Test
    void normalizesMultipleTypedEventsAndRegeneratesServerOwnedCalendar() {
        String providerIcs = """
                ```ics
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//Untrusted provider//EN
                BEGIN:VEVENT
                SUMMARY:Team planning
                DTSTART;TZID=Europe/Paris:20261025T090000
                DTEND;TZID=Europe/Paris:20261025T100000
                LOCATION:Paris
                END:VEVENT
                BEGIN:VEVENT
                SUMMARY:Public holiday
                DTSTART;VALUE=DATE:20261111
                DTEND;VALUE=DATE:20261112
                END:VEVENT
                END:VCALENDAR
                ```
                """;

        CalendarNormalizer.NormalizedCalendar result = normalizer.normalize(providerIcs, "Europe/Paris");

        assertEquals(2, result.eventCount());
        assertTrue(result.warnings().isEmpty());
        assertTrue(result.icsContent().contains("PRODID:-//PhotoCalia//Validated Calendar//EN"));
        assertFalse(result.icsContent().contains("Untrusted provider"));
        assertTrue(result.icsContent().contains("DTSTART;TZID=Europe/Paris:20261025T090000"));
        assertTrue(result.icsContent().contains("DTSTART;VALUE=DATE:20261111"));
        assertEquals(2, result.icsContent().split("BEGIN:VEVENT", -1).length - 1);
    }

    @Test
    void assumesRequestedTimezoneForFloatingProviderTimesAndReturnsWarning() {
        String providerIcs = calendar("""
                BEGIN:VEVENT
                SUMMARY:School meeting
                DTSTART:20260915T183000
                DTEND:20260915T193000
                END:VEVENT
                """);

        CalendarNormalizer.NormalizedCalendar result = normalizer.normalize(providerIcs, "America/Toronto");

        assertEquals(List.of("timezone_assumed:event[0]:America/Toronto"), result.warnings());
        assertTrue(result.icsContent().contains("DTSTART;TZID=America/Toronto:20260915T183000"));
    }

    @Test
    void rejectsMissingRequiredFieldsWithFieldLevelReason() {
        ProcessingException exception = assertThrows(ProcessingException.class,
                () -> normalizer.normalize(calendar("""
                        BEGIN:VEVENT
                        DTSTART:20260915T183000Z
                        END:VEVENT
                        """), "UTC"));

        assertTrue(exception.getMessage().contains("event[0].summary"));
    }

    @Test
    void rejectsEndBeforeStart() {
        ProcessingException exception = assertThrows(ProcessingException.class,
                () -> normalizer.normalize(calendar("""
                        BEGIN:VEVENT
                        SUMMARY:Impossible event
                        DTSTART:20260915T193000Z
                        DTEND:20260915T183000Z
                        END:VEVENT
                        """), "UTC"));

        assertTrue(exception.getMessage().contains("end must be after"));
    }

    @Test
    void escapesProviderTextInsteadOfAllowingPropertyInjection() {
        String providerIcs = calendar("""
                BEGIN:VEVENT
                SUMMARY:Safe title\\nATTENDEE:mailto:unexpected@example.com
                DTSTART:20260915T183000Z
                DTEND:20260915T193000Z
                END:VEVENT
                """);

        CalendarNormalizer.NormalizedCalendar result = normalizer.normalize(providerIcs, "UTC");

        assertTrue(result.icsContent().contains("SUMMARY:Safe title\\nATTENDEE:mailto:unexpected@example.com"));
        assertFalse(result.icsContent().contains("\r\nATTENDEE:"));
    }

    @Test
    void rejectsUnknownTimezoneAndMalformedOutput() {
        assertThrows(ProcessingException.class,
                () -> normalizer.normalize(calendar("""
                        BEGIN:VEVENT
                        SUMMARY:Bad zone
                        DTSTART;TZID=Mars/Olympus:20260915T183000
                        END:VEVENT
                        """), "UTC"));
        assertThrows(ProcessingException.class, () -> normalizer.normalize("BEGIN:VCALENDAR", "UTC"));
    }

    @Test
    void rejectsDuplicateSingleValueFields() {
        ProcessingException exception = assertThrows(ProcessingException.class,
                () -> normalizer.normalize(calendar("""
                        BEGIN:VEVENT
                        SUMMARY:First title
                        SUMMARY:Second title
                        DTSTART:20260915T183000Z
                        END:VEVENT
                        """), "UTC"));

        assertTrue(exception.getMessage().contains("duplicate summary"));
    }

    @Test
    void foldsLongUtf8ContentAtRfc5545ByteLimit() {
        String providerIcs = calendar("""
                BEGIN:VEVENT
                SUMMARY:Réunion très importante avec une équipe internationale et plusieurs participants éloignés
                DTSTART:20260915T183000Z
                DTEND:20260915T193000Z
                END:VEVENT
                """);

        String result = normalizer.normalize(providerIcs, "UTC").icsContent();

        for (String line : result.split("\\r\\n")) {
            assertTrue(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 75, line);
        }
        assertTrue(result.contains("\r\n "));
    }

    private String calendar(String events) {
        return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + events.strip() + "\r\nEND:VCALENDAR\r\n";
    }
}
