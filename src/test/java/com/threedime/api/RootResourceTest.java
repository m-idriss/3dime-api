package com.threedime.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class RootResourceTest {

    @Test
    public void testRootPathRedirectsToApiDocs() {
        given()
            .redirects().follow(false)
            .when().get("/")
            .then()
                .statusCode(307)
                .header("Location", org.hamcrest.Matchers.endsWith("/api-docs"));
    }
}
