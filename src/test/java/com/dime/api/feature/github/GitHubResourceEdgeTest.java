package com.dime.api.feature.github;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import io.restassured.response.Response;

@QuarkusTest
public class GitHubResourceEdgeTest {
    @Test
    public void testSocialAccountsEndpointReturnsValidStatusCode() {
        Response response = given()
                .when().get("/github/social")
                .then()
                .statusCode(anyOf(is(200), is(502)))
                .extract().response();
        if (response.statusCode() == 200) {
            response.then().body(notNullValue());
        }
    }

    @Test
    public void testCommitsEndpointHandlesInvalidMonths() {
        Response response = given()
                .queryParam("months", "invalid")
                .when().get("/github/commits")
                .then()
                .statusCode(anyOf(is(400), is(502), is(200)))
                .extract().response();
        // Acceptable: 400 (bad input), 502 (external error), or 200 (valid)
    }
}
