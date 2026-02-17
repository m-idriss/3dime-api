package com.dime.api.feature.converter;

import com.dime.api.feature.shared.exception.ProcessingException;
import com.dime.api.feature.shared.exception.QuotaException;
import com.dime.api.feature.shared.exception.ValidationException;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Path("/converter")
@Tag(name = "Image Converter", description = "AI-powered image to calendar conversion")
public class ConverterResource {

    @Inject
    QuotaService quotaService;

    @Inject
    GeminiService geminiService;

    @Inject
    TrackingService trackingService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Convert images to calendar events",
               description = "Uses AI to extract calendar events from images and convert them to ICS format")
    @APIResponse(responseCode = "200", description = "Conversion successful",
                content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ConverterResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request data")
    @APIResponse(responseCode = "422", description = "Processing error - valid input but conversion failed")
    @APIResponse(responseCode = "429", description = "Quota exceeded")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response convert(@Valid @NotNull ConverterRequest request, @Context HttpHeaders headers) {
        long startTime = System.currentTimeMillis();
        String userId = request.userId != null ? request.userId : "anonymous";
        String domain = getDomain(headers);
        int fileCount = request.files != null ? request.files.size() : 0;

        // Validate input
        if (fileCount == 0) {
            throw new ValidationException("No files provided. Please provide at least one image.");
        }

        // Check for valid file data
        boolean hasValidFile = request.files.stream()
            .anyMatch(file -> (file.dataUrl != null && !file.dataUrl.trim().isEmpty()) ||
                             (file.url != null && !file.url.trim().isEmpty()));
        if (!hasValidFile) {
            throw new ValidationException("All provided files are empty. Please provide valid image data.");
        }

        // Check Quota
        QuotaService.QuotaCheckResult quota = quotaService.checkQuota(userId);
        if (!quota.allowed()) {
            trackingService.logQuotaExceeded(userId, (int) (quota.limit() - quota.remaining()), (int) quota.limit(),
                    quota.plan().toString(), domain);

            throw new QuotaException("You've reached your monthly conversion limit. Limit: " + quota.limit(),
                    Map.of("limit", quota.limit(), "remaining", quota.remaining(), "plan", quota.plan()));
        }

        try {
            // Call Gemini
            String icsContent = geminiService.generateIcs(request);

            if (icsContent == null || icsContent.isEmpty() || icsContent.equalsIgnoreCase("null")) {
                trackingService.logConversionError(userId, fileCount, "No events found in images",
                        System.currentTimeMillis() - startTime, domain);
                throw new ProcessingException("No calendar events found in the provided images. " +
                    "Please ensure the images contain clear calendar information.",
                    Map.of("reason", "no_events_detected", "fileCount", fileCount));
            }

            if (!isValidIcs(icsContent)) {
                trackingService.logConversionError(userId, fileCount, "Generated ICS is invalid",
                        System.currentTimeMillis() - startTime, domain);
                throw new ProcessingException("The AI generated invalid calendar data. Please try again with clearer images.",
                    Map.of("reason", "invalid_ics_format", "fileCount", fileCount));
            }

            // Success
            int eventCount = countEvents(icsContent);
            quotaService.incrementUsage(userId);
            trackingService.logConversion(userId, fileCount, domain, eventCount,
                    System.currentTimeMillis() - startTime);

            return Response.ok(new ConverterResponse(true, icsContent)).build();

        } catch (IOException e) {
            log.error("Error processing conversion request for user {}: {}", userId, e.getMessage(), e);
            trackingService.logConversionError(userId, fileCount, e.getMessage(),
                    System.currentTimeMillis() - startTime, domain);
            throw new ProcessingException("Failed to process images for conversion: " + e.getMessage(), e);
        }
    }

    @GET
    @Path("/quotaStatus")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get user quota status",
               description = "Retrieves the current quota usage and plan information for a user")
    @APIResponse(responseCode = "200", description = "Quota status retrieved successfully",
                content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UserQuota.class)))
    @APIResponse(responseCode = "404", description = "User not found")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response getQuotaStatus(@QueryParam("userId") @NotNull String userId) {
        log.info("GET /converter/quotaStatus endpoint called for user: {}", userId);
        
        try {
            UserQuota userQuota = quotaService.getQuotaStatus(userId);
            
            if (userQuota == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "User not found", "userId", userId))
                        .build();
            }
            
            return Response.ok(userQuota).build();
        } catch (Exception e) {
            log.error("Error retrieving quota status for user {}: {}", userId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Internal server error"))
                    .build();
        }
    }

    private String getDomain(HttpHeaders headers) {
        String origin = headers.getHeaderString("Origin");
        if (origin == null) {
            origin = headers.getHeaderString("Referer");
        }
        if (origin != null) {
            try {
                java.net.URL url = new java.net.URL(origin);
                return url.getHost();
            } catch (Exception e) {
                return "invalid-url";
            }
        }
        return "unknown";
    }

    private boolean isValidIcs(String ics) {
        return ics.startsWith("BEGIN:VCALENDAR") && ics.contains("BEGIN:VEVENT") && ics.endsWith("END:VCALENDAR");
    }

    private int countEvents(String ics) {
        return ics.split("BEGIN:VEVENT").length - 1;
    }
}
