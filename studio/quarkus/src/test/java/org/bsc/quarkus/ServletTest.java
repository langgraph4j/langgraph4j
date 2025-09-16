package org.bsc.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;


import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class ServletTest {

    @Test
    void textGraphINitServlet() {
        given()
          .when().get("/init?instance=sample")
          .then()
             .statusCode(200)
             .body( not( empty() ) );
    }

}