package com.dime.api.feature.statistics;

import com.dime.api.feature.converter.TrackingService;
import com.dime.api.feature.converter.TrackingService.Statistics;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Slf4j
@Path("/statistics")
@Tag(name = "converter", description = "Usage statistics and analytics")
public class StatisticsResource {

    @Inject
    TrackingService trackingService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get usage statistics", description = "Retrieves usage statistics and analytics data")
    @APIResponse(responseCode = "200", description = "Statistics retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Statistics.class)))
    @APIResponse(responseCode = "502", description = "Failed to fetch statistics from external service")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response getStatistics() {
        log.info("GET /statistics endpoint called");
        Statistics stats = trackingService.getStatistics();
        return Response.ok(stats).build();
    }
}
