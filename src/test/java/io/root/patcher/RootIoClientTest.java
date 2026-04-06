package io.root.patcher;

import com.sun.net.httpserver.HttpServer;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import groovy.json.JsonOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RootIoClientTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /** Client with no retries and no delay — for tests that just verify HTTP behavior. */
    private RootIoClient noRetryClient() {
        return new RootIoClient(HttpClient.newHttpClient(), 0, attempt -> 0);
    }

    /** Client with instant retries (no sleep) and a given retry count. */
    private RootIoClient retryClient(int maxRetries) {
        return new RootIoClient(HttpClient.newHttpClient(), maxRetries, attempt -> 0);
    }

    @Test
    void returnsNullWhenNoPatchAvailable() {
        respondWith(200, JsonOutput.toJson(Map.of("patches", List.of(), "skipped", List.of())));

        String result = noRetryClient().query("org.example:foo:1.0", "http://localhost:" + port, "test-key");

        assertNull(result);
    }

    @Test
    void returnsPatchedCoordsWhenPatchAvailable() {
        respondWith(200, JsonOutput.toJson(Map.of(
            "patches", List.of(Map.of(
                "package_name", "org.example:foo",
                "version", "1.0",
                "patch", Map.of("name", "io.root.org.example:foo", "version", "1.0"),
                "patch_alias", Map.of("name", "io.root.org.example:foo", "version", "1.0-patched"),
                "cve_ids", List.of())),
            "skipped", List.of())));

        String result = noRetryClient().query("org.example:foo:1.0", "http://localhost:" + port, "test-key");

        assertEquals("io.root.org.example:foo:1.0-patched", result);
    }

    @Test
    void throwsGradleExceptionOn500() {
        respondWith(500, "");

        assertThrows(GradleException.class, () ->
            noRetryClient().query("org.example:foo:1.0", "http://localhost:" + port, "test-key"));
    }

    @Test
    void throwsGradleExceptionOn401() {
        respondWith(401, "");

        assertThrows(GradleException.class, () ->
            noRetryClient().query("org.example:foo:1.0", "http://localhost:" + port, "test-key"));
    }

    @Test
    void throwsGradleExceptionOnConnectionFailure() {
        // Port 1 has no server — connection will be refused
        assertThrows(GradleException.class, () ->
            noRetryClient().query("org.example:foo:1.0", "http://localhost:1", "test-key"));
    }

    @Test
    void retriesOn500AndEventuallySucceeds() {
        AtomicInteger callCount = new AtomicInteger(0);
        String successBody = JsonOutput.toJson(Map.of(
            "patches", List.of(Map.of(
                "patch_alias", Map.of("name", "io.root.org.example:foo", "version", "1.0-patched"))),
            "skipped", List.of()));

        server.createContext("/v3/analyze/maven", exchange -> {
            byte[] bytes;
            int status;
            if (callCount.incrementAndGet() < 3) {
                status = 500;
                bytes = new byte[0];
            } else {
                status = 200;
                bytes = successBody.getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(status, status == 200 ? bytes.length : -1);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        String result = retryClient(3).query("org.example:foo:1.0", "http://localhost:" + port, "test-key");

        assertEquals("io.root.org.example:foo:1.0-patched", result);
        assertEquals(3, callCount.get());
    }

    @Test
    void doesNotRetryOn4xx() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v3/analyze/maven", exchange -> {
            callCount.incrementAndGet();
            exchange.sendResponseHeaders(401, -1);
            exchange.getResponseBody().close();
        });

        assertThrows(GradleException.class, () ->
            retryClient(3).query("org.example:foo:1.0", "http://localhost:" + port, "test-key"));

        assertEquals(1, callCount.get());
    }

    @Test
    void sendsCorrectBasicAuthHeader() {
        final String TEST_API_KEY = "test-api-key";
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        server.createContext("/v3/analyze/maven", exchange -> {
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = JsonOutput.toJson(Map.of("patches", List.of())).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });

        noRetryClient().query("org.example:foo:1.0", "http://localhost:" + port, TEST_API_KEY);

        String expected = "Basic " + Base64.getEncoder().encodeToString((TEST_API_KEY + ":").getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, capturedAuth.get());
    }

    @Test
    void returnsNullAndSkipsRequestForMissingGroup() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v3/analyze/maven", exchange -> {
            callCount.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        });

        String result = noRetryClient().query(":artifact:1.0", "http://localhost:" + port, "test-key");

        assertNull(result);
        assertEquals(0, callCount.get());
    }

    @Test
    void returnsNullAndSkipsRequestForMissingArtifact() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v3/analyze/maven", exchange -> {
            callCount.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        });

        String result = noRetryClient().query("org.example::1.0", "http://localhost:" + port, "test-key");

        assertNull(result);
        assertEquals(0, callCount.get());
    }

    @Test
    void returnsNullAndSkipsRequestForMissingVersion() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v3/analyze/maven", exchange -> {
            callCount.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        });

        String result = noRetryClient().query("org.example:artifact:", "http://localhost:" + port, "test-key");

        assertNull(result);
        assertEquals(0, callCount.get());
    }

    private void respondWith(int status, String body) {
        server.createContext("/v3/analyze/maven", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, status == 200 ? bytes.length : -1);
            if (status == 200) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.getResponseBody().close();
            }
        });
    }
}
