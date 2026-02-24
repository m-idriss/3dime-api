package com.dime.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

@QuarkusTest
public class GitHubResourceTest {

    @Test
    public void testGitHubUserEndpointReturnsValidStatusCode() {
        // Test can return either 200 (success) or 502 (GitHub API failure/rate limit)
        // All external API errors are converted to 502 Bad Gateway
        Response response = given()
          .when().get("/github/user")
          .then()
             .statusCode(anyOf(
                is(200),
                is(502)
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
        // Health endpoint may return 200 (all UP) or 503 (external deps DOWN in test context)
        given()
          .when().get("/health")
          .then()
             .statusCode(anyOf(is(200), is(503)));
    }

    @Test
    public void testReadinessEndpointReturnsOk() {
        // Readiness endpoint now checks external dependencies; status may be UP or DOWN in test context
        given()
          .when().get("/health/ready")
          .then()
             .statusCode(anyOf(is(200), is(503)))
             .body("checks.size()", greaterThan(0))
             .body("checks.name", hasItem("firestore"))
             .body("checks.name", hasItem("gemini"))
             .body("checks.name", hasItem("notion"))
             .body("checks.name", hasItem("github"));
    }
    
    @Test
    public void testLivenessEndpointReturnsOk() {
        given()
          .when().get("/health/live")
          .then()
             .statusCode(200)
             .body("status", is("UP"))
             .body("checks.size()", greaterThan(0))
             .body("checks.name", hasItem("3dime-api is live"));
    }
}
