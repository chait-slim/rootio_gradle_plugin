package io.root.patcher;

import java.util.Map;

/** Represents a Maven dependency as separate group, name, and version components. */
public class RootDependency {
    private final String group;
    private final String name;
    private final String version;

    /**
     * @param group    Maven group ID
     * @param name     Maven artifact ID
     * @param version  artifact version
     */
    public RootDependency(String group, String name, String version) {
        this.group = group;
        this.name = name;
        this.version = version;
    }

    /**
     * Parses a Maven GAV string of the form {@code group:artifact:version}.
     *
     * @param coords Maven GAV string
     * @throws IllegalArgumentException if {@code coords} does not contain exactly two colons
     */
    public RootDependency(String coords) {
        String[] parts = coords.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid coordinates: " + coords);
        }

        this.group = parts[0];
        this.name = parts[1];
        this.version = parts[2];
    }

    /**
     * Returns a map with {@code group}, {@code name}, and {@code version} keys,
     * suitable for passing to Gradle's {@code useTarget}.
     *
     * @return dependency coordinates as a map
     */
    public Map<String, String> toBuildTarget() {
        return Map.of("group", group, "name", name, "version", version);
    }
}
