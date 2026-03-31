package io.root.patcher;

import com.sun.net.httpserver.HttpServer;
import groovy.json.JsonOutput;
import groovy.text.SimpleTemplateEngine;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class RootIoPatcherPluginFunctionalTest {

    @TempDir
    File projectDir;

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

    @Test
    void substitutesDepWhenPatchAvailable() throws IOException {
        setupServerResponse(200, patchResponseJson("io.test:my-lib", "1.0.0", "io.root.io.test:my-lib", "1.0.0-patched"));
        writeProjectFiles();

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withGradleVersion("9.4.1")
            .withArguments("dependencies", "--configuration", "compileClasspath")
            .build();

        assertTrue(result.getOutput().contains("io.root.io.test:my-lib:1.0.0-patched"),
            "Expected patched coordinates in output:\n" + result.getOutput());
        assertFalse(result.getOutput().contains("io.test:my-lib:1.0.0\n"),
            "Expected original dependency to be substituted, not resolved as-is:\n" + result.getOutput());
    }

    @Test
    void doesNotSubstituteWhenNoPatchAvailable() throws IOException {
        setupServerResponse(200, emptyPatchResponseJson());
        writeProjectFiles();

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withGradleVersion("9.4.1")
            .withArguments("dependencies", "--configuration", "compileClasspath")
            .build();

        assertTrue(result.getOutput().contains("io.test:my-lib:1.0.0"),
            "Expected original coordinates in output:\n" + result.getOutput());
        assertFalse(result.getOutput().contains("io.root.io.test"),
            "Expected no substitution in output:\n" + result.getOutput());
    }

    @Test
    void reasonStringAppearsInDependencyInsight() throws IOException {
        setupServerResponse(200, patchResponseJson("io.test:my-lib", "1.0.0", "io.root.io.test:my-lib", "1.0.0-patched"));
        writeProjectFiles();

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withGradleVersion("9.4.1")
            .withArguments("dependencyInsight", "--dependency", "io.test:my-lib",
                "--configuration", "compileClasspath")
            .build();

        assertTrue(result.getOutput().contains("Root.io security patch"),
            "Expected 'Root.io security patch' reason in dependencyInsight output:\n" + result.getOutput());
    }

    @Test
    void failsBuildWhenApiReturns500() throws IOException {
        setupServerResponse(500, "");
        writeProjectFiles();

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withGradleVersion("9.4.1")
            .withArguments("forceResolve")
            .buildAndFail();

        assertTrue(result.getOutput().contains("500") || result.getOutput().contains("Root.io"),
            "Expected error message about HTTP 500 in output:\n" + result.getOutput());
    }

    @Test
    void resolvesFromAutoRegisteredPkgRepo() throws IOException {
        // The plugin must auto-register {pkgUrl}/maven-patches so patched artifacts resolve
        // without the user needing to add the repository manually.
        setupServerResponse(200, patchResponseJson("io.test:my-lib", "1.0.0", "io.root.io.test:my-lib", "1.0.0-patched"));

        // Original artifact in the project's own repo; patched artifact ONLY in the pkg repo.
        // The build script does NOT declare the pkg repo — the plugin must add it automatically.
        File repoDir = new File(projectDir, "local-repo");
        File pkgRepoDir = new File(projectDir, "pkg-repo");
        createFakeArtifact(repoDir, "io.test", "my-lib", "1.0.0");
        // Plugin appends /maven to pkgUrl, so the artifact must live in that subdirectory.
        createFakeArtifact(new File(pkgRepoDir, "maven"), "io.root.io.test", "my-lib", "1.0.0-patched");

        Files.writeString(new File(projectDir, "settings.gradle.kts").toPath(),
            "rootProject.name = \"test-project\"\n");
        writeBuildGradleKts(repoDir, "http://localhost:" + port, "test-key",
            pkgRepoDir.toURI().toString().replaceAll("/$", ""), true);

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withGradleVersion("9.4.1")
            .withArguments("forceResolve")
            .build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"),
            "Expected patched artifact to resolve from auto-registered pkg repo:\n" + result.getOutput());
    }

    @Test
    void worksWithConfigurationCache() throws IOException {
        setupServerResponse(200, patchResponseJson("io.test:my-lib", "1.0.0", "io.root.io.test:my-lib", "1.0.0-patched"));
        writeProjectFiles();

        // First build: stores the configuration cache
        BuildResult first = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withGradleVersion("9.4.1")
            .withArguments("--configuration-cache", "dependencies", "--configuration", "compileClasspath")
            .build();

        assertTrue(first.getOutput().contains("io.root.io.test:my-lib:1.0.0-patched"),
            "Expected patched coordinates in first build output:\n" + first.getOutput());

        // Second build: must reuse the stored configuration cache
        BuildResult second = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withGradleVersion("9.4.1")
            .withArguments("--configuration-cache", "dependencies", "--configuration", "compileClasspath")
            .build();

        assertTrue(
            second.getOutput().contains("Configuration cache entry reused") ||
            second.getOutput().contains("Reusing configuration cache"),
            "Expected second build to reuse configuration cache:\n" + second.getOutput());
        assertTrue(second.getOutput().contains("io.root.io.test:my-lib:1.0.0-patched"),
            "Expected patched coordinates in second build output:\n" + second.getOutput());
    }

    @Test
    void resolvesApiKeyFromDotEnvFile() throws IOException {
        setupServerResponse(200, emptyPatchResponseJson());

        File repoDir = new File(projectDir, "local-repo");
        createFakeArtifact(repoDir, "io.test", "my-lib", "1.0.0");

        // Write a .env file with the API key — no apiKey.set() in the build script
        Files.writeString(new File(projectDir, ".env").toPath(), "ROOTIO_API_KEY=test-key\n");

        Files.writeString(new File(projectDir, "settings.gradle.kts").toPath(),
            "rootProject.name = \"test-project\"\n");
        // No apiKey.set() — key must come from .env file
        writeBuildGradleKts(repoDir, "http://localhost:" + port, null, null, false);

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withGradleVersion("9.4.1")
            .withArguments("dependencies", "--configuration", "compileClasspath")
            .build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"),
            "Expected build to succeed when API key is resolved from .env file:\n" + result.getOutput());
    }

    // --- Helpers ---

    private static final String BUILD_SCRIPT_TEMPLATE =
        "plugins {\n" +
        "    java\n" +
        "    id(\"io.root.patcher\")\n" +
        "}\n" +
        "repositories {\n" +
        "    maven { url = uri(\"${repoUri}\") }\n" +
        "}\n" +
        "dependencies {\n" +
        "    implementation(\"io.test:my-lib:1.0.0\")\n" +
        "}\n" +
        "rootio {\n" +
        "${rootioConfig}" +
        "}\n" +
        "${extraTasks}";

    // Writes build.gradle.kts using SimpleTemplateEngine to bind runtime values into the template.
    // apiKey and pkgUrl are optional (pass null to omit).
    // forceResolve uses strict (non-lenient) resolution — fails the build if any dep throws
    // during eachDependency. The `dependencies` task uses lenient resolution and only marks
    // deps FAILED without failing the overall build.
    private void writeBuildGradleKts(File repoDir, String apiUrl, String apiKey, String pkgUrl,
            boolean includeForceResolve) throws IOException {
        StringBuilder rootioConfig = new StringBuilder();
        if (apiKey != null) rootioConfig.append("    apiKey.set(\"").append(apiKey).append("\")\n");
        rootioConfig.append("    apiUrl.set(\"").append(apiUrl).append("\")\n");
        if (pkgUrl != null) rootioConfig.append("    pkgUrl.set(\"").append(pkgUrl).append("\")\n");

        String forceResolveTask = includeForceResolve
            ? "tasks.register(\"forceResolve\") {\n" +
              "    doLast {\n" +
              "        configurations[\"compileClasspath\"].files\n" +
              "    }\n" +
              "}\n"
            : "";

        Map<String, Object> bindings = new HashMap<>(){{
            put("repoUri", repoDir.toURI().toString());
            put("rootioConfig", rootioConfig.toString());
            put("extraTasks", forceResolveTask);
        }};

        try {
            String content = new SimpleTemplateEngine()
                .createTemplate(BUILD_SCRIPT_TEMPLATE)
                .make(bindings)
                .toString();
            Files.writeString(new File(projectDir, "build.gradle.kts").toPath(), content);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static String patchResponseJson(String packageName, String version, String patchedName, String patchedVersion) {
        Map<String, Object> patchAlias = Map.of("name", patchedName, "version", patchedVersion);
        Map<String, Object> patch = new java.util.LinkedHashMap<>();
        patch.put("package_name", packageName);
        patch.put("version", version);
        patch.put("patch_alias", patchAlias);
        patch.put("cve_ids", List.of());
        return JsonOutput.toJson(Map.of("patches", List.of(patch), "skipped", List.of()));
    }

    private static String emptyPatchResponseJson() {
        return JsonOutput.toJson(Map.of("patches", List.of(), "skipped", List.of()));
    }

    private void setupServerResponse(int status, String body) {
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

    private void writeProjectFiles() throws IOException {
        File repoDir = new File(projectDir, "local-repo");
        createFakeArtifact(repoDir, "io.test", "my-lib", "1.0.0");
        createFakeArtifact(repoDir, "io.root.io.test", "my-lib", "1.0.0-patched");

        Files.writeString(new File(projectDir, "settings.gradle.kts").toPath(),
            "rootProject.name = \"test-project\"\n");
        writeBuildGradleKts(repoDir, "http://localhost:" + port, "test-key", null, true);
    }

    /**
     * Creates a minimal Maven artifact (POM + empty valid JAR) in a local file repository.
     * Gradle needs a valid ZIP/JAR file to successfully resolve the artifact.
     */
    private void createFakeArtifact(File repoDir, String group, String artifact, String version)
            throws IOException {
        File dir = new File(repoDir, group.replace('.', '/') + "/" + artifact + "/" + version);
        dir.mkdirs();

        String pom = "<project><modelVersion>4.0.0</modelVersion>" +
            "<groupId>" + group + "</groupId>" +
            "<artifactId>" + artifact + "</artifactId>" +
            "<version>" + version + "</version>" +
            "<packaging>jar</packaging></project>";
        Files.writeString(new File(dir, artifact + "-" + version + ".pom").toPath(), pom);

        // Create a valid (empty) JAR — JARs are ZIP files; an empty ZIP is valid
        try (ZipOutputStream zos = new ZipOutputStream(
                new FileOutputStream(new File(dir, artifact + "-" + version + ".jar")))) {
            // intentionally empty
        }
    }
}
