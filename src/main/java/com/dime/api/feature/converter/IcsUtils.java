package com.dime.api.feature.converter;

/**
 * Shared utility for ICS content post-processing.
 */
final class IcsUtils {

    private IcsUtils() {
    }

    /**
     * Strips markdown code block fences (```ics or ```) from AI-generated ICS text.
     *
     * @param text raw text returned by an AI model
     * @return cleaned ICS string, or {@code null} if input is {@code null}
     */
    static String cleanIcs(String text) {
        if (text == null)
            return null;
        return text.replaceAll("```(?:ics)?\\s*[\\r\\n]|```", "").trim();
    }
}
