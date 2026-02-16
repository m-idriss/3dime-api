package com.dime.api.feature.shared;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.net.URI;

@Path("/")
public class RootResource {

    @GET
    @Operation(hidden = true)
    public Response redirectToApiDocs() {
        return Response
                .temporaryRedirect(URI.create("/api-docs"))
                .build();
    }
}