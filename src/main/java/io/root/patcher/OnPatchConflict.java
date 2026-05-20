package io.root.patcher;

/**
 * Policy for resolving a capability collision between a Root.io-patched coord
 * (group prefix {@code io.root.}) and its unpatched upstream sibling.
 *
 * <p>Wired into the plugin's per-configuration {@code capabilitiesResolution} rule.
 * Default: {@link #PREFER_PATCH}.
 */
public enum OnPatchConflict {
    /**
     * The {@code io.root.*} variant always wins. Guarantees the audited patched
     * code runs even when a BOM supplies a numerically-newer clean version.
     * This is the default.
     */
    PREFER_PATCH,

    /**
     * {@code selectHighestVersion()} over capability versions. The unpatched
     * sibling will usually win because the {@code -root.io.N} suffix sorts as
     * a pre-release qualifier under Maven's {@code ComparableVersion}. Useful
     * when the user explicitly pinned a newer version and wants it.
     */
    PREFER_NEWEST,

    /**
     * Raise a {@link org.gradle.api.GradleException} listing the colliding
     * coords. Useful as a CI gate where any unresolved patch conflict must
     * surface to a human.
     */
    FAIL
}
