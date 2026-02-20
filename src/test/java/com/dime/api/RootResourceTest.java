package com.dime.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;

@QuarkusTest
public class RootResourceTest {

    @Test
    public void testRootPathRedirectsToLoginPage() {
        given()
            .redirects().follow(false)
            .when().get("/")
            .then()
                .header("Location", endsWith("/login.html"));
    }
    @Test
    public void testApiPathRedirectsToLoginPageWhenNotLoggedIn() {
        given()
            .redirects().follow(false)
            .when().get("/api-docs")
            .then()
                .header("Location", endsWith("/login.html"));
    }
}
