package io.root.patcher;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RootDependencyTest {

    @Test
    void coordsConstructor_parsesGroupNameVersion() {
        RootDependency dep = new RootDependency("com.example:my-lib:2.3.4");

        Map<String, String> target = dep.toBuildTarget();
        assertEquals("com.example", target.get("group"));
        assertEquals("my-lib", target.get("name"));
        assertEquals("2.3.4", target.get("version"));
    }

    @Test
    void fieldsConstructor_setsGroupNameVersion() {
        RootDependency dep = new RootDependency("com.example", "my-lib", "2.3.4");

        Map<String, String> target = dep.toBuildTarget();
        assertEquals("com.example", target.get("group"));
        assertEquals("my-lib", target.get("name"));
        assertEquals("2.3.4", target.get("version"));
    }

    @Test
    void toBuildTarget_returnsAllThreeKeys() {
        RootDependency dep = new RootDependency("org.foo:bar:1.0");

        Map<String, String> target = dep.toBuildTarget();
        assertTrue(target.containsKey("group"));
        assertTrue(target.containsKey("name"));
        assertTrue(target.containsKey("version"));
        assertEquals(3, target.size());
    }

    @Test
    void coordsConstructor_missingVersion_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new RootDependency("com.example:my-lib"));
    }

    @Test
    void coordsConstructor_emptyString_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new RootDependency(""));
    }

    @Test
    void coordsConstructor_timestampedVersion_preservesFullVersion() {
        // Maven snapshot timestamps use the format "base:YYYYMMDD.HHmmss-n",
        // so the version field itself contains a colon. The parser must treat
        // everything after the second colon as the version.
        RootDependency dep = new RootDependency("com.example:my-lib:1.0:20260101.120000-1");

        Map<String, String> target = dep.toBuildTarget();
        assertEquals("com.example", target.get("group"));
        assertEquals("my-lib", target.get("name"));
        assertEquals("1.0:20260101.120000-1", target.get("version"));
    }
}