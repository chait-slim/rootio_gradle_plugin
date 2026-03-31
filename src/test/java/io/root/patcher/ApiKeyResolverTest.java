package io.root.patcher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyResolverTest {

    @TempDir
    File projectDir;

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty("ROOTIO_API_KEY");
    }

    @Test
    void returnsEmptyWhenNothingSet() {
        ApiKeyResolver resolver = new ApiKeyResolver(k -> null);
        assertEquals(Optional.empty(), resolver.resolve(projectDir));
    }

    @Test
    void resolvesFromDotEnvFile() throws IOException {
        Files.writeString(new File(projectDir, ".env").toPath(), "ROOTIO_API_KEY=from-dotenv\n");
        ApiKeyResolver resolver = new ApiKeyResolver(k -> null);
        assertEquals(Optional.of("from-dotenv"), resolver.resolve(projectDir));
    }

    @Test
    void dotEnvSkipsBlankLinesAndComments() throws IOException {
        Files.writeString(new File(projectDir, ".env").toPath(),
            "# comment\n\nOTHER_KEY=other\nROOTIO_API_KEY=real-key\n");
        ApiKeyResolver resolver = new ApiKeyResolver(k -> null);
        assertEquals(Optional.of("real-key"), resolver.resolve(projectDir));
    }

    @Test
    void dotEnvHandlesValueWithEqualsSign() throws IOException {
        Files.writeString(new File(projectDir, ".env").toPath(), "ROOTIO_API_KEY=key==extra\n");
        ApiKeyResolver resolver = new ApiKeyResolver(k -> null);
        assertEquals(Optional.of("key==extra"), resolver.resolve(projectDir));
    }

    @Test
    void resolvesFromSystemProperty() {
        System.setProperty("ROOTIO_API_KEY", "from-sysprop");
        ApiKeyResolver resolver = new ApiKeyResolver(k -> null);
        assertEquals(Optional.of("from-sysprop"), resolver.resolve(projectDir));
    }

    @Test
    void resolvesFromEnvVar() {
        ApiKeyResolver resolver = new ApiKeyResolver(k -> "from-envvar");
        assertEquals(Optional.of("from-envvar"), resolver.resolve(projectDir));
    }

    @Test
    void envVarTakesPrecedenceOverSystemProperty() {
        System.setProperty("ROOTIO_API_KEY", "from-sysprop");
        ApiKeyResolver resolver = new ApiKeyResolver(k -> "from-envvar");
        assertEquals(Optional.of("from-envvar"), resolver.resolve(projectDir));
    }

    @Test
    void envVarTakesPrecedenceOverDotEnv() throws IOException {
        Files.writeString(new File(projectDir, ".env").toPath(), "ROOTIO_API_KEY=from-dotenv\n");
        ApiKeyResolver resolver = new ApiKeyResolver(k -> "from-envvar");
        assertEquals(Optional.of("from-envvar"), resolver.resolve(projectDir));
    }

    @Test
    void systemPropertyTakesPrecedenceOverDotEnv() throws IOException {
        Files.writeString(new File(projectDir, ".env").toPath(), "ROOTIO_API_KEY=from-dotenv\n");
        System.setProperty("ROOTIO_API_KEY", "from-sysprop");
        ApiKeyResolver resolver = new ApiKeyResolver(k -> null);
        assertEquals(Optional.of("from-sysprop"), resolver.resolve(projectDir));
    }

    @Test
    void dotEnvEmptyValueFallsThroughToSystemProperty() throws IOException {
        Files.writeString(new File(projectDir, ".env").toPath(), "ROOTIO_API_KEY=\n");
        System.setProperty("ROOTIO_API_KEY", "from-sysprop");
        ApiKeyResolver resolver = new ApiKeyResolver(k -> null);
        assertEquals(Optional.of("from-sysprop"), resolver.resolve(projectDir));
    }

    @Test
    void dotEnvValueIsTrimmed() throws IOException {
        Files.writeString(new File(projectDir, ".env").toPath(), "ROOTIO_API_KEY=  trimmed-key  \n");
        ApiKeyResolver resolver = new ApiKeyResolver(k -> null);
        assertEquals(Optional.of("trimmed-key"), resolver.resolve(projectDir));
    }
}
