package com.dime.api.feature.notion;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@Path("/notion/cms")
@Tag(name = "admin", description = "Administrative Notion CMS operations")
@Extension(name = "x-smallrye-profile-admin", value = "")
public class NotionAdminResource {

    @Inject
    NotionService notionService;

    @GET
    @Path("/refresh")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Forced refresh of CMS content", description = "Bypasses cache and fetches fresh content from Notion CMS database. Restricted to admin user.")
    @APIResponse(responseCode = "200", description = "Content refreshed successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Map.class)))
    @APIResponse(responseCode = "502", description = "Failed to fetch content from Notion API")
    @APIResponse(responseCode = "401", description = "Unauthorized - admin login required")
    public Response refreshCmsContent() {
        Map<String, List<NotionService.CmsItem>> content = notionService.refreshCmsContent();
        return Response.ok(content).build();
    }
}
