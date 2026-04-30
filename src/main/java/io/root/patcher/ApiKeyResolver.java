package io.root.patcher;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

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
                String[] parts = trimmed.split("=", 2);
                if (parts.length < 2) {
                    continue;
                }
                if (ROOTIO_API_KEY.equals(parts[0].trim())) {
                    return parts[1].trim();
                }
            }
        } catch (IOException e) {
            // Ignore unreadable .env files — missing key will be caught at build time
            logger.warn("Failed to read .env file: " + e.getMessage(), e);
        }
        return null;
    }
}
