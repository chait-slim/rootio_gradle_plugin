package io.root.patcher;

/**
 * Policy for resolving a capability collision between a Root.io-patched coord
 * (group prefix {@code io.root.}) and its unpatched upstream sibling.
 *
 * <p>Wired into the plugin's per-configuration {@code capabilitiesResolution} rule.
 * Default: {@link #PREFER_NEWEST}.
 */
public enum OnPatchConflict {
    /**
     * Default. Compares capability versions and picks the highest. In the
     * canonical BOM-upgrade case ({@code logback-core:1.1.3} transitively
     * patched alongside BOM-supplied {@code logback-core:1.5.x}), the BOM
     * version wins — the same outcome the user would have got without the
     * plugin's group rewrite, preserving the API surface their application
     * code expects.
     *
     * <p>In the rare same-version case (patched {@code 1.5.32-root.io.1}
     * alongside clean {@code 1.5.32}), capability versions tie at
     * {@code 1.5.32} on both sides and Gradle's tie-break may pick either.
     * Users who require the patched code to win in that case can override
     * via standard Gradle dependency substitution:
     *
     * <pre>{@code
     * configurations.all {
     *     resolutionStrategy.dependencySubstitution {
     *         substitute(module("ch.qos.logback:logback-core:1.5.32"))
     *             .using(module("io.root.ch.qos.logback:logback-core:1.5.32-root.io.1"))
     *     }
     * }
     * }</pre>
     */
    PREFER_NEWEST,

    /**
     * Raise a {@link org.gradle.api.GradleException} listing the colliding
     * coords. Useful as a CI gate where any unresolved patch conflict must
     * surface to a human.
     */
    FAIL
}
