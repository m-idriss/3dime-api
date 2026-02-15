package com.threedime.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class GitHubResourceTest {

    @Test
    public void testGitHubUserEndpointReturnsValidResponse() {
        // Test can return either 200 (success) or 502 (GitHub API failure)
        // Both are valid responses depending on GitHub API availability
        given()
          .when().get("/github/user")
          .then()
             .statusCode(org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.is(200),
                org.hamcrest.Matchers.is(502)
             ));
    }
    
    @Test
    public void testHealthEndpoint() {
        given()
          .when().get("/health")
          .then()
             .statusCode(200);
    }
}
