package com.dime.api.feature.shared;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;

@QuarkusTest
public class RootResourceEdgeTest {
    @Test
    public void testLogoutRedirectsToLoginPage() {
        given()
                .redirects().follow(false)
                .when().get("/v1/logout")
                .then()
                .header("Location", endsWith("/login.html"));
    }

    @Test
    public void testRootRedirectsToLoginPageWhenNotLoggedIn() {
        given()
                .redirects().follow(false)
                .when().get("/v1/")
                .then()
                .header("Location", endsWith("/login.html"));
    }
}
