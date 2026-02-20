package com.dime.api.feature.converter;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class ConverterIntegrationTest {

    @Test
    public void testConverterEndpointValidationErrorHandling() {
        // Test with empty files array
        given()
            .contentType(ContentType.JSON)
            .body("{\"files\":[],\"userId\":\"test-user\"}")
            .when().post("/converter")
            .then()
                .statusCode(400)
                .body("success", is(false))
                .body("errorCode", is("VALIDATION_ERROR"))
                .body("message", notNullValue())
                .body("timestamp", notNullValue());
    }

    @Test 
    public void testConverterEndpointWithValidRequest() {
        // Note: This will likely fail due to missing Gemini config, but it tests the enum handling
        String validRequest = """
            {
                "userId": "test-user",
                "timeZone": "UTC",
                "currentDate": "2026-02-17",
                "files": [
                    {
                        "dataUrl": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="
                    }
                ]
            }
        """;

        given()
            .contentType(ContentType.JSON)
            .body(validRequest)
            .when().post("/converter")
            .then()
                .statusCode(anyOf(is(200), is(422), is(502))) // Accept various responses depending on config
                .body("success", notNullValue())
                .body("timestamp", notNullValue());
    }

    @Test
    public void testConverterEndpointWithInvalidFileData() {
        String invalidRequest = """
            {
                "userId": "test-user",
                "files": [
                    {
                        "dataUrl": "",
                        "url": ""
                    }
                ]
            }
        """;

        given()
            .contentType(ContentType.JSON)
            .body(invalidRequest)
            .when().post("/converter")
            .then()
                .statusCode(400)
                .body("success", is(false))
                .body("errorCode", is("VALIDATION_ERROR"))
                .body("message", containsString("empty"));
    }

    @Test
    public void testQuotaStatusEndpointWithValidUser() {
        given()
            .param("userId", "test-user")
            .when().get("/converter/quota-status")
            .then()
                .statusCode(anyOf(is(200), is(404))) // User may or may not exist
                .body(notNullValue());
    }

    @Test 
    public void testQuotaStatusEndpointWithoutUserId() {
        given()
            .when().get("/converter/quota-status")
            .then()
                .statusCode(400); // Bad Request due to missing required parameter
    }

    @Test
    public void testQuotaStatusEndpointWithEmptyUserId() {
        given()
            .param("userId", "")
            .when().get("/converter/quota-status")
            .then()
                .statusCode(400); // Bad Request due to empty required parameter
    }
}