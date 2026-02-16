package com.dime.api.feature.notion;

import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import com.fasterxml.jackson.databind.JsonNode;

@Path("/v1")
@RegisterRestClient(configKey = "notion-api")
public interface NotionClient {

        @POST
        @Path("/pages")
        JsonNode createPage(@HeaderParam("Authorization") String token,
                        @HeaderParam("Notion-Version") String version,
                        Object page);

        @POST
        @Path("/databases/{databaseId}/query")
        JsonNode queryDatabase(@HeaderParam("Authorization") String token,
                        @HeaderParam("Notion-Version") String version,
                        @PathParam("databaseId") String databaseId,
                        Object query);

        @PATCH
        @Path("/pages/{pageId}")
        @Produces(MediaType.APPLICATION_JSON)
        JsonNode updatePage(@HeaderParam("Authorization") String token,
                        @HeaderParam("Notion-Version") String version,
                        @PathParam("pageId") String pageId,
                        Object properties);
}
