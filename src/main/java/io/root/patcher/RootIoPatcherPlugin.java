package io.root.patcher;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.logging.Logger;
import org.gradle.authentication.http.BasicAuthentication;
import org.gradle.util.GradleVersion;

import java.util.Map;

import static io.root.patcher.RootIoExtension.LOG_PREFIX;

public class RootIoPatcherPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Fail fast if configuration cache is enabled — this plugin performs network I/O
        // during dependency resolution, which is incompatible with the configuration cache.
        if (isConfigurationCacheRequested(project)) {
            throw new GradleException(
                "io.root.patcher is not compatible with the Gradle configuration cache " +
                "(performs network I/O during dependency resolution).");
        }

        RootIoExtension ext = project.getExtensions().create("rootio", RootIoExtension.class);
        ext.getApiUrl().convention(envOrDefault("ROOTIO_API_URL", "https://api.root.io"));
        ext.getPkgUrl().convention(envOrDefault("ROOTIO_PKG_URL", "https://pkg.root.io"));
        ext.getTtlHours().convention(24L);
        // apiKey has no hardcoded default — it must come from env or build script
        String envApiKey = System.getenv("ROOTIO_API_KEY");
        if (envApiKey != null && !envApiKey.isEmpty()) {
            ext.getApiKey().convention(envApiKey);
        }

        Logger logger = project.getLogger();

        // Auto-register the Root.io patches Maven repository so patched artifacts resolve
        // without users needing to add it manually. Done in afterEvaluate so apiKey/pkgUrl
        // are fully configured by the time we read them.
        project.afterEvaluate(p -> {
            String pkgBase = ext.getPkgUrl().get().replaceAll("/$", "");
            String key = ext.getApiKey().getOrElse("(not set)");
            String masked = key.length() > 8 ? key.substring(0, 4) + "..." + key.substring(key.length() - 4) : "(too short or not set)";
            logger.info(LOG_PREFIX + "Registering repo: {}/maven (apiKey: {})", pkgBase, masked);
            p.getRepositories().maven(repo -> {
                repo.setName("Root.io patches");
                repo.setUrl(pkgBase + "/maven");
                // Credentials only apply to HTTP(S) — file:// repos (e.g. in tests) reject them.
                // BasicAuthentication forces preemptive auth so credentials are sent on the
                // first request. Without it, Gradle waits for a 401 challenge, but artrepo
                // returns 403 directly for unauthenticated requests.
                if (pkgBase.startsWith("http://") || pkgBase.startsWith("https://")) {
                    repo.credentials(creds -> {
                        creds.setUsername("token");
                        creds.setPassword(ext.getApiKey().get());
                    });
                    repo.authentication(auth -> auth.create("basic", BasicAuthentication.class));
                }
            });
        });

        project.getConfigurations().all(config -> {
            // Only hook resolvable configurations — non-resolvable ones (e.g. `api`, `implementation`)
            // are for declaring dependencies and cannot have eachDependency applied safely.
            if (!config.isCanBeResolved()) return;

            config.getResolutionStrategy().eachDependency(details -> {
                ModuleVersionSelector req = details.getRequested();
                String version = req.getVersion();

                // Skip deps with no version — these are BOM/platform-managed or Kotlin-plugin-managed
                // deps whose version is resolved separately. Sending an empty version to the API
                // produces a 400.
                if (version == null || version.isEmpty()) {
                    logger.info(LOG_PREFIX + "Skipping {}:{} (no version)", req.getGroup(), req.getName());
                    return;
                }

                String coords = req.getGroup() + ":" + req.getName() + ":" + version;

                long[] queryMs = {-1};
                String patched = DepCache.lookup(
                    coords,
                    project.getRootDir(), // always rootDir so subprojects share the cache
                    ext.getTtlHours().get(),
                    () -> {
                        long start = System.currentTimeMillis();
                        String result = RootIoClient.query(coords, ext.getApiUrl().get(), ext.getApiKey().get());
                        queryMs[0] = System.currentTimeMillis() - start;
                        return result;
                    }
                );

                if (patched != null) {
                    // Split "group:artifact:version" for useTarget map
                    int firstColon = patched.indexOf(':');
                    int lastColon  = patched.lastIndexOf(':');
                    String pGroup   = patched.substring(0, firstColon);
                    String pName    = patched.substring(firstColon + 1, lastColon);
                    String pVersion = patched.substring(lastColon + 1);
                    details.useTarget(Map.of("group", pGroup, "name", pName, "version", pVersion));
                    details.because("Root.io security patch");
                    logger.info(LOG_PREFIX + "Patching {} -> {}", coords, patched);
                    logger.debug(LOG_PREFIX + "Patch ({}) discovery took {}ms", patched, queryMs[0]);
                } else {
                    logger.info(LOG_PREFIX + "No patch for {}", coords);
                }
            });
        });
    }

    /**
     * Returns true if the configuration cache was requested for this build.
     * Uses the BuildFeatures API (Gradle 8.5+) when available; falls back to the
     * deprecated StartParameter method on older Gradle versions (7.6–8.4).
     * BuildFeatures cannot be imported directly here because the class doesn't exist
     * on Gradle < 8.5, so it is accessed reflectively to avoid NoClassDefFoundError.
     */
    @SuppressWarnings("deprecation")
    private static boolean isConfigurationCacheRequested(Project project) {
        if (GradleVersion.current().compareTo(GradleVersion.version("8.5")) >= 0) {
            try {
                Class<?> buildFeaturesClass = Class.forName("org.gradle.api.configuration.BuildFeatures");
                // Project's runtime type is ProjectInternal, which exposes getServices().
                Object services = project.getClass().getMethod("getServices").invoke(project);
                Object buildFeatures = services.getClass()
                    .getMethod("get", Class.class).invoke(services, buildFeaturesClass);
                Object ccFlags = buildFeatures.getClass()
                    .getMethod("getConfigurationCache").invoke(buildFeatures);
                Object requested = ccFlags.getClass().getMethod("getRequested").invoke(ccFlags);
                Object result = requested.getClass()
                    .getMethod("getOrElse", Object.class).invoke(requested, false);
                return Boolean.TRUE.equals(result);
            } catch (Exception e) {
                // Reflection failed unexpectedly — treat as not requested rather than crashing.
                return false;
            }
        }
        return project.getGradle().getStartParameter().isConfigurationCacheRequested();
    }

    private static String envOrDefault(String name, String defaultValue) {
        String val = System.getenv(name);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }
}
