package com.dime.api.feature.notion;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
public class NotionResourceTest {
    @Test
    public void testCmsContentEndpointReturnsValidStatusCode() {
        // Endpoint returns 200 (success/empty) or 502 (Notion API unavailable in test context)
        io.restassured.response.Response response = given()
                .when().get("/notion/cms")
                .then()
                .statusCode(anyOf(is(200), is(502)))
                .extract().response();
        if (response.statusCode() == 200) {
            response.then().body(notNullValue());
        }
    }
}
