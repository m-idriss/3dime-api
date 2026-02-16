package com.threedime.api.feature.shared;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import java.net.URI;

@Path("/")
public class RootResource {

    @GET
    public Response redirectToApiDocs() {
        return Response
                .temporaryRedirect(URI.create("/api-docs"))
                .build();
    }
}