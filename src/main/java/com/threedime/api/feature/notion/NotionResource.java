package com.threedime.api.feature.notion;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@Path("/notion")
@Tag(name = "Notion CMS", description = "Endpoints for fetching content from Notion CMS")
public class NotionResource {

    @Inject
    NotionService notionService;

    @GET
    @Path("/cms")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get CMS content", description = "Fetches and groups content (tools, resources) from the Notion CMS database")
    @APIResponse(responseCode = "200", description = "Content retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Map.class)))
    public Response getCmsContent() {
        Map<String, List<NotionService.CmsItem>> content = notionService.getCmsContent();
        return Response.ok(content).build();
    }
}
