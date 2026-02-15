package com.threedime.api.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import org.eclipse.microprofile.openapi.annotations.Hidden;

@Path("/")
@Hidden
public class RootResource {

    @GET
    public Response redirectToApiDocs() {
        return Response
                .temporaryRedirect(URI.create("/api-docs"))
                .build();
    }
}