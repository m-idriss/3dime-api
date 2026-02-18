package com.dime.api.feature.converter;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
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

import java.util.List;

@Slf4j
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "admin", description = "User management and quota administration")
@Extension(name = "x-smallrye-profile-admin", value = "")
public class UserQuotaResource {

    @Inject
    QuotaService quotaService;

    @GET
    @Operation(summary = "List all user quotas", description = "Retrieves a list of all users and their current quota status")
    @APIResponse(responseCode = "200", description = "List of user quotas", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = QuotaService.UserQuotaWrapper.class, type = SchemaType.ARRAY)))
    public List<QuotaService.UserQuotaWrapper> getAllQuotas() {
        log.info("GET /users called");
        return quotaService.findAll();
    }

    @GET
    @Path("/{userId}")
    @Operation(summary = "Get user quota", description = "Retrieves the quota status for a specific user")
    @APIResponse(responseCode = "200", description = "User quota found", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UserQuota.class)))
    @APIResponse(responseCode = "404", description = "User not found")
    public Response getQuota(@PathParam("userId") String userId) {
        log.info("GET /users/{} called", userId);
        UserQuota quota = quotaService.getQuotaStatus(userId);
        if (quota == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(quota).build();
    }

    @PATCH
    @Path("/{userId}")
    @Operation(summary = "Update user quota", description = "Updates specific fields of a user's quota")
    @APIResponse(responseCode = "204", description = "User quota updated")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response updateQuota(@PathParam("userId") String userId, UserQuota quota) {
        log.info("PATCH /users/{} called", userId);
        quotaService.updateQuota(userId, quota);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{userId}")
    @Operation(summary = "Delete user quota", description = "Removes a user's quota record from Firestore")
    @APIResponse(responseCode = "204", description = "User quota deleted")
    public Response deleteQuota(@PathParam("userId") String userId) {
        log.info("DELETE /users/{} called", userId);
        quotaService.deleteQuota(userId);
        return Response.noContent().build();
    }

    @POST
    @Path("/sync-notion")
    @Operation(summary = "Sync to Notion", description = "Triggers a synchronization of all user quotas from Firestore to Notion. Optionally provide a list of user IDs to sync only those users.")
    @APIResponse(responseCode = "202", description = "Synchronization started")
    public Response syncToNotion(List<String> userIds) {
        log.info("POST /users/sync-notion called with {} userIds", userIds == null ? 0 : userIds.size());
        quotaService.syncToNotion(userIds);
        return Response.accepted().build();
    }

    @POST
    @Path("/sync-firebase")
    @Operation(summary = "Sync from Notion", description = "Triggers a synchronization of user quotas from Notion back to Firestore. Optionally provide a list of user IDs to sync only those users.")
    @APIResponse(responseCode = "202", description = "Synchronization started")
    public Response syncFromNotion(List<String> userIds) {
        log.info("POST /users/sync-firebase called with {} userIds", userIds == null ? 0 : userIds.size());
        quotaService.syncFromNotion(userIds);
        return Response.accepted().build();
    }
}
