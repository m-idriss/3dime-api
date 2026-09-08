package com.dime.api.feature.converter;

import com.dime.api.feature.shared.exception.ProcessingException;
import com.dime.api.feature.shared.exception.QuotaException;
import com.dime.api.feature.shared.exception.ValidationException;
import com.dime.api.feature.shared.exception.IdempotencyException;
import com.dime.api.feature.shared.exception.AuthenticationException;
import com.dime.api.feature.shared.exception.ErrorResponse;
import com.dime.api.feature.shared.config.FirebaseAuthFilter;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.smallrye.faulttolerance.api.RateLimit;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Path("/converter")
@Tag(name = "converter", description = "AI-powered image to calendar conversion and quota status")
@Extension(name = "x-smallrye-profile-public", value = "")
public class ConverterResource {

    @Inject
    QuotaService quotaService;

    @Inject
    QuotaIdentityService quotaIdentityService;

    @Inject
    GeminiService geminiService;

    @Inject
    ClaudeService claudeService;

    @Inject
    CalendarNormalizer calendarNormalizer;

    @Inject
    TrackingService trackingService;

    @ConfigProperty(name = "ai.provider", defaultValue = "claude")
    String aiProvider;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RateLimit(value = 10, window = 1, windowUnit = ChronoUnit.MINUTES)
    @Operation(summary = "Convert images to calendar events", description = "Uses AI to extract calendar events from images and convert them to ICS format")
    @APIResponse(responseCode = "200", description = "Conversion successful", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ConverterResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request data", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "Verified Google authentication required", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "409", description = "Idempotency conflict", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "422", description = "Processing error - valid input but conversion failed", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "429", description = "Quota or rate limit exceeded", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "502", description = "AI provider rejected the request", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "503", description = "Quota datastore unavailable", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "504", description = "AI provider timed out", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response convert(@Valid @NotNull ConverterRequest request,
            @HeaderParam("X-Installation-ID") String installationId, @Context HttpHeaders headers,
            @Context ContainerRequestContext requestContext) {
        long startTime = System.currentTimeMillis();
        String verifiedUid = (String) requestContext.getProperty(FirebaseAuthFilter.FIREBASE_UID);
        Boolean emailVerified = (Boolean) requestContext.getProperty(FirebaseAuthFilter.FIREBASE_EMAIL_VERIFIED);
        if (verifiedUid == null || !Boolean.TRUE.equals(emailVerified)) {
            throw new AuthenticationException("A verified Google account is required to convert files.");
        }
        String rawEmail = (String) requestContext.getProperty(FirebaseAuthFilter.FIREBASE_EMAIL);
        String email = rawEmail != null && rawEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
                ? rawEmail : null;
        String userId = verifiedUid;
        QuotaIdentityService.QuotaIdentity quotaIdentity = quotaIdentityService.resolve(
                userId, installationId, requestContext);
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

        // Validate file content types via magic bytes
        for (ConverterRequest.ImageFile file : request.files) {
            if (file.dataUrl != null && !file.dataUrl.trim().isEmpty()) {
                validateFileContent(file.dataUrl);
            }
        }

        String idempotencyKey = headers.getHeaderString("Idempotency-Key");
        QuotaService.QuotaReservationResult reservation;
        try {
            reservation = quotaService.reserveQuota(userId, email, idempotencyKey, fileCount, quotaIdentity);
        } catch (QuotaException e) {
            logQuotaExceeded(userId, email, domain, e);
            throw e;
        }
        if (reservation.replay()) {
            QuotaReservation existing = reservation.reservation();
            if (existing.getStateType() == QuotaReservationState.COMPLETED && existing.icsContent != null) {
                log.info("Replayed completed quota reservation for user {}", userId);
                CalendarNormalizer.NormalizedCalendar replayed =
                        calendarNormalizer.normalize(existing.icsContent, request.timeZone);
                return Response.ok(new ConverterResponse(true, replayed.icsContent(), replayed.warnings())).build();
            }
            throw new IdempotencyException("A conversion with this Idempotency-Key is already in progress.",
                    Map.of("state", existing.state));
        }

        CalendarAiProvider selectedProvider = "gemini".equalsIgnoreCase(aiProvider) ? geminiService : claudeService;
        String provider = selectedProvider.name();
        try {
            // Provider output is untrusted: parse into the shared typed model,
            // validate every event, then generate the public ICS server-side.
            String providerOutput = selectedProvider.generateCalendar(request);
            CalendarNormalizer.NormalizedCalendar normalized =
                    calendarNormalizer.normalize(providerOutput, request.timeZone);
            String icsContent = normalized.icsContent();

            // Success
            int eventCount = normalized.eventCount();
            quotaService.completeReservation(userId, idempotencyKey, provider, icsContent, eventCount);
            trackingService.logConversion(userId, email, fileCount, domain, eventCount,
                    System.currentTimeMillis() - startTime);
            log.info("Calendar conversion completed: provider={}, result=success, latencyMs={}, eventCount={}",
                    provider, System.currentTimeMillis() - startTime, eventCount);

            return Response.ok(new ConverterResponse(true, icsContent, normalized.warnings())).build();

        } catch (IOException e) {
            log.error("Error processing conversion request for user {}: {}", userId, e.getMessage(), e);
            quotaService.failReservation(userId, idempotencyKey, provider, e.getMessage(), true);
            trackingService.logConversionError(userId, email, fileCount, e.getMessage(),
                    System.currentTimeMillis() - startTime, domain);
            throw new ProcessingException("Failed to process images for conversion: " + e.getMessage(), e);
        } catch (ProcessingException e) {
            quotaService.failReservation(userId, idempotencyKey, provider, e.getMessage(), true);
            trackingService.logConversionError(userId, email, fileCount, e.getMessage(),
                    System.currentTimeMillis() - startTime, domain);
            log.warn("Calendar conversion failed: provider={}, result=processing_error, latencyMs={}",
                    provider, System.currentTimeMillis() - startTime);
            throw e;
        } catch (RuntimeException e) {
            quotaService.failReservation(userId, idempotencyKey, provider, e.getMessage(), true);
            log.warn("Calendar conversion failed: provider={}, result=runtime_error, latencyMs={}",
                    provider, System.currentTimeMillis() - startTime);
            throw e;
        }
    }

