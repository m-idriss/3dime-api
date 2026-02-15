package com.threedime.api.service;

import com.threedime.api.client.GitHubClient;
import com.threedime.api.client.GitHubUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class GitHubService {
    
    private static final Logger LOG = Logger.getLogger(GitHubService.class);
    
    @Inject
    @RestClient
    GitHubClient gitHubClient;
    
    @ConfigProperty(name = "github.username")
    String username;
    
    public GitHubUser getUserInfo() {
        LOG.infof("Fetching GitHub user info for: %s", username);
        
        try {
            GitHubUser user = gitHubClient.getUser(username);
            LOG.infof("Successfully fetched user info for: %s", username);
            return user;
        } catch (WebApplicationException e) {
            LOG.errorf(e, "Failed to fetch GitHub user info for: %s", username);
            // Return 502 Bad Gateway for external API failures
            throw new WebApplicationException("Failed to fetch user from GitHub API", 
                Response.Status.BAD_GATEWAY);
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error fetching GitHub user info for: %s", username);
            throw new WebApplicationException("Unexpected error calling GitHub API", 
                Response.Status.BAD_GATEWAY);
        }
    }
}
