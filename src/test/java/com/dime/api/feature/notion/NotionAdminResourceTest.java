package com.dime.api.feature.notion;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

@QuarkusTest
public class NotionAdminResourceTest {

    @Test
    public void testRefreshEndpoint_Unauthorized() {
        // Unauthenticated users should be blocked (401 or 302 redirect to login)
        given()
                .redirects().follow(false)
                .when().get("/v1/notion/cms/refresh")
                .then()
                .statusCode(anyOf(is(401), is(302)));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testRefreshEndpoint_Authorized() {
        // Authenticated admin should be able to trigger refresh
        // 200 (success) or 502 (Notion API unavailable in test context) are acceptable
        given()
                .when().get("/v1/notion/cms/refresh")
                .then()
                .statusCode(anyOf(is(200), is(502)));
    }
}
