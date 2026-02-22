package com.dime.api.feature.converter;

import com.dime.api.feature.shared.exception.ExternalServiceException;
import com.dime.api.feature.shared.exception.ProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
public class GeminiServiceTest {
    @Inject
    GeminiService geminiService;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        geminiService.geminiClient = mock(GeminiClient.class);
        geminiService.objectMapper = objectMapper;
        geminiService.apiKeyJson = Optional.of("{}\n");
    }

    @Test
    public void testCleanIcs() {
        String dirty = "BEGIN:VCALENDAR\nSUMMARY:Test\nEND:VCALENDAR";
        String cleaned = geminiService.cleanIcs(dirty);
        assertNotNull(cleaned);
        assertTrue(cleaned.contains("VCALENDAR"));
    }

    @Test
    public void testGetAccessTokenHandlesException() {
        geminiService.apiKeyJson = Optional.empty();
        // Reset cachedCredentials to ensure code path is exercised
        try {
            var field = GeminiService.class.getDeclaredField("cachedCredentials");
            field.setAccessible(true);
            field.set(geminiService, null);
        } catch (Exception e) {
            fail("Reflection error resetting cachedCredentials: " + e.getMessage());
        }
        // Should not throw, should fallback to application default credentials or
        // return a token
        try {
            String token = geminiService.getAccessToken();
            assertNotNull(token);
        } catch (IOException e) {
            // Acceptable if environment does not provide application default credentials
            assertTrue(e.getMessage().contains("credentials"));
        }
    }

    // --- Behavior tests (plain unit tests, no Quarkus context needed) ---

    @Nested
    class BehaviorTest {

        private GeminiService service;
        private GeminiClient mockClient;
        private final ObjectMapper mapper = new ObjectMapper();

        @BeforeEach
        void setup() throws IOException {
            service = spy(new GeminiService());
            mockClient = mock(GeminiClient.class);
            service.geminiClient = mockClient;
            service.objectMapper = mapper;
            service.modelName = "gemini-test-model";
            service.baseMessageTemplate = "Convert images. Today is {today}, tz={tz}.";
            service.systemPrompt = "You are a calendar assistant.";
            service.apiKeyJson = Optional.of("{}");
            doReturn("fake-token").when(service).getAccessToken();
        }

        // --- cleanIcs ---

        @Test
        void testCleanIcs_removesMarkdownIcsBlock() {
            String dirty = "```ics\nBEGIN:VCALENDAR\nSUMMARY:Test\nEND:VCALENDAR\n```";
            String cleaned = service.cleanIcs(dirty);
            assertNotNull(cleaned);
            assertFalse(cleaned.contains("```"));
            assertTrue(cleaned.contains("BEGIN:VCALENDAR"));
        }

        @Test
        void testCleanIcs_removesGenericCodeBlock() {
            String dirty = "```\nBEGIN:VCALENDAR\nEND:VCALENDAR\n```";
            String cleaned = service.cleanIcs(dirty);
            assertFalse(cleaned.contains("```"));
            assertTrue(cleaned.contains("BEGIN:VCALENDAR"));
        }

        @Test
        void testCleanIcs_alreadyClean() {
            String clean = "BEGIN:VCALENDAR\nSUMMARY:Test\nEND:VCALENDAR";
            assertEquals(clean, service.cleanIcs(clean));
        }

        @Test
        void testCleanIcs_null() {
            assertNull(service.cleanIcs(null));
        }

        // --- generateIcs: successful responses ---

        @Test
        void testGenerateIcs_validResponse_returnsIcsContent() throws IOException {
            String icsContent = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Test\nEND:VEVENT\nEND:VCALENDAR";
            when(mockClient.generateContent(any(), any(), any())).thenReturn(buildSuccessResponse(icsContent));

            ConverterRequest request = buildRequestWithBase64Image();
            request.currentDate = "2026-02-22";
            request.timeZone = "UTC";

            String result = service.generateIcs(request);

            assertNotNull(result);
            assertTrue(result.contains("BEGIN:VCALENDAR"));
        }

        @Test
        void testGenerateIcs_stripsMarkdownFromResponse() throws IOException {
            String icsContent = "```ics\nBEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Test\nEND:VEVENT\nEND:VCALENDAR\n```";
            when(mockClient.generateContent(any(), any(), any())).thenReturn(buildSuccessResponse(icsContent));

            String result = service.generateIcs(buildRequestWithBase64Image());

            assertFalse(result.contains("```"));
            assertTrue(result.startsWith("BEGIN:VCALENDAR"));
        }

        @Test
        void testGenerateIcs_nullCurrentDateAndTimezone_usesDefaults() throws IOException {
            String icsContent = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Test\nEND:VEVENT\nEND:VCALENDAR";
            when(mockClient.generateContent(any(), any(), any())).thenReturn(buildSuccessResponse(icsContent));

            ConverterRequest request = buildRequestWithBase64Image();
            // currentDate and timeZone intentionally left null

            assertDoesNotThrow(() -> service.generateIcs(request));
        }

        // --- generateIcs: error cases ---

        @Test
        void testGenerateIcs_finishReasonNotStop_throwsProcessingException() {
            ObjectNode response = mapper.createObjectNode();
            ObjectNode candidate = response.putArray("candidates").addObject();
            candidate.put("finishReason", "SAFETY");
            candidate.putObject("content").putArray("parts").addObject().put("text", "Partial");
            when(mockClient.generateContent(any(), any(), any())).thenReturn(response);

            assertThrows(ProcessingException.class, () -> service.generateIcs(buildRequestWithBase64Image()));
        }

        @Test
        void testGenerateIcs_errorField_throwsExternalServiceException() {
            ObjectNode errorResponse = mapper.createObjectNode();
            errorResponse.putObject("error").put("message", "API quota exceeded");
            when(mockClient.generateContent(any(), any(), any())).thenReturn(errorResponse);

            assertThrows(ExternalServiceException.class, () -> service.generateIcs(buildRequestWithBase64Image()));
        }

        @Test
        void testGenerateIcs_clientThrows_throwsExternalServiceException() {
            when(mockClient.generateContent(any(), any(), any()))
                    .thenThrow(new RuntimeException("Connection refused"));

            assertThrows(ExternalServiceException.class, () -> service.generateIcs(buildRequestWithBase64Image()));
        }

        @Test
        void testGenerateIcs_noCandidates_throwsProcessingException() {
            ObjectNode response = mapper.createObjectNode();
            response.putArray("candidates"); // empty array
            when(mockClient.generateContent(any(), any(), any())).thenReturn(response);

            assertThrows(ProcessingException.class, () -> service.generateIcs(buildRequestWithBase64Image()));
        }

        // --- generateIcs: image handling ---

        @Test
        void testGenerateIcs_urlBasedImage_skippedWithWarning() throws IOException {
            String icsContent = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Test\nEND:VEVENT\nEND:VCALENDAR";
            when(mockClient.generateContent(any(), any(), any())).thenReturn(buildSuccessResponse(icsContent));

            ConverterRequest request = new ConverterRequest();
            ConverterRequest.ImageFile file = new ConverterRequest.ImageFile();
            file.url = "https://example.com/image.png"; // URL-based images are skipped
            request.files = List.of(file);

            // Should not throw — URL images are skipped with a log warning
            assertDoesNotThrow(() -> service.generateIcs(request));
        }

        @Test
        void testGenerateIcs_multipleMixedFiles_processesBase64Only() throws IOException {
            String icsContent = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Test\nEND:VEVENT\nEND:VCALENDAR";
            when(mockClient.generateContent(any(), any(), any())).thenReturn(buildSuccessResponse(icsContent));

            ConverterRequest request = new ConverterRequest();
            ConverterRequest.ImageFile urlFile = new ConverterRequest.ImageFile();
            urlFile.url = "https://example.com/image.png";
            ConverterRequest.ImageFile base64File = new ConverterRequest.ImageFile();
            base64File.dataUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==";
            request.files = List.of(urlFile, base64File);

            String result = service.generateIcs(request);
            assertNotNull(result);
            verify(mockClient, times(1)).generateContent(any(), any(), any());
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
            ObjectNode response = mapper.createObjectNode();
            ObjectNode candidate = response.putArray("candidates").addObject();
            candidate.put("finishReason", "STOP");
            candidate.putObject("content").putArray("parts").addObject().put("text", icsContent);
            return response;
        }
    }
}
