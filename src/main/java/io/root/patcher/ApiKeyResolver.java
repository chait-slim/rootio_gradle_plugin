package io.root.patcher;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.function.Function;

public class ApiKeyResolver {
    private static final String ROOTIO_API_KEY = "ROOTIO_API_KEY";
    private static final Logger logger = Logging.getLogger(ApiKeyResolver.class);

    private final Function<String, String> envVarReader;

    public ApiKeyResolver() {
        this(System::getenv);
    }

    /** Package-private constructor for tests — allows injecting a fake env var reader. */
    ApiKeyResolver(Function<String, String> envVarReader) {
        this.envVarReader = envVarReader;
    }

    /**
     * Resolves the Root.io API key from available sources in ascending priority order:
     * .env file < system property < environment variable.
     * Returns Optional.empty() if no source provides a non-empty value.
     */
    public Optional<String> resolve(File projectRootDir) {
        String fromEnvVar = envVarReader.apply(ROOTIO_API_KEY);
        if (fromEnvVar != null && !fromEnvVar.isEmpty()) {
            return Optional.of(fromEnvVar);
        }

        String fromSysProp = System.getProperty(ROOTIO_API_KEY);
        if (fromSysProp != null && !fromSysProp.isEmpty()) {
            return Optional.of(fromSysProp);
        }

        String fromDotEnv = readDotEnv(projectRootDir);
        if (fromDotEnv != null && !fromDotEnv.isEmpty()) {
            return Optional.of(fromDotEnv);
        }

        return Optional.empty();
    }

    /**
     * Resolves the API key from all sources in descending priority order:
     * explicit override > environment variable > system property > .env file.
     * Throws {@link GradleException} if no source provides a non-empty value.
     *
     * @param explicitKey value set directly in the build script or passed via extension/parameters; may be null
     */
    public String resolveOrThrow(@Nullable String explicitKey, File projectRootDir) {
        return Optional.ofNullable(explicitKey)
            .filter(k -> !k.isEmpty())
            .or(() -> resolve(projectRootDir))
            .orElseThrow(() -> new GradleException(
                "rootIo.apiKey must be set — provide it via the rootio { } block, " +
                "ROOTIO_API_KEY env var, system property, or .env file"));
    }

    private String readDotEnv(File projectRootDir) {
        File dotEnvFile = new File(projectRootDir, ".env");
        if (!dotEnvFile.exists()) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(dotEnvFile.toPath())) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String k = trimmed.substring(0, eq).trim();
                if (ROOTIO_API_KEY.equals(k)) {
                    return trimmed.substring(eq + 1).trim();
                }
            }
        } catch (IOException e) {
            // Ignore unreadable .env files — missing key will be caught at build time
            logger.warn("Failed to read .env file: " + e.getMessage(), e);
        }
        return null;
    }
}
