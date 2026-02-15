package com.threedime.api.resource;

import com.threedime.api.service.TrackingService;
import com.threedime.api.service.TrackingService.Statistics;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/statistics")
public class StatisticsResource {

    @Inject
    TrackingService trackingService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatistics() {
        Statistics stats = trackingService.getStatistics();
        return Response.ok(stats).build();
    }
}
