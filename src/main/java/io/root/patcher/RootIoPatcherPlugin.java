package io.root.patcher;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.authentication.http.BasicAuthentication;

public class RootIoPatcherPlugin implements Plugin<Project> {
    private static final Logger logger = Logging.getLogger(RootIoPatcherPlugin.class);
    private static final String MAVEN_REPO_USERNAME = "token";

    @Override
    public void apply(Project project) {
        RootIoExtension extension = project.getExtensions().create("rootio", RootIoExtension.class);
        extension.getApiUrl().convention(envOrDefault("ROOTIO_API_URL", "https://api.root.io"));
        extension.getPkgUrl().convention(envOrDefault("ROOTIO_PKG_URL", "https://pkg.root.io"));
        extension.getTtlHours().convention(24L);
        extension.getMaxRetries().convention(3);
        extension.getRetryBaseDelayMs().convention(1000L);
        extension.getAllowInsecurePkgRepo().convention(false);
        // apiKey resolved automatically from .env, systemProp, or env var
        new ApiKeyResolver().resolve(project.getRootDir())
            .ifPresent(key -> extension.getApiKey().convention(key));

        project.afterEvaluate(p -> registerRootMavenRepo(p, extension));

        project.getConfigurations().all(config -> {
            // Only hook resolvable configurations — non-resolvable ones (e.g. `api`, `implementation`)
            // are for declaring dependencies and do not support eachDependency. Their dependencies
            // are still patched because resolvable configurations (e.g. `compileClasspath`,
            // `runtimeClasspath`) inherit from them and are processed below.
            if (!config.isCanBeResolved()) {
                return;
            }

            config.getResolutionStrategy().eachDependency(details ->
                    handleDependency(project, details, extension));
        });
    }

    // Auto-register the Root.io patches Maven repository so patched artifacts resolve
    // without users needing to add it manually. Done in afterEvaluate so apiKey/pkgUrl
    // are fully configured by the time we read them.
    private static void registerRootMavenRepo(Project p, RootIoExtension extension) {
        String pkgBase = extension.getPkgUrl().get().replaceAll("/$", "");
        String key = extension.getApiKey().getOrElse("(not set)");
        String masked = key.length() > 8 ? key.substring(0, 4) + "..." + key.substring(key.length() - 4) : "(too short or not set)";
        logger.info("Registering repo: {}/maven (apiKey: {})", pkgBase, masked);
        p.getRepositories().maven(repo -> {
            repo.setName("Root.io patches");
            repo.setUrl(pkgBase + "/maven");
            // Credentials only apply to HTTP(S) — file:// repos (e.g. in tests) reject them.
            // BasicAuthentication forces preemptive auth so credentials are sent on the
            // first request. Without it, Gradle waits for a 401 challenge, but artrepo
            // returns 403 directly for unauthenticated requests.
            if (pkgBase.startsWith("http://") || pkgBase.startsWith("https://")) {
                repo.credentials(creds -> {
                    creds.setUsername(MAVEN_REPO_USERNAME);
                    creds.setPassword(extension.getApiKey().get());
                });
                repo.authentication(auth -> auth.create("basic", BasicAuthentication.class));
            }
            if (extension.getAllowInsecurePkgRepo().get()) {
                repo.setAllowInsecureProtocol(true);
            }
        });
    }

    private static void handleDependency(Project project, DependencyResolveDetails details, RootIoExtension extension) {
        ModuleVersionSelector req = details.getRequested();
        String version = req.getVersion();

        // Skip deps with no version — these are BOM/platform-managed or Kotlin-plugin-managed
        // deps whose version is resolved separately. Sending an empty version to the API
        // produces a 400.
        if (version == null || version.isEmpty()) {
            logger.info("Skipping {}:{} (no version)", req.getGroup(), req.getName());
            return;
        }

        String coords = req.getGroup() + ":" + req.getName() + ":" + version;

        resolvePatchedDependency(project, details, coords, extension);
    }

    private static void resolvePatchedDependency(
            Project project,
            DependencyResolveDetails details,
            String coords,
            RootIoExtension ext
    ) {
        Provider<String> patchedProvider = project.getProviders().of(RootIoValueSource.class, spec -> {
            spec.getParameters().getCoords().set(coords);
            spec.getParameters().getApiUrl().set(ext.getApiUrl());
            spec.getParameters().getApiKey().set(ext.getApiKey());
            spec.getParameters().getRootDirPath().set(project.getRootDir().getAbsolutePath());
            spec.getParameters().getTtlHours().set(ext.getTtlHours());
            spec.getParameters().getMaxRetries().set(ext.getMaxRetries());
            spec.getParameters().getRetryBaseDelayMs().set(ext.getRetryBaseDelayMs());
        });
        // gets from cache if available or resolves from Root.io if not
        String patched = patchedProvider.getOrNull();

        if (patched != null) {
            RootDependency dep = new RootDependency(patched);
            details.useTarget(dep.toBuildTarget());
            details.because("Root.io security patch");
            logger.info("Patching {} -> {}", coords, patched);
        } else {
            logger.info("No patch for {}", coords);
        }
    }

    private static String envOrDefault(String name, String defaultValue) {
        String val = System.getenv(name);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }
}
