package io.root.patcher;

import java.util.Map;

public class RootDependency {
    private String group;
    private String name;
    private String version;

    public RootDependency(String group, String name, String version) {
        this.group = group;
        this.name = name;
        this.version = version;
    }

    public RootDependency(String coords) {
        String[] parts = coords.split(":");
        this.group = parts[0];
        this.name = parts[1];
        this.version = parts[2];
    }

    public Map<String, String> toBuildTarget() {
        return Map.of("group", group, "name", name, "version", version);
    }
}
