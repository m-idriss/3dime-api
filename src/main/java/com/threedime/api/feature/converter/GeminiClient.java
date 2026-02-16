package com.threedime.api.feature.converter;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/v1beta/models")
@RegisterRestClient(configKey = "gemini-api")
public interface GeminiClient {

    @POST
    @Path("/{model}:generateContent")
    JsonNode generateContent(@HeaderParam("Authorization") String token,
            @PathParam("model") String model,
            Object body);
}
