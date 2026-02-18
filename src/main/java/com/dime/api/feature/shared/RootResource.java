package com.dime.api.feature.shared;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.NewCookie;
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

    @GET
    @Path("/logout")
    @Operation(summary = "Logout from the administrative console", hidden = true)
    public Response logout() {
        return Response.seeOther(URI.create("/login.html"))
                .cookie(new NewCookie.Builder("quarkus-credential")
                        .path("/")
                        .maxAge(0)
                        .expiry(new java.util.Date(0))
                        .secure(false) // Match the current security of the cookie or set based on environment
                        .httpOnly(true)
                        .build())
                .build();
    }
}