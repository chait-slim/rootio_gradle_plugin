package io.root.patcher;

import org.gradle.api.GradleException;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class RootIoClient {
    // Shared across all query() calls within a build — reuses TLS connections
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .build();

    private static final String ENDPOINT_ANALYZE_MAVEN = "/v3/analyze/maven";

    private static final String REQUEST_PACKAGES = "packages";
    private static final String REQUEST_PACKAGE_NAME = "name";
    private static final String REQUEST_PACKAGE_VERSION = "version";

    private static final String RESPONSE_PATCHES = "patches";
    private static final String RESPONSE_PATCH_ALIAS = "patch_alias";

    /**
     * Query the Root.io API for a patch for the given dependency.
     *
     * @param coords  Maven GAV string — "group:artifact:version"
     * @param apiUrl  Root.io API base URL (e.g. "<a href="https://api.root.io">...</a>")
     * @param apiKey  Root.io API key (used as HTTP basic auth username)
     * @return patched GAV string ("io.root.group:artifact:version"), or null if no patch
     * @throws GradleException on non-200 response or network failure (fails the build)
     */
    public static String query(String coords, String apiUrl, String apiKey) {
        HttpRequest request = prepareHttpRequest(coords, apiUrl, apiKey);

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new GradleException(
                    "Root.io API returned HTTP " + response.statusCode() + " for " + coords);
            }
            return extractPatchedCoords(response.body());
        } catch (GradleException e) {
            // rethrow GradleException as-is, so that it's reported as a build failure
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException(
                "Root.io API request interrupted for " + coords, e);
        } catch (IOException e) {
            throw new GradleException(
                "Root.io API request failed for " + coords + ": " + e.getMessage(), e);
        }
    }

    private static HttpRequest prepareHttpRequest(String coords, String apiUrl, String apiKey) {
        // Split "group:artifact:version" — last colon separates version
        int lastColon = coords.lastIndexOf(':');
        String groupArtifact = coords.substring(0, lastColon);
        String version = coords.substring(lastColon + 1);

        String requestBody = JsonOutput.toJson(Map.of(
                REQUEST_PACKAGES,
                List.of(Map.of(
                        REQUEST_PACKAGE_NAME, groupArtifact,
                        REQUEST_PACKAGE_VERSION, version))));
        String endpoint = apiUrl.replaceAll("/$", "") + ENDPOINT_ANALYZE_MAVEN;

        String credentials = Base64.getEncoder()
            .encodeToString((apiKey + ":").getBytes(StandardCharsets.UTF_8));

        return HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic " + credentials)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();
    }

    @SuppressWarnings("unchecked")
    private static String extractPatchedCoords(String json) {
        try {
            Map<String, Object> root = (Map<String, Object>) new JsonSlurper().parseText(json);
            List<Map<String, Object>> patches = (List<Map<String, Object>>) root.get(RESPONSE_PATCHES);
            if (patches == null || patches.isEmpty()) {
                return null;
            }

            Map<String, Object> patchAlias = (Map<String, Object>) patches.get(0).get(RESPONSE_PATCH_ALIAS);
            if (patchAlias == null) {
                return null;
            }

            String name = (String) patchAlias.get(REQUEST_PACKAGE_NAME);
            String version = (String) patchAlias.get(REQUEST_PACKAGE_VERSION);
            if (name == null || name.isEmpty() || version == null || version.isEmpty()) return null;
            return name + ":" + version;
        } catch (ClassCastException e) {
            throw new GradleException("Root.io API returned unexpected JSON structure: " + e.getMessage(), e);
        }
    }
}
