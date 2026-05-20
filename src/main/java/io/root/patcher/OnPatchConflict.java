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
     * plugin, preserving the API surface their application code expects.
     *
     * <p>Trade-off: in the rare same-version case (patched
     * {@code 1.5.32-root.io.1} alongside clean {@code 1.5.32}), the
     * comparison is a tie at the capability-version level — {@code 1.5.32}
     * on both sides — and Gradle's tie-break may pick the clean sibling,
     * silently dropping the patch. Users who require the patched code in
     * all cases should explicitly set {@link #PREFER_PATCH}.
     */
    PREFER_NEWEST,

    /**
     * The {@code io.root.*} variant always wins. Guarantees the audited
     * patched code runs even when a BOM supplies a numerically-newer clean
     * version.
     *
     * <p>Trade-off: a patch from version {@code N} doesn't have APIs that
     * appeared in later versions. If application code or transitives call a
     * method that only exists in the BOM-supplied newer version, those calls
     * will fail at runtime with {@code NoSuchMethodError}. Use this policy
     * only when the security/audit posture genuinely requires the patched
     * code to win and the API delta has been reviewed.
     */
    PREFER_PATCH,

    /**
     * Raise a {@link org.gradle.api.GradleException} listing the colliding
     * coords. Useful as a CI gate where any unresolved patch conflict must
     * surface to a human.
     */
    FAIL
}
