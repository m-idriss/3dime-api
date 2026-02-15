package com.threedime.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
public class GitHubResourceTest {

    @Test
    public void testGitHubUserEndpointReturnsValidStatusCode() {
        // Test can return either 200 (success) or 502 (GitHub API failure/rate limit)
        // All external API errors are converted to 502 Bad Gateway
        Response response = given()
          .when().get("/github/user")
          .then()
             .statusCode(org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.is(200),
                org.hamcrest.Matchers.is(502)
             ))
          .extract().response();
        
        // If successful, verify response contains expected fields
        if (response.statusCode() == 200) {
            response.then()
                .body("login", notNullValue())
                .body("id", notNullValue());
        }
    }
    
    @Test
    public void testHealthEndpointReturnsOk() {
        given()
          .when().get("/health")
          .then()
             .statusCode(200);
    }
}