    @GET
    @Path("/quota-status")
    @Produces(MediaType.APPLICATION_JSON)
    @RateLimit(value = 30, window = 1, windowUnit = ChronoUnit.MINUTES)
    @Operation(summary = "Get user quota status", description = "Retrieves the current quota usage and plan information for a user")
    @APIResponse(responseCode = "200", description = "Quota status retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = QuotaStatusResponse.class)))
    @APIResponse(responseCode = "401", description = "Verified Google authentication required", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "User not found")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response getQuotaStatus(@QueryParam("userId") @NotNull String userId,
            @HeaderParam("X-Installation-ID") String installationId,
            @Context ContainerRequestContext requestContext) {
        String verifiedUid = (String) requestContext.getProperty(FirebaseAuthFilter.FIREBASE_UID);
        Boolean emailVerified = (Boolean) requestContext.getProperty(FirebaseAuthFilter.FIREBASE_EMAIL_VERIFIED);
        if (verifiedUid == null || !Boolean.TRUE.equals(emailVerified)) {
            throw new AuthenticationException("A verified Google account is required to view quota status.");
        }
        String effectiveUserId = verifiedUid;
        QuotaIdentityService.QuotaIdentity quotaIdentity = quotaIdentityService.resolve(
                effectiveUserId, installationId, requestContext);
        log.info("GET /converter/quotaStatus endpoint called for user: {}", effectiveUserId);

        try {
            UserQuota userQuota = quotaService.getQuotaStatus(effectiveUserId, quotaIdentity);

            if (userQuota == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "User not found"))
                        .build();
            }

            return Response.ok(QuotaStatusResponse.from(userQuota)).build();
        } catch (Exception e) {
            log.error("Error retrieving quota status for user {}: {}", effectiveUserId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Internal server error"))
                    .build();
        }
    }

