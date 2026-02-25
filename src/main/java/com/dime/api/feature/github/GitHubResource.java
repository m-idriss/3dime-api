package com.dime.api.feature.github;

import com.dime.api.feature.shared.exception.ValidationException;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Path("/github")
@Tag(name = "portfolio", description = "GitHub profile and contribution data")
@Extension(name = "x-smallrye-profile-public", value = "")
public class GitHubResource {

    @Inject
    GitHubService gitHubService;

    @GET
    @Path("/user")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get GitHub user information", description = "Retrieves the authenticated GitHub user information")
    @APIResponse(responseCode = "200", description = "User information retrieved successfully")
    @APIResponse(responseCode = "502", description = "Failed to fetch user from GitHub API")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response getUser() {
        log.info("GET /github/user endpoint called");
        return Response.ok(gitHubService.getUserInfo())
                .header("Cache-Control", "public, max-age=3600")
                .build();
    }

    @GET
    @Path("/social")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get GitHub social accounts", description = "Retrieves the authenticated GitHub user's social accounts")
    @APIResponse(responseCode = "200", description = "Social accounts retrieved successfully")
    @APIResponse(responseCode = "502", description = "Failed to fetch social accounts from GitHub API")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response getSocialAccounts() {
        log.info("GET /github/social endpoint called");
        return Response.ok(gitHubService.getSocialAccounts())
                .header("Cache-Control", "public, max-age=3600")
                .build();
    }

    @GET
    @Path("/commits")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get GitHub commit statistics", description = "Retrieves the authenticated GitHub user's commit statistics over a period")
    @APIResponse(responseCode = "200", description = "Commit statistics retrieved successfully")
    @APIResponse(responseCode = "400", description = "Invalid months parameter")
    @APIResponse(responseCode = "502", description = "Failed to fetch commits from GitHub API")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response getCommits(@QueryParam("months") String monthsStr) {
        log.info("GET /github/commits endpoint called with months={}", monthsStr);

        int months = 12; // default
        if (monthsStr != null) {
            try {
                months = Integer.parseInt(monthsStr);
                if (months < 1 || months > 60) {
                    throw new ValidationException("Invalid months parameter. Must be between 1 and 60.");
                }
            } catch (NumberFormatException e) {
                throw new ValidationException("Invalid months parameter. Must be a valid integer.");
            }
        }
        return Response.ok(gitHubService.getCommits(months))
                .header("Cache-Control", "public, max-age=3600")
                .build();
    }
}
