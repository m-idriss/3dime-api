package com.dime.api.feature.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class GeminiService {

    private static final Logger LOG = LoggerFactory.getLogger(GeminiService.class);
    private static final Pattern BASE64_PATTERN = Pattern.compile("^data:(.+?);base64,(.+)$");

    @Inject
    @RestClient
    GeminiClient geminiClient;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "gemini.model", defaultValue = "gemini-2.0-flash-lite-preview-02-05")
    String modelName;

    @ConfigProperty(name = "gemini.base-message")
    String baseMessageTemplate;

    @ConfigProperty(name = "gemini.system-prompt")
    String systemPrompt;

    public String generateIcs(ConverterRequest request) throws IOException {
        String token = getAccessToken();

        String today = request.currentDate != null ? request.currentDate : java.time.LocalDate.now().toString();
        String tz = request.timeZone != null ? request.timeZone : "UTC";
        String baseMessage = baseMessageTemplate.replace("{today}", today).replace("{tz}", tz);

        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode contents = requestBody.putArray("contents");
        ObjectNode contentPart = contents.addObject();
        ArrayNode parts = contentPart.putArray("parts");

        // Add user prompt logic
        String userPrompt = systemPrompt + "\n\n" + baseMessage;
        parts.addObject().put("text", userPrompt);

        // Add images
        if (request.files != null) {
            for (ConverterRequest.ImageFile file : request.files) {
                String dataUrl = file.dataUrl != null ? file.dataUrl : file.url;
                if (dataUrl != null) {
                    Matcher matcher = BASE64_PATTERN.matcher(dataUrl);
                    if (matcher.find()) {
                        String mimeType = matcher.group(1);
                        String data = matcher.group(2);

                        ObjectNode inlineDataPart = parts.addObject();
                        ObjectNode inlineData = inlineDataPart.putObject("inline_data");
                        inlineData.put("mime_type", mimeType);
                        inlineData.put("data", data);
                    }
                }
            }
        }

        ObjectNode generationConfig = requestBody.putObject("generationConfig");
        generationConfig.put("temperature", 0.1);
        generationConfig.put("maxOutputTokens", 8192);

        LOG.info("Calling Gemini API with model {}", modelName);
        JsonNode response = geminiClient.generateContent("Bearer " + token, modelName, requestBody);

        if (response.has("candidates") && response.get("candidates").size() > 0) {
            JsonNode candidate = response.get("candidates").get(0);
            if (candidate.has("content") && candidate.get("content").has("parts")) {
                String text = candidate.get("content").get("parts").get(0).get("text").asText();
                return cleanIcs(text);
            }
        }

        LOG.error("Gemini response did not contain expected content: {}", response.toString());
        throw new IOException("Failed to generate ICS content from Gemini response");
    }

    private String getAccessToken() throws IOException {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped(Collections.singleton("https://www.googleapis.com/auth/generative-language"));
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    private String cleanIcs(String text) {
        if (text == null)
            return null;
        String cleaned = text.replaceAll("```(?:ics)?\\s*[\\r\\n]|```", "").trim();
        return cleaned;
    }
}
