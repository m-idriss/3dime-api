package com.threedime.api.resource;

import com.threedime.api.client.GitHubUser;
import com.threedime.api.service.GitHubService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

@Path("/github")
public class GitHubResource {
    
    private static final Logger LOG = Logger.getLogger(GitHubResource.class);
    
    @Inject
    GitHubService gitHubService;
    
    @GET
    @Path("/user")
    @Produces(MediaType.APPLICATION_JSON)
    public GitHubUser getUser() {
        LOG.info("GET /github/user endpoint called");
        return gitHubService.getUserInfo();
    }
}
