package com.spe.calculator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.BinaryOperator;

/**
 * Calculator REST API built on the JDK's own com.sun.net.httpserver (no framework).
 *
 *   GET /api/v1/add|subtract|multiply|divide?a=&b=
 *   GET /actuator/health
 */
public class CalculatorServer {

    private static final int MAX_INPUT_LENGTH = 100;
    private static final int MAX_SCALE = 1000;

    private final HttpServer server;

    public CalculatorServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));

        register("add", Calculator::add);
        register("subtract", Calculator::subtract);
        register("multiply", Calculator::multiply);
        register("divide", Calculator::divide);

        server.createContext("/actuator/health", exchange -> {
            if (!isGet(exchange)) {
                return;
            }
            send(exchange, 200, "{\"status\":\"UP\"}");
        });

        // Anything that did not match a context above
        server.createContext("/", exchange ->
                send(exchange, 404, Json.error(404, "Not Found", "No such endpoint")));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(1);
    }

    /** The real port (useful when started with port 0 in tests). */
    public int getPort() {
        return server.getAddress().getPort();
    }

    // ---------------------------------------------------------------- handlers

    private void register(String operation, BinaryOperator<BigDecimal> function) {
        String path = "/api/v1/" + operation;
        server.createContext(path, exchange -> {
            // createContext matches prefixes, so "/api/v1/addxyz" would land here too
            if (!exchange.getRequestURI().getPath().equals(path)) {
                send(exchange, 404, Json.error(404, "Not Found", "No such endpoint"));
                return;
            }
            if (!isGet(exchange)) {
                return;
            }
            try {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
                BigDecimal a = readNumber(params, "a");
                BigDecimal b = readNumber(params, "b");
                BigDecimal result = function.apply(a, b);
                send(exchange, 200, Json.result(operation, format(a), format(b), format(result)));
            } catch (IllegalArgumentException | ArithmeticException e) {
                // bad / missing number, division by zero, numeric overflow -> HTTP 400
                send(exchange, 400, Json.error(400, "Bad Request", e.getMessage()));
            }
        });
    }

    /** Returns true if the request is GET; otherwise sends 405 and returns false. */
    private boolean isGet(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            return true;
        }
        exchange.getResponseHeaders().set("Allow", "GET");
        send(exchange, 405, Json.error(405, "Method Not Allowed", "Only GET is supported"));
        return false;
    }

    // ----------------------------------------------------------------- helpers

    private static BigDecimal readNumber(Map<String, String> params, String name) {
        String raw = params.get(name);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter '" + name + "'");
        }
        raw = raw.trim();
        try {
            if (raw.length() > MAX_INPUT_LENGTH) {
                throw new NumberFormatException();
            }
            BigDecimal value = new BigDecimal(raw);
            if (Math.abs(value.scale()) > MAX_SCALE) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parameter '" + name + "' must be a valid number");
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            params.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return params;
    }

    /** 2.50 -> "2.5", 5.0 -> "5", so the JSON shows { "a": 2, "result": 5 }. */
    private static String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        System.out.println(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " -> " + status);
    }

    // -------------------------------------------------------------------- main

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        CalculatorServer app = new CalculatorServer(port);
        // Docker sends SIGTERM on "docker stop" -> shut down cleanly
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
        app.start();
        System.out.println("Calculator API listening on port " + port);
    }
}
