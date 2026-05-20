package io.root.patcher;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RootIoCapabilityRule}.
 *
 * <p>The rule is exercised via a real {@link Project} from {@link ProjectBuilder}: the
 * test declares a metadata-source dependency on a fake coord, applies the rule via
 * {@code components.all(...)}, and asserts on the resulting capability set by querying
 * Gradle's resolution model. Mocking {@code ComponentMetadataContext} directly is
 * brittle across Gradle versions, so the test uses Gradle's own machinery to invoke
 * the rule end-to-end.
 *
 * <p>For each scenario the test class plants a fake artifact in a local file repo,
 * registers the rule on the project's dependencies, then resolves a configuration that
 * depends on the fake coord and inspects the resolution-result capability list.
 */
class RootIoCapabilityRuleTest {

    private Project project;
    private java.io.File repoDir;

    @BeforeEach
    void setUp(@org.junit.jupiter.api.io.TempDir java.io.File tempDir) throws java.io.IOException {
        project = ProjectBuilder.builder().withProjectDir(tempDir).build();
        project.getPluginManager().apply("java");
        repoDir = new java.io.File(tempDir, "local-repo");
        project.getRepositories().maven(repo -> repo.setUrl(repoDir.toURI()));
        project.getDependencies().getComponents().all(RootIoCapabilityRule.class);
    }

    @Test
    void u1_canonicalPatchedCoord_addsOriginalCapability() throws Exception {
        plantFakeArtifact("io.root.ch.qos.logback", "logback-core", "1.1.3-root.io.1");
        project.getDependencies().add("implementation",
            "io.root.ch.qos.logback:logback-core:1.1.3-root.io.1");

        java.util.Set<String> caps = collectCapabilityCoords(
            "io.root.ch.qos.logback", "logback-core", "1.1.3-root.io.1");

        assertTrue(caps.contains("ch.qos.logback:logback-core:1.1.3"),
            "Expected original-coord capability injected, got: " + caps);
    }

    @Test
    void u2_multiSegmentGroup_doubleDigitN_parsesCorrectly() throws Exception {
        plantFakeArtifact("io.root.org.springframework.boot", "spring-boot-starter", "3.3.4-root.io.10");
        project.getDependencies().add("implementation",
            "io.root.org.springframework.boot:spring-boot-starter:3.3.4-root.io.10");

        java.util.Set<String> caps = collectCapabilityCoords(
            "io.root.org.springframework.boot", "spring-boot-starter", "3.3.4-root.io.10");

        assertTrue(caps.contains("org.springframework.boot:spring-boot-starter:3.3.4"),
            "Expected original capability with multi-segment group and double-digit N, got: " + caps);
    }

    @Test
    void u3_nonRootIoGroup_isNoOp() throws Exception {
        plantFakeArtifact("org.example", "my-lib", "1.0.0");
        project.getDependencies().add("implementation", "org.example:my-lib:1.0.0");

        java.util.Set<String> caps = collectCapabilityCoords(
            "org.example", "my-lib", "1.0.0");

        // Only the implicit default capability — no injected one.
        assertEquals(java.util.Set.of("org.example:my-lib:1.0.0"), caps,
            "Expected only the default capability, got: " + caps);
    }

    @Test
    void u4_rootIoGroupWithoutSuffix_isNoOp() throws Exception {
        plantFakeArtifact("io.root.foo", "bar", "1.0.0");
        project.getDependencies().add("implementation", "io.root.foo:bar:1.0.0");

        java.util.Set<String> caps = collectCapabilityCoords("io.root.foo", "bar", "1.0.0");

        assertFalse(caps.stream().anyMatch(c -> c.startsWith("foo:bar:")),
            "Expected NO original-coord capability injected (no -root.io.N suffix), got: " + caps);
    }

    @Test
    void u5_qualifierBeforeSuffix_endAnchoredStripPreservesQualifier() throws Exception {
        plantFakeArtifact("io.root.ch.qos.logback", "logback-core", "1.1.3-final-root.io.1");
        project.getDependencies().add("implementation",
            "io.root.ch.qos.logback:logback-core:1.1.3-final-root.io.1");

        java.util.Set<String> caps = collectCapabilityCoords(
            "io.root.ch.qos.logback", "logback-core", "1.1.3-final-root.io.1");

        assertTrue(caps.contains("ch.qos.logback:logback-core:1.1.3-final"),
            "Expected qualifier-preserving capability, got: " + caps);
    }

    @Test
    void u6_doubleDigitN_matchesPattern() throws Exception {
        plantFakeArtifact("io.root.ch.qos.logback", "logback-core", "1.1.3-root.io.10");
        project.getDependencies().add("implementation",
            "io.root.ch.qos.logback:logback-core:1.1.3-root.io.10");

        java.util.Set<String> caps = collectCapabilityCoords(
            "io.root.ch.qos.logback", "logback-core", "1.1.3-root.io.10");

        assertTrue(caps.contains("ch.qos.logback:logback-core:1.1.3"),
            "Expected double-digit N to match, got: " + caps);
    }

    // --- Helpers ---

    /**
     * Drops a minimal POM + empty JAR into the local file repo so Gradle's resolver can
     * fetch the artifact and run the metadata rule against it.
     */
    private void plantFakeArtifact(String group, String artifact, String version) throws java.io.IOException {
        java.io.File dir = new java.io.File(repoDir,
            group.replace('.', '/') + "/" + artifact + "/" + version);
        dir.mkdirs();
        String pom = "<project><modelVersion>4.0.0</modelVersion>"
            + "<groupId>" + group + "</groupId>"
            + "<artifactId>" + artifact + "</artifactId>"
            + "<version>" + version + "</version>"
            + "<packaging>jar</packaging></project>";
        java.nio.file.Files.writeString(
            new java.io.File(dir, artifact + "-" + version + ".pom").toPath(), pom);
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                new java.io.FileOutputStream(new java.io.File(dir, artifact + "-" + version + ".jar")))) {
            // empty zip is a valid jar
        }
    }

    /**
     * Resolves the compileClasspath and returns the set of capability coords
     * {@code (group, name, version)} declared by the variant matching the given coord.
     * Capability coords are flattened to {@code group:name:version} strings for assertion.
     */
    private java.util.Set<String> collectCapabilityCoords(String group, String artifact, String version) {
        org.gradle.api.artifacts.Configuration cfg =
            project.getConfigurations().getByName("compileClasspath");
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        cfg.getIncoming().getResolutionResult().allComponents(component -> {
            org.gradle.api.artifacts.ModuleVersionIdentifier id = component.getModuleVersion();
            if (id == null) return;
            if (!id.getGroup().equals(group) || !id.getName().equals(artifact) || !id.getVersion().equals(version)) {
                return;
            }
            component.getVariants().forEach(variant -> {
                variant.getCapabilities().forEach(cap ->
                    out.add(cap.getGroup() + ":" + cap.getName() + ":" + cap.getVersion()));
            });
        });
        return out;
    }
}
