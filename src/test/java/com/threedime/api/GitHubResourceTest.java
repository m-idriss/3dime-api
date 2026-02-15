package com.threedime.api;

import com.threedime.api.client.GitHubUser;
import com.threedime.api.service.GitHubService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

@QuarkusTest
public class GitHubResourceTest {

    @InjectMock
    GitHubService gitHubService;

    @Test
    public void testGitHubUserEndpointReturnsSuccessWithMockedClient() {
        // Mock successful response from GitHub API
        GitHubUser mockUser = new GitHubUser();
        mockUser.setLogin("testuser");
        mockUser.setId(12345L);
        mockUser.setName("Test User");
        
        when(gitHubService.getUserInfo()).thenReturn(mockUser);
        
        given()
          .when().get("/github/user")
          .then()
             .statusCode(200)
             .body("login", is("testuser"))
             .body("id", is(12345))
             .body("name", is("Test User"));
    }
    
    @Test
    public void testGitHubUserEndpointReturns502OnClientFailure() {
        // Mock failure from GitHub API
        when(gitHubService.getUserInfo())
            .thenThrow(new WebApplicationException("Failed to fetch user from GitHub API", 
                Response.Status.BAD_GATEWAY));
        
        given()
          .when().get("/github/user")
          .then()
             .statusCode(502);
    }
    
    @Test
    public void testHealthEndpointReturnsOk() {
        given()
          .when().get("/health")
          .then()
             .statusCode(200);
    }
    
    @Test
    public void testReadinessEndpointReturnsOk() {
        given()
          .when().get("/health/ready")
          .then()
             .statusCode(200)
             .body("status", is("UP"))
             .body("checks.size()", greaterThan(0))
             .body("checks.name", hasItem("3dime-api is ready"));
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
