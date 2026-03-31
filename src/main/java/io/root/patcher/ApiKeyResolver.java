package io.root.patcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.function.Function;

public class ApiKeyResolver {
    static final String KEY = "ROOTIO_API_KEY";

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
        String result = null;

        String fromDotEnv = readDotEnv(projectRootDir);
        if (fromDotEnv != null && !fromDotEnv.isEmpty()) result = fromDotEnv;

        String fromSysProp = System.getProperty(KEY);
        if (fromSysProp != null && !fromSysProp.isEmpty()) result = fromSysProp;

        String fromEnvVar = envVarReader.apply(KEY);
        if (fromEnvVar != null && !fromEnvVar.isEmpty()) result = fromEnvVar;

        return Optional.ofNullable(result);
    }

    private String readDotEnv(File projectRootDir) {
        File dotEnvFile = new File(projectRootDir, ".env");
        if (!dotEnvFile.exists()) return null;
        try {
            for (String line : Files.readAllLines(dotEnvFile.toPath())) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq < 0) continue;
                String k = trimmed.substring(0, eq).trim();
                if (KEY.equals(k)) {
                    return trimmed.substring(eq + 1).trim();
                }
            }
        } catch (IOException e) {
            // Ignore unreadable .env files — missing key will be caught at build time
        }
        return null;
    }
}
