package com.threedime.api.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/users")
@RegisterRestClient(configKey = "com.threedime.api.client.GitHubClient")
public interface GitHubClient {
    
    @GET
    @Path("/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    GitHubUser getUser(@PathParam("username") String username);
}
