package com.dime.api.feature.converter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CalendarAiProviderContractTest {

    @Test
    void geminiAndClaudeExposeTheSameProviderBoundary() {
        CalendarAiProvider gemini = new GeminiService();
        CalendarAiProvider claude = new ClaudeService();

        assertInstanceOf(CalendarAiProvider.class, gemini);
        assertInstanceOf(CalendarAiProvider.class, claude);
        assertEquals("gemini", gemini.name());
        assertEquals("claude", claude.name());
    }
}
