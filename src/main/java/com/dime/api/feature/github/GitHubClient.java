package com.dime.api.feature.github;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import com.fasterxml.jackson.databind.JsonNode;

@RegisterRestClient(configKey = "com.dime.api.feature.github.GitHubClient")
public interface GitHubClient {

    @GET
    @Path("/users/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 3, delay = 200)
    @Timeout(2000)
    GitHubUser getUser(@PathParam("username") String username);

    @GET
    @Path("/users/{username}/social_accounts")
    @Produces(MediaType.APPLICATION_JSON)
    JsonNode getSocialAccounts(@PathParam("username") String username);

    @POST
    @Path("/graphql")
    @Produces(MediaType.APPLICATION_JSON)
    JsonNode postGraphql(@HeaderParam("Authorization") String token, Object query);
}
