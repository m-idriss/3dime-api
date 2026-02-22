package com.dime.api.feature.converter;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/v1")
@RegisterRestClient(configKey = "claude-api")
public interface ClaudeClient {

    @POST
    @Path("/messages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    JsonNode createMessage(
            @HeaderParam("x-api-key") String apiKey,
            @HeaderParam("anthropic-version") String version,
            Object body);
}