    @GET
    @Path("/plans")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get available plans", description = "Returns available plans and their quota limits")
    @APIResponse(responseCode = "200", description = "Plans retrieved",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = QuotaService.PlanInfo.class, type = SchemaType.ARRAY)))
    public List<QuotaService.PlanInfo> getPlans() {
        log.info("GET /converter/plans called");
        return quotaService.getQuotaLimits();
    }

    @GET
    @Path("/statistics")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get usage statistics", description = "Retrieves usage statistics and analytics data")
    @APIResponse(responseCode = "200", description = "Statistics retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TrackingService.Statistics.class)))
    @APIResponse(responseCode = "502", description = "Failed to fetch statistics from external service")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response getStatistics() {
        log.info("GET /converter/statistics endpoint called");
        TrackingService.Statistics stats = trackingService.getStatistics();
        return Response.ok(stats).build();
    }

    private String getDomain(HttpHeaders headers) {
        String origin = headers.getHeaderString("Origin");
        if (origin == null) {
            origin = headers.getHeaderString("Referer");
        }
        if (origin != null) {
            try {
                java.net.URI uri = new java.net.URI(origin);
                return uri.getHost();
            } catch (Exception e) {
                return "invalid-url";
            }
        }
        return "unknown";
    }

    private void logQuotaExceeded(String userId, String email, String domain, QuotaException exception) {
        long limit = -1;
        long remaining = 0;
        String plan = "UNKNOWN";
        if (exception.getDetails() instanceof Map<?, ?> details) {
            Object rawLimit = details.get("limit");
            if (rawLimit instanceof Number number) {
                limit = number.longValue();
            }
            Object rawRemaining = details.get("remaining");
            if (rawRemaining instanceof Number number) {
                remaining = number.longValue();
            }
            Object rawPlan = details.get("plan");
            if (rawPlan != null) {
                plan = rawPlan.toString();
            }
        }

        trackingService.logQuotaExceeded(userId, email, (int) Math.max(0, limit - remaining), (int) limit, plan,
                domain);
    }

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/heic", "application/pdf");

    private void validateFileContent(String dataUrl) {
        String[] parts = dataUrl.split(",", 2);
        if (parts.length != 2) {
            throw new ValidationException("Invalid data URL format.");
        }

        // Validate MIME type from header
        String header = parts[0].toLowerCase();
        boolean mimeAllowed = ALLOWED_MIME_TYPES.stream().anyMatch(header::contains);
        if (!mimeAllowed) {
            throw new ValidationException("Unsupported file type. Allowed: JPEG, PNG, HEIC, PDF.");
        }

        // Validate magic bytes
        try {
            byte[] decoded = Base64.getDecoder().decode(
                    parts[1].substring(0, Math.min(24, parts[1].length())));
            if (decoded.length < 4) {
                throw new ValidationException("File data is too small to be a valid image.");
            }
            if (!matchesMagicBytes(decoded)) {
                throw new ValidationException("File content does not match declared type.");
            }
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid base64 encoding in file data.");
        }
    }

    private boolean matchesMagicBytes(byte[] bytes) {
        // JPEG: FF D8 FF
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47
        if (bytes.length >= 4 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return true;
        }
        // PDF: 25 50 44 46 (%PDF)
        if (bytes.length >= 4 && bytes[0] == 0x25 && bytes[1] == 0x50
                && bytes[2] == 0x44 && bytes[3] == 0x46) {
            return true;
        }
        // HEIC: ftyp at offset 4
        if (bytes.length >= 12 && bytes[4] == 0x66 && bytes[5] == 0x74
                && bytes[6] == 0x79 && bytes[7] == 0x70) {
            return true;
        }
        return false;
    }
}
