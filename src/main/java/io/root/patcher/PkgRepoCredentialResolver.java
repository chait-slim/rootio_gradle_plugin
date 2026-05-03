package io.root.patcher;

import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.PasswordCredentials;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

/**
 * Resolves credentials for the pkg Maven repository.
 *
 * <p>Priority:
 * <ol>
 *   <li>Explicit {@code pkgUsername} + {@code pkgPassword} — use as-is (e.g. JFrog credentials).</li>
 *   <li>Root.io {@code apiKey} — used as the password with the fixed username {@code "token"}
 *       (the default {@code pkg.root.io} convention).</li>
 *   <li>Neither set — no credentials injected (anonymous or externally managed).</li>
 * </ol>
 *
 * <p>Note: {@code apiKey} is separate from pkg repo credentials. It is used exclusively for
 * authenticating against the Root.io {@code /v3/analyze} API. When the API becomes open,
 * {@code apiKey} will no longer be required and pkg repo credentials will be the only thing needed.
 */
public class PkgRepoCredentialResolver {

    static final String DEFAULT_PKG_USERNAME = "token";

    static final List<String> REMOTE_PREFIXES = List.of("https://", "http://");

    /**
     * Returns an {@link Action} that applies credentials to a Gradle {@link PasswordCredentials},
     * or {@code null} if no credentials should be used (anonymous or file:// repo).
     *
     * @param extension      the rootio extension; {@code pkgUrl} is read to determine if the repo
     *                       is HTTP(S) — file:// repos never get credentials
     * @param apiKeyResolver used to resolve the apiKey from env/system-property/.env as a fallback
     * @param rootDir        root directory passed to {@code apiKeyResolver}
     * @return an {@link Action} that configures credentials, or {@code null} for anonymous/file repos
     */
    @Nullable
    public static Action<? super PasswordCredentials> resolve(
            RootIoExtension extension,
            ApiKeyResolver apiKeyResolver,
            File rootDir) {
        // Credentials only apply to HTTP(S) — file:// repos (e.g. in tests) reject them.
        // BasicAuthentication forces preemptive auth so credentials are sent on the
        // first request. Without it, Gradle waits for a 401 challenge, but artrepo
        // returns 403 directly for unauthenticated requests.
        if (!isRemoteRepo(extension.getPkgUrl().get())) {
            return null;
        }

        String pkgUsername = extension.getPkgUsername().getOrNull();
        String pkgPassword = extension.getPkgPassword().getOrNull();
        if (!isEmpty(pkgUsername) && !isEmpty(pkgPassword)) {
            return creds -> {
                creds.setUsername(pkgUsername);
                creds.setPassword(pkgPassword);
            };
        }

        String apiKey = extension.getApiKey().getOrNull();
        if (isEmpty(apiKey)) {
            apiKey = apiKeyResolver.resolve(rootDir).orElse(null);
        }
        if (!isEmpty(apiKey)) {
            String resolvedApiKey = apiKey;
            return creds -> {
                creds.setUsername(DEFAULT_PKG_USERNAME);
                creds.setPassword(resolvedApiKey);
            };
        }

        return null;
    }

    private static boolean isEmpty(@Nullable String s) {
        return s == null || s.isEmpty();
    }

    private static boolean isRemoteRepo(String url) {
        for (String prefix : REMOTE_PREFIXES) {
            if (url.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }
}
