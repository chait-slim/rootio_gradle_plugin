# Root.io Gradle Plugin

Automatically patches vulnerable Java dependencies with secure versions from the [Root.io](https://root.io) registry — no changes to your dependency declarations required.

## What It Does

The Root.io Gradle plugin silently upgrades vulnerable dependencies to patched versions at build time. It requires no changes to your `dependencies {}` block: you keep declaring the versions you know, and the plugin transparently swaps in secure replacements wherever Root.io has a patch available.

### How It Works

When a Gradle build resolves dependencies, the plugin:

1. **Intercepts dependency resolution** for every resolvable configuration in your project
2. **Queries the Root.io API** to check whether a patched version of each dependency exists
3. **Substitutes vulnerable coordinates** with patched ones using Gradle's `ResolutionStrategy.eachDependency` mechanism
4. **Registers the Root.io Maven registry** (`https://pkg.root.io/maven`) as a repository so patched artifacts resolve automatically

API responses are cached locally under `.gradle/rootio-cache/` (SHA-1-keyed JSON files) to avoid repeated network calls. The cache TTL defaults to 24 hours and is configurable.

### Example

You declare:

```kotlin
implementation("io.netty:netty-handler:4.1.118.Final")
```

If Root.io has a patch for that version, Gradle resolves a secure drop-in replacement instead — at the same coordinates but sourced from the Root.io registry — without any change to your build file.

## Requirements

- Gradle 7.0 or later
- JDK 11 or later
- A Root.io API key ([sign up at root.io](https://root.io))

## Installation

### Option A: Root.io Maven Repository

The plugin is published to Root.io's Maven repository. Add the repository to your `settings.gradle.kts` and apply the plugin:

**`settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        maven {
            url = uri("https://pkg.root.io/gradle-plugins")
            credentials {
                username = "token"
                password = providers.environmentVariable("ROOTIO_API_KEY").get()
            }
        }
        gradlePluginPortal()
    }
}
```

**`build.gradle.kts`**

```kotlin
plugins {
    id("io.root.patcher") version "0.1.0"
}
```

### Option B: Build Locally (mavenLocal)

Clone the repository and publish to your local Maven cache:

```bash
git clone https://github.com/rootio-avr/rootio_gradle_plugin.git
cd rootio_gradle_plugin
make publish-local
```

Then reference it from your project by adding `mavenLocal()` to `pluginManagement` repositories:

**`settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```

**`build.gradle.kts`**

```kotlin
plugins {
    id("io.root.patcher") version "0.1.0"
}
```

### Multi-project Builds

Apply the plugin once in the root `build.gradle.kts`:

```kotlin
plugins {
    id("io.root.patcher") version "0.1.0" apply false
}

subprojects {
    apply(plugin = "io.root.patcher")
}
```

No further configuration is required once your API key is available (see below).

## API Key

The plugin resolves your Root.io API key automatically. Sources are checked in this order — the highest-priority source that provides a non-empty value wins:

| Priority | Source | How to set |
|----------|--------|------------|
| 4 — highest | Build script | `rootio { apiKey.set("your-key") }` |
| 3 | Environment variable | `export ROOTIO_API_KEY=your-key` |
| 2 | JVM system property | `systemProp.ROOTIO_API_KEY=your-key` in `~/.gradle/gradle.properties` |
| 1 — lowest | `.env` file | `ROOTIO_API_KEY=your-key` in project root `.env` |

### Option 1: Environment Variable

```bash
export ROOTIO_API_KEY=your-api-key
```

Good for CI/CD pipelines where the key is injected as a secret.

### Option 2: Global Gradle Properties (recommended for local development)

Add to `~/.gradle/gradle.properties` — this file lives in your home directory and is never part of any project:

```properties
systemProp.ROOTIO_API_KEY=your-api-key
```

The `systemProp.` prefix is required. Gradle exposes entries with this prefix as JVM system properties, which is how the plugin reads them. Setting `ROOTIO_API_KEY=your-api-key` without the prefix will be silently ignored.

This works for every project on your machine without any per-project setup.

### Option 3: `.env` File

Create a `.env` file in the project root:

```
ROOTIO_API_KEY=your-api-key
```

Add `.env` to your `.gitignore` to keep the key out of version control. A `.env.example` template is included in this repository.

### Option 4: Build Script (explicit override)

```kotlin
rootio {
    apiKey.set("your-api-key")
}
```

Use this when the key is already managed outside of source control (e.g. injected by a secrets manager at build time) and you need to pass it explicitly.

## Configuration

All settings are optional beyond the API key. Configure them inside the `rootio {}` block:

| Property           | Default               | Description                                                                                                                    |
|--------------------|-----------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `apiKey`           | —                     | Your Root.io API key                                                                                                           |
| `apiUrl`           | `https://api.root.io` | Root.io API base URL                                                                                                           |
| `pkgUrl`           | `https://pkg.root.io` | Root.io package registry base URL                                                                                              |
| `ttlHours`         | `24`                  | Hours to cache API responses locally. Set to `0` to disable caching.                                                           |
| `maxRetries`       | `3`                   | Max retry attempts on transient failures (5xx, network errors). Set to `0` to disable retries.                                 |
| `retryBaseDelayMs` | `1000`                | Base delay in milliseconds for exponential backoff between retries. Delay doubles on each attempt (1000ms, 2000ms, 4000ms, …). |

Example — extend the cache TTL and adjust retry behavior:

```kotlin
rootio {
    ttlHours.set(48)
    maxRetries.set(5)
    retryBaseDelayMs.set(500)
}
```

## Local Development

### Prerequisites

- JDK 11 or later

### Running Tests

```bash
make test
```

### Publishing to the Local Maven Repository

To test the plugin in another local project before publishing:

```bash
make publish-local
```

Then reference it from your local project by adding `mavenLocal()` to your `pluginManagement` repositories as shown in [Option B](#option-b-build-locally-mavenlocal) above.

## Examples

The repository includes two example projects under `example/` and `example-multi-projects/` that demonstrate single-project and multi-project setups respectively. Both use `mavenLocal()` and expect the plugin to be published locally first (`make publish-local`).

## Contributing

Contributions are welcome. Please open an issue to discuss a proposed change before submitting a pull request.

1. Fork the repository
2. Create a feature branch (`git checkout -b my-feature`)
3. Make your changes and add tests
4. Run the test suite (`make test`) and ensure everything passes
5. Open a pull request

## License

This project is licensed under the [Apache License 2.0](LICENSE).
