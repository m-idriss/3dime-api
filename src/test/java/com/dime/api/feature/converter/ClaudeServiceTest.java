package com.dime.api.feature.converter;

import com.dime.api.feature.shared.exception.ExternalServiceException;
import com.dime.api.feature.shared.exception.ProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ClaudeServiceTest {

    private ClaudeService service;
    private ClaudeClient mockClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        service = new ClaudeService();
        mockClient = mock(ClaudeClient.class);
        service.claudeClient = mockClient;
        service.objectMapper = objectMapper;
        service.apiKey = Optional.of("test-api-key");
        service.modelName = "claude-3-5-sonnet-20241022";
        service.baseMessageTemplate = "Convert these images to ICS. Today is {today}, timezone is {tz}.";
        service.systemPrompt = "You are a calendar assistant.";
    }

    // --- cleanIcs ---

    @Test
    public void testCleanIcs_removesMarkdownIcsBlock() {
        String dirty = "```ics\nBEGIN:VCALENDAR\nSUMMARY:Test\nEND:VCALENDAR\n```";
        String cleaned = service.cleanIcs(dirty);
        assertNotNull(cleaned);
        assertFalse(cleaned.contains("```"));
        assertTrue(cleaned.contains("BEGIN:VCALENDAR"));
    }

    @Test
    public void testCleanIcs_removesGenericCodeBlock() {
        String dirty = "```\nBEGIN:VCALENDAR\nEND:VCALENDAR\n```";
        String cleaned = service.cleanIcs(dirty);
        assertFalse(cleaned.contains("```"));
        assertTrue(cleaned.contains("BEGIN:VCALENDAR"));
    }

    @Test
    public void testCleanIcs_alreadyClean() {
        String clean = "BEGIN:VCALENDAR\nSUMMARY:Test\nEND:VCALENDAR";
        assertEquals(clean, service.cleanIcs(clean));
    }

    @Test
    public void testCleanIcs_null() {
        assertNull(service.cleanIcs(null));
    }

    // --- generateIcs: API key validation ---

    @Test
    public void testGenerateIcs_missingApiKey_throwsExternalServiceException() {
        service.apiKey = Optional.empty();
        assertThrows(ExternalServiceException.class, () -> service.generateIcs(buildRequestWithBase64Image()));
    }

    @Test
    public void testGenerateIcs_blankApiKey_throwsExternalServiceException() {
        service.apiKey = Optional.of("   ");
        assertThrows(ExternalServiceException.class, () -> service.generateIcs(buildRequestWithBase64Image()));
    }

    // --- generateIcs: successful response ---

    @Test
    public void testGenerateIcs_validResponse_returnsIcsContent() {
        String icsContent = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Test\nEND:VEVENT\nEND:VCALENDAR";
        when(mockClient.createMessage(any(), any(), any())).thenReturn(buildSuccessResponse(icsContent));

        ConverterRequest request = buildRequestWithBase64Image();
        request.currentDate = "2026-02-22";
        request.timeZone = "UTC";

        String result = service.generateIcs(request);

        assertNotNull(result);
        assertTrue(result.contains("BEGIN:VCALENDAR"));
    }

    @Test
    public void testGenerateIcs_stripsMarkdownFromResponse() {
        String icsContent = "```ics\nBEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Test\nEND:VEVENT\nEND:VCALENDAR\n```";
        when(mockClient.createMessage(any(), any(), any())).thenReturn(buildSuccessResponse(icsContent));

        String result = service.generateIcs(buildRequestWithBase64Image());

        assertFalse(result.contains("```"));
        assertTrue(result.startsWith("BEGIN:VCALENDAR"));
    }

    @Test
    public void testGenerateIcs_nullCurrentDateAndTimezone_usesDefaults() {
        String icsContent = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Test\nEND:VEVENT\nEND:VCALENDAR";
        when(mockClient.createMessage(any(), any(), any())).thenReturn(buildSuccessResponse(icsContent));

        ConverterRequest request = buildRequestWithBase64Image();
        // currentDate and timeZone intentionally left null

        assertDoesNotThrow(() -> service.generateIcs(request));
    }

    // --- generateIcs: error cases ---

    @Test
    public void testGenerateIcs_apiErrorField_throwsExternalServiceException() {
        ObjectNode errorResponse = objectMapper.createObjectNode();
        errorResponse.putObject("error")
                .put("type", "authentication_error")
                .put("message", "Invalid API key");
        when(mockClient.createMessage(any(), any(), any())).thenReturn(errorResponse);

        assertThrows(ExternalServiceException.class, () -> service.generateIcs(buildRequestWithBase64Image()));
    }

    @Test
    public void testGenerateIcs_contentBlocked_throwsProcessingException() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("stop_reason", "max_tokens");
        response.putArray("content").addObject().put("type", "text").put("text", "Partial");
        when(mockClient.createMessage(any(), any(), any())).thenReturn(response);

        assertThrows(ProcessingException.class, () -> service.generateIcs(buildRequestWithBase64Image()));
    }

    @Test
    public void testGenerateIcs_clientThrows_throwsExternalServiceException() {
        when(mockClient.createMessage(any(), any(), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThrows(ExternalServiceException.class, () -> service.generateIcs(buildRequestWithBase64Image()));
    }

    @Test
    public void testGenerateIcs_emptyContent_throwsProcessingException() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("stop_reason", "end_turn");
        response.putArray("content"); // empty content array
        when(mockClient.createMessage(any(), any(), any())).thenReturn(response);

        assertThrows(ProcessingException.class, () -> service.generateIcs(buildRequestWithBase64Image()));
    }

    // --- generateIcs: image handling ---

    @Test
    public void testGenerateIcs_urlBasedImage_skippedWithWarning() {
        String icsContent = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Test\nEND:VEVENT\nEND:VCALENDAR";
        when(mockClient.createMessage(any(), any(), any())).thenReturn(buildSuccessResponse(icsContent));

        ConverterRequest request = new ConverterRequest();
        ConverterRequest.ImageFile file = new ConverterRequest.ImageFile();
        file.url = "https://example.com/image.png"; // URL-based images are skipped
        request.files = List.of(file);

        // Should not throw — URL images are skipped with a log warning
        assertDoesNotThrow(() -> service.generateIcs(request));
    }

    @Test
    public void testGenerateIcs_multipleMixedFiles_processesBase64Only() {
        String icsContent = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Test\nEND:VEVENT\nEND:VCALENDAR";
        when(mockClient.createMessage(any(), any(), any())).thenReturn(buildSuccessResponse(icsContent));

        ConverterRequest request = new ConverterRequest();
        ConverterRequest.ImageFile urlFile = new ConverterRequest.ImageFile();
        urlFile.url = "https://example.com/image.png";
        ConverterRequest.ImageFile base64File = new ConverterRequest.ImageFile();
        base64File.dataUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==";
        request.files = List.of(urlFile, base64File);

        String result = service.generateIcs(request);
        assertNotNull(result);
        verify(mockClient, times(1)).createMessage(any(), any(), any());
    }

    // --- helpers ---

    private ConverterRequest buildRequestWithBase64Image() {
        ConverterRequest request = new ConverterRequest();
        ConverterRequest.ImageFile file = new ConverterRequest.ImageFile();
        file.dataUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==";
        request.files = List.of(file);
        return request;
    }

    private ObjectNode buildSuccessResponse(String icsContent) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("stop_reason", "end_turn");
        response.putArray("content").addObject()
                .put("type", "text")
                .put("text", icsContent);
        return response;
    }
}
