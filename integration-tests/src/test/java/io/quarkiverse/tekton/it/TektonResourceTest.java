package io.quarkiverse.tekton.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TektonResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/tekton")
                .then()
                .statusCode(200)
                .body(is("Hello tekton"));
    }
}
