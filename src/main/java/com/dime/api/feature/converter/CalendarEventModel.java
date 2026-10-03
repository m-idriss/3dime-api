package com.dime.api.feature.converter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Provider-independent, validated calendar event.
 */
record CalendarEventModel(
        String title,
        CalendarEventModel.EventTime start,
        CalendarEventModel.EventTime end,
        String location,
        String description,
        String recurrence,
        String uid) {

    record EventTime(LocalDate date, LocalDateTime dateTime, ZoneId zone, boolean utc) {
        static EventTime allDay(LocalDate date) {
            return new EventTime(date, null, null, false);
        }

        static EventTime timed(LocalDateTime dateTime, ZoneId zone, boolean utc) {
            return new EventTime(null, dateTime, zone, utc);
        }

        boolean allDay() {
            return date != null;
        }
    }
}
