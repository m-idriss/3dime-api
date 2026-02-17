package com.dime.api.feature.converter;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Path("/converter")
public class ConverterResource {

    private static final Logger LOG = LoggerFactory.getLogger(ConverterResource.class);

    @Inject
    QuotaService quotaService;

    @Inject
    GeminiService geminiService;

    @Inject
    TrackingService trackingService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response convert(ConverterRequest request, @Context HttpHeaders headers) {
        long startTime = System.currentTimeMillis();
        String userId = request.userId != null ? request.userId : "anonymous";
        String domain = getDomain(headers);
        int fileCount = request.files != null ? request.files.size() : 0;

        if (fileCount == 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ConverterResponse.error("No files provided", "Please provide at least one image."))
                    .build();
        }

        // Check Quota
        QuotaService.QuotaCheckResult quota = quotaService.checkQuota(userId);
        if (!quota.allowed()) {
            trackingService.logQuotaExceeded(userId, (int) (quota.limit() - quota.remaining()), (int) quota.limit(),
                    quota.plan().toString(), domain);
            return Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .entity(ConverterResponse.error(
                            "You've reached your monthly conversion limit.",
                            "Monthly limit reached. Limit: " + quota.limit()))
                    .build();
        }

        try {
            // Call Gemini
            String icsContent = geminiService.generateIcs(request);

            if (icsContent == null || icsContent.isEmpty() || icsContent.equalsIgnoreCase("null")) {
                trackingService.logConversionError(userId, fileCount, "No events found in images",
                        System.currentTimeMillis() - startTime, domain);
                return Response
                        .ok(ConverterResponse.error("No events found in images",
                                "AI determined there are no calendar events."))
                        .build();
            }

            if (!isValidIcs(icsContent)) {
                trackingService.logConversionError(userId, fileCount, "Generated ICS is invalid",
                        System.currentTimeMillis() - startTime, domain);
                return Response
                        .ok(ConverterResponse.error("Generated ICS is invalid",
                                "The AI generated invalid calendar data."))
                        .build();
            }

            // Success
            int eventCount = countEvents(icsContent);
            quotaService.incrementUsage(userId);
            trackingService.logConversion(userId, fileCount, domain, eventCount,
                    System.currentTimeMillis() - startTime);

            return Response.ok(new ConverterResponse(true, icsContent)).build();

        } catch (IOException e) {
            LOG.error("Error processing conversion request for user {}", userId, e);
            trackingService.logConversionError(userId, fileCount, e.getMessage(),
                    System.currentTimeMillis() - startTime, domain);
            return Response.serverError()
                    .entity(ConverterResponse.error("Internal Server Error", e.getMessage()))
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
