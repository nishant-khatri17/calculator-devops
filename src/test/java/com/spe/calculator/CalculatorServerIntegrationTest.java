package com.spe.calculator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Starts the real server on a free port and calls it over HTTP. */
class CalculatorServerIntegrationTest {

    private static CalculatorServer server;
    private static HttpClient client;
    private static String baseUrl;

    @BeforeAll
    static void startServer() throws IOException {
        server = new CalculatorServer(0); // 0 = pick any free port
        server.start();
        client = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + server.getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void addReturnsJsonResult() throws Exception {
        HttpResponse<String> response = get("/api/v1/add?a=2&b=3");
        assertEquals(200, response.statusCode());
        assertEquals("{\"operation\":\"add\",\"a\":2,\"b\":3,\"result\":5}", response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
    }

    @Test
    void subtractWorks() throws Exception {
        assertEquals("{\"operation\":\"subtract\",\"a\":2,\"b\":3,\"result\":-1}",
                get("/api/v1/subtract?a=2&b=3").body());
    }

    @Test
    void multiplyWorksWithDecimals() throws Exception {
        assertEquals("{\"operation\":\"multiply\",\"a\":1.5,\"b\":4,\"result\":6}",
                get("/api/v1/multiply?a=1.5&b=4").body());
    }

    @Test
    void divideWorks() throws Exception {
        assertEquals("{\"operation\":\"divide\",\"a\":10,\"b\":4,\"result\":2.5}",
                get("/api/v1/divide?a=10&b=4").body());
    }

    @Test
    void divideByZeroReturns400() throws Exception {
        HttpResponse<String> response = get("/api/v1/divide?a=5&b=0");
        assertEquals(400, response.statusCode());
        assertEquals("{\"status\":400,\"error\":\"Bad Request\",\"message\":\"Division by zero is not allowed\"}",
                response.body());
    }

    @Test
    void missingParameterReturns400() throws Exception {
        HttpResponse<String> response = get("/api/v1/add?a=2");
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Missing required parameter 'b'"));
    }

    @Test
    void invalidParameterReturns400() throws Exception {
        HttpResponse<String> response = get("/api/v1/add?a=abc&b=3");
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Parameter 'a' must be a valid number"));
    }

    @Test
    void noQueryStringReturns400() throws Exception {
        assertEquals(400, get("/api/v1/multiply").statusCode());
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertEquals(200, response.statusCode());
        assertEquals("{\"status\":\"UP\"}", response.body());
    }

    @Test
    void unknownPathReturns404() throws Exception {
        assertEquals(404, get("/nope").statusCode());
        assertEquals(404, get("/api/v1/addxyz?a=1&b=2").statusCode());
    }

    @Test
    void postIsNotAllowed() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/add?a=1&b=2"))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
    }
}
