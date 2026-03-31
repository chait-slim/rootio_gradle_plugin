package io.root.patcher;

import java.util.Map;

public class RootDependency {
    private final String group;
    private final String name;
    private final String version;

    public RootDependency(String group, String name, String version) {
        this.group = group;
        this.name = name;
        this.version = version;
    }

    public RootDependency(String coords) {
        String[] parts = coords.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid coordinates: " + coords);
        }

        this.group = parts[0];
        this.name = parts[1];
        this.version = parts[2];
    }

    public Map<String, String> toBuildTarget() {
        return Map.of("group", group, "name", name, "version", version);
    }
}
