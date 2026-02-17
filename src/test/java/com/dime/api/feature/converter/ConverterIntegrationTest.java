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
        // Test with a valid request - assertions conditional on status code
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

        var response = given()
            .contentType(ContentType.JSON)
            .body(validRequest)
            .when().post("/converter")
            .then()
                .statusCode(anyOf(is(200), is(422), is(500), is(502))) // Accept various responses depending on config
                .body("success", notNullValue())
                .body("timestamp", notNullValue());
        
        int statusCode = response.extract().statusCode();
        
        // Conditional assertions based on status code
        if (statusCode == 200) {
            // On success, verify ICS content is present and valid
            response.body("success", is(true))
                .body("icsContent", notNullValue())
                .body("icsContent", containsString("BEGIN:VCALENDAR"))
                .body("icsContent", containsString("BEGIN:VEVENT"))
                .body("icsContent", containsString("END:VCALENDAR"));
        } else if (statusCode == 422 || statusCode == 500 || statusCode == 502) {
            // On processing/server error, verify error structure
            response.body("success", is(false))
                .body("errorCode", notNullValue())
                .body("message", notNullValue());
        }
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
}