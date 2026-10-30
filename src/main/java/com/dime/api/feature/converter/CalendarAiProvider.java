package com.dime.api.feature.converter;

import java.io.IOException;

/**
 * Common boundary for every AI calendar provider.
 *
 * <p>Provider output is deliberately treated as untrusted. Callers must pass it
 * through {@link CalendarNormalizer} before returning it to clients.</p>
 */
public interface CalendarAiProvider {

    String name();

    String generateCalendar(ConverterRequest request) throws IOException;
}
