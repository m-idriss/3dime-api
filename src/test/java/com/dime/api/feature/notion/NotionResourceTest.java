package com.dime.api.feature.notion;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
// Removed unused import

@QuarkusTest
public class NotionResourceTest {
    @Test
    public void testCmsContentEndpointReturnsValidStatusCode() {
        io.restassured.response.Response response = given()
                .when().get("/notion/cms")
                .then()
                .statusCode(is(200))
                .extract().response();
        // If successful, verify response contains expected fields
        response.then().body(notNullValue());
    }
}
