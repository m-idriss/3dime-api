package com.threedime.api.resource;

import com.threedime.api.client.GitHubUser;
import com.threedime.api.service.GitHubService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

@Path("/github")
@Tag(name = "GitHub", description = "GitHub API operations")
public class GitHubResource {

    private static final Logger LOG = Logger.getLogger(GitHubResource.class);

    @Inject
    GitHubService gitHubService;

    @GET
    @Path("/user")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get GitHub user information", description = "Retrieves the authenticated GitHub user information")
    @APIResponse(responseCode = "200", description = "User information retrieved successfully")
    @APIResponse(responseCode = "502", description = "Failed to fetch user from GitHub API")
    public GitHubUser getUser() {
        LOG.info("GET /github/user endpoint called");
        return gitHubService.getUserInfo();
    }
}
