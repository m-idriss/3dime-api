package com.dime.api.feature.converter;

import com.dime.api.feature.shared.exception.ProcessingException;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Converts untrusted provider ICS into a typed model, validates it and emits a
 * fresh server-owned ICS document.
 */
@ApplicationScoped
public class CalendarNormalizer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final int MAX_PROVIDER_OUTPUT = 1_000_000;
    private static final int MAX_EVENTS = 500;
    private static final Set<String> SINGLE_VALUE_PROPERTIES =
            Set.of("SUMMARY", "DTSTART", "DTEND", "UID", "RRULE");

    public NormalizedCalendar normalize(String providerOutput, String requestedTimeZone) {
        if (providerOutput == null || providerOutput.isBlank() || "null".equalsIgnoreCase(providerOutput.trim())) {
            throw invalid("no_events_detected", "No calendar events were returned by the AI provider.");
        }
        if (providerOutput.length() > MAX_PROVIDER_OUTPUT) {
            throw invalid("provider_output_too_large", "The AI provider returned too much calendar data.");
        }

        String cleaned = IcsUtils.cleanIcs(providerOutput);
        if (!cleaned.startsWith("BEGIN:VCALENDAR") || !cleaned.endsWith("END:VCALENDAR")) {
            throw invalid("invalid_ics_format", "The AI provider returned malformed calendar data.");
        }

        ZoneId defaultZone = parseZone(requestedTimeZone == null || requestedTimeZone.isBlank()
                ? "UTC" : requestedTimeZone);
        List<String> lines = unfold(cleaned);
        List<CalendarEventModel> events = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Property> current = null;

        for (String line : lines) {
            if ("BEGIN:VEVENT".equalsIgnoreCase(line)) {
                if (current != null) {
                    throw invalid("nested_event", "The AI provider returned nested calendar events.");
                }
                current = new LinkedHashMap<>();
                continue;
            }
            if ("END:VEVENT".equalsIgnoreCase(line)) {
                if (current == null) {
                    throw invalid("unexpected_event_end", "The AI provider returned malformed calendar data.");
                }
                events.add(toEvent(current, defaultZone, events.size(), warnings));
                if (events.size() > MAX_EVENTS) {
                    throw invalid("too_many_events", "The AI provider returned too many events.");
                }
                current = null;
                continue;
            }
            if (current != null && !line.isBlank()) {
                Property property = parseProperty(line);
                Property previous = current.putIfAbsent(property.name(), property);
                if (previous != null && SINGLE_VALUE_PROPERTIES.contains(property.name())) {
                    throw invalid("duplicate_event_field",
                            "The AI provider returned duplicate " + property.name().toLowerCase(Locale.ROOT)
                                    + " values.");
                }
            }
        }

        if (current != null || events.isEmpty()) {
            throw invalid("invalid_ics_format", "The AI provider returned incomplete calendar data.");
        }
        return new NormalizedCalendar(serialize(events), List.copyOf(warnings), events.size());
    }

    private CalendarEventModel toEvent(Map<String, Property> properties, ZoneId defaultZone, int index,
            List<String> warnings) {
        String title = requiredText(properties, "SUMMARY", index);
        EventTimeResult start = parseTime(required(properties, "DTSTART", index), defaultZone, index);
        Property rawEnd = properties.get("DTEND");
        CalendarEventModel.EventTime end = rawEnd == null ? null : parseTime(rawEnd, defaultZone, index).time();
        validateEnd(start.time(), end, index);

        if (start.assumedTimeZone()) {
            warnings.add("timezone_assumed:event[" + index + "]:" + defaultZone.getId());
        }

        String recurrence = optionalRaw(properties, "RRULE");
        if (recurrence != null && (!recurrence.startsWith("FREQ=") || recurrence.contains("\r")
                || recurrence.contains("\n") || recurrence.length() > 500)) {
            throw invalid("invalid_recurrence", "event[" + index + "].recurrence is invalid.");
        }

        String location = optionalText(properties, "LOCATION");
        String description = optionalText(properties, "DESCRIPTION");
        String uid = optionalText(properties, "UID");
        if (uid == null || uid.isBlank()) {
            String seed = title + "|" + start.time() + "|" + (location == null ? "" : location);
            uid = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)) + "@photocalia.com";
        }

        return new CalendarEventModel(title, start.time(), end, location, description, recurrence, uid);
    }

    private EventTimeResult parseTime(Property property, ZoneId defaultZone, int index) {
        String value = property.value().trim();
        try {
            if ("DATE".equalsIgnoreCase(property.parameters().get("VALUE")) || value.matches("\\d{8}")) {
                return new EventTimeResult(CalendarEventModel.EventTime.allDay(LocalDate.parse(value, DATE)), false);
            }

            boolean utc = value.endsWith("Z");
            String normalized = utc ? value.substring(0, value.length() - 1) : value;
            LocalDateTime dateTime = LocalDateTime.parse(normalized, DATE_TIME);
            String tzid = property.parameters().get("TZID");
            ZoneId zone = utc ? ZoneOffset.UTC : tzid == null ? defaultZone : parseZone(tzid);
            return new EventTimeResult(CalendarEventModel.EventTime.timed(dateTime, zone, utc),
                    !utc && tzid == null);
        } catch (DateTimeParseException e) {
            throw invalid("invalid_date_time", "event[" + index + "]." + property.name().toLowerCase(Locale.ROOT)
                    + " is invalid.");
        }
    }

    private void validateEnd(CalendarEventModel.EventTime start, CalendarEventModel.EventTime end, int index) {
        if (end == null) {
            return;
        }
        if (start.allDay() != end.allDay()) {
            throw invalid("inconsistent_event_time", "event[" + index + "] mixes all-day and timed values.");
        }
        boolean valid;
        if (start.allDay()) {
            valid = end.date().isAfter(start.date());
        } else {
            valid = end.dateTime().atZone(end.zone()).toInstant()
                    .isAfter(start.dateTime().atZone(start.zone()).toInstant());
        }
        if (!valid) {
            throw invalid("invalid_event_range", "event[" + index + "].end must be after its start.");
        }
    }

    private List<String> unfold(String ics) {
        List<String> unfolded = new ArrayList<>();
        for (String line : ics.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && !unfolded.isEmpty()) {
                int last = unfolded.size() - 1;
                unfolded.set(last, unfolded.get(last) + line.substring(1));
            } else {
                unfolded.add(line);
            }
        }
        return unfolded;
    }

    private Property parseProperty(String line) {
        int colon = line.indexOf(':');
        if (colon <= 0) {
            throw invalid("invalid_ics_property", "The AI provider returned a malformed calendar property.");
        }
        String[] head = line.substring(0, colon).split(";");
        String name = head[0].toUpperCase(Locale.ROOT);
        Map<String, String> parameters = new LinkedHashMap<>();
        for (int i = 1; i < head.length; i++) {
            int equals = head[i].indexOf('=');
            if (equals > 0) {
                parameters.put(head[i].substring(0, equals).toUpperCase(Locale.ROOT),
                        head[i].substring(equals + 1));
            }
        }
        return new Property(name, parameters, line.substring(colon + 1));
    }

    private String serialize(List<CalendarEventModel> events) {
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//PhotoCalia//Validated Calendar//EN");
        lines.add("CALSCALE:GREGORIAN");
        for (CalendarEventModel event : events) {
            lines.add("BEGIN:VEVENT");
            lines.add("UID:" + escape(event.uid()));
            lines.add("SUMMARY:" + escape(event.title()));
            lines.add(formatTime("DTSTART", event.start()));
            if (event.end() != null) {
                lines.add(formatTime("DTEND", event.end()));
            }
            if (event.location() != null && !event.location().isBlank()) {
                lines.add("LOCATION:" + escape(event.location()));
            }
            if (event.description() != null && !event.description().isBlank()) {
                lines.add("DESCRIPTION:" + escape(event.description()));
            }
            if (event.recurrence() != null) {
                lines.add("RRULE:" + event.recurrence());
            }
            lines.add("END:VEVENT");
        }
        lines.add("END:VCALENDAR");
        return lines.stream()
                .map(this::fold)
                .reduce((left, right) -> left + "\r\n" + right)
                .orElse("") + "\r\n";
    }

    /**
     * RFC 5545 content lines are limited to 75 octets. Continuations begin with
     * one space, which is included in the next line's limit.
     */
    private String fold(String line) {
        StringBuilder folded = new StringBuilder();
        int bytesOnLine = 0;
        int contentLimit = 75;
        for (int offset = 0; offset < line.length();) {
            int codePoint = line.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int byteCount = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytesOnLine > 0 && bytesOnLine + byteCount > contentLimit) {
                folded.append("\r\n ");
                bytesOnLine = 0;
                contentLimit = 74;
            }
            folded.append(character);
            bytesOnLine += byteCount;
            offset += Character.charCount(codePoint);
        }
        return folded.toString();
    }

    private String formatTime(String name, CalendarEventModel.EventTime time) {
        if (time.allDay()) {
            return name + ";VALUE=DATE:" + DATE.format(time.date());
        }
        String formatted = DATE_TIME.format(time.dateTime());
        if (time.utc()) {
            return name + ":" + formatted + "Z";
        }
        return name + ";TZID=" + time.zone().getId() + ":" + formatted;
    }

    private String requiredText(Map<String, Property> properties, String name, int index) {
        String value = unescape(required(properties, name, index).value()).trim();
        if (value.isEmpty() || value.length() > 500) {
            throw invalid("invalid_event_title", "event[" + index + "].title is empty or too long.");
        }
        return value;
    }

    private Property required(Map<String, Property> properties, String name, int index) {
        Property property = properties.get(name);
        if (property == null || property.value().isBlank()) {
            throw invalid("missing_event_field", "event[" + index + "]." + name.toLowerCase(Locale.ROOT)
                    + " is required.");
        }
        return property;
    }

    private String optionalRaw(Map<String, Property> properties, String name) {
        Property property = properties.get(name);
        return property == null || property.value().isBlank() ? null : property.value().trim();
    }

    private String optionalText(Map<String, Property> properties, String name) {
        String value = optionalRaw(properties, name);
        return value == null ? null : unescape(value);
    }

    private String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\N", "\n")
                .replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\");
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\r\n", "\\n")
                .replace("\r", "\\n").replace("\n", "\\n")
                .replace(";", "\\;").replace(",", "\\,");
    }

    private ZoneId parseZone(String value) {
        try {
            return ZoneId.of(value);
        } catch (DateTimeException e) {
            throw invalid("invalid_timezone", "The requested calendar timezone is invalid.");
        }
    }

    private ProcessingException invalid(String reason, String message) {
        return new ProcessingException(message, Map.of("reason", reason));
    }

    public record NormalizedCalendar(String icsContent, List<String> warnings, int eventCount) {
    }

    private record Property(String name, Map<String, String> parameters, String value) {
    }

    private record EventTimeResult(CalendarEventModel.EventTime time, boolean assumedTimeZone) {
    }
}
