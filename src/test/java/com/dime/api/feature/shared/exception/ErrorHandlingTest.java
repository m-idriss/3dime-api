package com.dime.api.feature.shared.exception;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class ErrorHandlingTest {

    @Test
    public void testValidationErrorHandling() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"files\":[]}")  // Empty files array should trigger validation error
            .when().post("/converter")
            .then()
                .statusCode(400)
                .body("success", is(false))
                .body("error", notNullValue())
                .body("errorCode", is("VALIDATION_ERROR"))
                .body("timestamp", notNullValue());
    }

    @Test
    public void testInvalidRequestFormat() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"invalid\": \"json\"}")  // Invalid request body
            .when().post("/converter")
            .then()
                .statusCode(400)
                .body("success", is(false))
                .body("errorCode", is("VALIDATION_ERROR"));
    }

    @Test
    public void testGitHubInvalidMonthsParameter() {
        given()
            .when().get("/github/commits?months=invalid")
            .then()
                .statusCode(400)
                .body("success", is(false))
                .body("message", containsString("Invalid months parameter"))
                .body("errorCode", is("VALIDATION_ERROR"));
    }

    @Test
    public void testGitHubMonthsOutOfRange() {
        given()
            .when().get("/github/commits?months=100")
            .then()
                .statusCode(400)
                .body("success", is(false))
                .body("message", containsString("Must be between 1 and 60"))
                .body("errorCode", is("VALIDATION_ERROR"));
    }
}