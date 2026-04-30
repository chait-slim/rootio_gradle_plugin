package io.root.patcher;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class PkgRepoCredentialResolverTest {

    private static final String HTTP_PKG_URL = "https://pkg.example.com/maven";
    private static final String FILE_PKG_URL = "file:///local/repo";

    @TempDir
    File projectDir;

    private RootIoExtension extension;
    private ApiKeyResolver noOpApiKeyResolver;

    @BeforeEach
    void setUp() {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build();
        extension = project.getExtensions().create("rootio", RootIoExtension.class);
        extension.getPkgUrl().set(HTTP_PKG_URL); // default to HTTP; override per test when needed
        noOpApiKeyResolver = new ApiKeyResolver(k -> null); // no env vars
    }

    // Helper to apply a credentials action and capture the result
    private static PasswordCredentials applyAction(Action<? super PasswordCredentials> action) {
        SimplePasswordCredentials creds = new SimplePasswordCredentials();
        action.execute(creds);
        return creds;
    }

    // --- non-HTTP repo ---

    @Test
    void returnsNullForFileRepo() {
        extension.getPkgUrl().set(FILE_PKG_URL);
        extension.getPkgUsername().set("user");
        extension.getPkgPassword().set("pass");
        assertNull(PkgRepoCredentialResolver.resolve(extension, noOpApiKeyResolver, projectDir));
    }

    @Test
    void returnsNullForFileRepoEvenWithNoCredentials() {
        extension.getPkgUrl().set(FILE_PKG_URL);
        assertNull(PkgRepoCredentialResolver.resolve(extension, noOpApiKeyResolver, projectDir));
    }

    // --- explicit pkgUsername + pkgPassword ---

    @Test
    void usesExplicitPkgCredentialsWhenBothSet() {
        final String USERNAME = "user";
        final String PASSWORD = "pass";
        extension.getPkgUsername().set(USERNAME);
        extension.getPkgPassword().set(PASSWORD);
        extension.getApiKey().set("api-key");

        Action<? super PasswordCredentials> action =
            PkgRepoCredentialResolver.resolve(extension, noOpApiKeyResolver, projectDir);
        assertNotNull(action);
        PasswordCredentials creds = applyAction(action);
        assertEquals(USERNAME, creds.getUsername());
        assertEquals(PASSWORD, creds.getPassword());
    }

    @Test
    void explicitPkgCredentialsTakePrecedenceOverApiKey() {
        final String USERNAME = "user";
        final String PASSWORD = "pass";
        extension.getPkgUsername().set(USERNAME);
        extension.getPkgPassword().set(PASSWORD);
        extension.getApiKey().set("some-api-key");

        Action<? super PasswordCredentials> action =
            PkgRepoCredentialResolver.resolve(extension, noOpApiKeyResolver, projectDir);
        assertNotNull(action);
        PasswordCredentials creds = applyAction(action);
        assertEquals(USERNAME, creds.getUsername());
        assertEquals(PASSWORD, creds.getPassword());
    }

    // --- partial explicit credentials fall through to apiKey ---

    @Test
    void fallsBackToApiKeyWhenOnlyPkgUsernameSet() {
        final String API_KEY = "apikey";
        extension.getPkgUsername().set("user");
        extension.getApiKey().set(API_KEY);

        Action<? super PasswordCredentials> action =
            PkgRepoCredentialResolver.resolve(extension, noOpApiKeyResolver, projectDir);
        assertNotNull(action);
        PasswordCredentials creds = applyAction(action);
        assertEquals(PkgRepoCredentialResolver.DEFAULT_PKG_USERNAME, creds.getUsername());
        assertEquals(API_KEY, creds.getPassword());
    }

    @Test
    void fallsBackToApiKeyWhenOnlyPkgPasswordSet() {
        final String API_KEY = "apikey";
        extension.getPkgPassword().set("password");
        extension.getApiKey().set(API_KEY);

        Action<? super PasswordCredentials> action =
            PkgRepoCredentialResolver.resolve(extension, noOpApiKeyResolver, projectDir);
        assertNotNull(action);
        PasswordCredentials creds = applyAction(action);
        assertEquals(PkgRepoCredentialResolver.DEFAULT_PKG_USERNAME, creds.getUsername());
        assertEquals(API_KEY, creds.getPassword());
    }

    // --- apiKey fallback ---

    @Test
    void usesApiKeyWithDefaultUsernameWhenNoPkgCredentials() {
        final String API_KEY = "apikey";
        extension.getApiKey().set(API_KEY);

        Action<? super PasswordCredentials> action =
            PkgRepoCredentialResolver.resolve(extension, noOpApiKeyResolver, projectDir);
        assertNotNull(action);
        PasswordCredentials creds = applyAction(action);
        assertEquals(PkgRepoCredentialResolver.DEFAULT_PKG_USERNAME, creds.getUsername());
        assertEquals(API_KEY, creds.getPassword());
    }

    @Test
    void resolvesApiKeyFromDotEnvFileWhenNotSetOnExtension() throws IOException {
        Files.writeString(new File(projectDir, ".env").toPath(), "ROOTIO_API_KEY=from-dotenv\n");
        ApiKeyResolver dotEnvResolver = new ApiKeyResolver(k -> null);

        Action<? super PasswordCredentials> action =
            PkgRepoCredentialResolver.resolve(extension, dotEnvResolver, projectDir);
        assertNotNull(action);
        PasswordCredentials creds = applyAction(action);
        assertEquals(PkgRepoCredentialResolver.DEFAULT_PKG_USERNAME, creds.getUsername());
        assertEquals("from-dotenv", creds.getPassword());
    }

    // --- no credentials at all ---

    @Test
    void returnsNullWhenNoCredentialsAndNoApiKey() {
        assertNull(PkgRepoCredentialResolver.resolve(extension, noOpApiKeyResolver, projectDir));
    }

    // --- simple PasswordCredentials implementation for capturing action results ---

    private static class SimplePasswordCredentials implements PasswordCredentials {
        private String username;
        private String password;

        @Override public String getUsername() { return username; }
        @Override public void setUsername(String username) { this.username = username; }
        @Override public String getPassword() { return password; }
        @Override public void setPassword(String password) { this.password = password; }
    }
}
