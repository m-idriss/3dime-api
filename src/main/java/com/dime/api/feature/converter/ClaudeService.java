package com.dime.api.feature.converter;

import com.dime.api.feature.shared.exception.ExternalServiceException;
import com.dime.api.feature.shared.exception.ProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class ClaudeService {

    private static final Pattern BASE64_PATTERN = Pattern.compile("^data:(.+?);base64,(.+)$");
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Inject
    @RestClient
    ClaudeClient claudeClient;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "claude.model", defaultValue = "claude-3-5-sonnet-20241022")
    String modelName;

    @ConfigProperty(name = "claude.base-message")
    Optional<String> baseMessageTemplate;

    @ConfigProperty(name = "claude.system-prompt")
    Optional<String> systemPrompt;

    @ConfigProperty(name = "claude.api.key")
    Optional<String> apiKey;

    @Timeout(value = 60, unit = ChronoUnit.SECONDS)
    public String generateIcs(ConverterRequest request) {
        if (apiKey.isEmpty() || apiKey.get().trim().isEmpty()) {
            throw new ExternalServiceException("Claude", "Missing Claude API key (CLAUDE_API_KEY)");
        }

        String resolvedBaseMessage = baseMessageTemplate
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new ExternalServiceException("Claude",
                        "Missing required config: claude.base-message (set CLAUDE_BASE_MESSAGE env var)"));

        String resolvedSystemPrompt = systemPrompt
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new ExternalServiceException("Claude",
                        "Missing required config: claude.system-prompt (set CLAUDE_SYSTEM_PROMPT env var)"));

        String today = request.currentDate != null ? request.currentDate : java.time.LocalDate.now().toString();
        String tz = request.timeZone != null ? request.timeZone : "UTC";
        String baseMessage = resolvedBaseMessage.replace("{today}", today).replace("{tz}", tz);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", modelName);
        requestBody.put("max_tokens", 8192);
        requestBody.put("system", resolvedSystemPrompt);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");

        // Add images
        if (request.files != null) {
            for (ConverterRequest.ImageFile file : request.files) {
                String dataUrl = file.dataUrl != null ? file.dataUrl : file.url;
                if (dataUrl != null) {
                    Matcher matcher = BASE64_PATTERN.matcher(dataUrl);
                    if (matcher.find()) {
                        String mimeType = matcher.group(1);
                        String data = matcher.group(2);

                        ObjectNode imagePart = content.addObject();
                        imagePart.put("type", "image");
                        ObjectNode source = imagePart.putObject("source");
                        source.put("type", "base64");
                        source.put("media_type", mimeType);
                        source.put("data", data);
                    } else if (file.url != null) {
                        log.warn("URL-based image files are not yet supported. Skipping: {}", file.url);
                    }
                }
            }
        }

        // Add text prompt
        content.addObject().put("type", "text").put("text", baseMessage);

        log.info("Calling Claude API with model {}", modelName);

        JsonNode response;
        try {
            response = claudeClient.createMessage(apiKey.get(), ANTHROPIC_VERSION, requestBody);
        } catch (Exception e) {
            log.error("Failed to call Claude API", e);
            throw new ExternalServiceException("Claude",
                    "Failed to generate content using Claude API: " + e.getMessage(), e);
        }

        if (response.has("error")) {
            String error = response.get("error").toString();
            log.error("Claude API returned error: {}", error);
            throw new ExternalServiceException("Claude", "Claude API error: " + error);
        }

        String stopReason = response.has("stop_reason") ? response.get("stop_reason").asText() : "";
        if (!"end_turn".equals(stopReason)) {
            log.warn("Claude response was blocked or incomplete: {}", stopReason);
            throw new ProcessingException("Content generation was blocked or incomplete. " +
                    "Please try with different images. Reason: " + stopReason);
        }

        if (response.has("content") && response.get("content").size() > 0) {
            JsonNode firstContent = response.get("content").get(0);
            if (firstContent != null && firstContent.has("text")) {
                String text = firstContent.get("text").asText();
                return cleanIcs(text);
            }
        }

        log.error("Claude response did not contain expected content: {}", response.toString());
        throw new ProcessingException("Claude API returned unexpected response format");
    }

    String cleanIcs(String text) {
        return IcsUtils.cleanIcs(text);
    }
}
