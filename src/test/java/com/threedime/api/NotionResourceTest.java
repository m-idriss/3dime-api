package com.threedime.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
public class NotionResourceTest {

    @Test
    public void testNotionCmsEndpointReturnsOkWithEmptyMap() {
        // With default empty notion.cms.database-id, should return 200 and empty map
        given()
          .when().get("/notion/cms")
          .then()
             .statusCode(200)
             .contentType("application/json")
             .body("$", notNullValue())
             .body("isEmpty()", is(true));
    }
}
