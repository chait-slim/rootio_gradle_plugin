 # Root.io Gradle Plugin

Automatically patches vulnerable Java dependencies with secure versions from the [Root.io](https://root.io) registry — no changes to your dependency declarations required.

When you run a Gradle build, the plugin:
1. Intercepts dependency resolution for every resolvable configuration
2. Queries the Root.io API to check whether a patched version exists
3. Transparently substitutes vulnerable coordinates with patched ones
4. Registers the Root.io package registry as a Maven repository so patched artifacts resolve

## Local Development

### Prerequisites

- JDK 11 or later

### Running tests

```bash
make test
```

### Publishing to the local Maven repository

To test the plugin in another local project before publishing:

```bash
make publish-local
```

Then reference it from your local project by adding `mavenLocal()` to your `pluginManagement` repositories.

## Installation

Add the plugin to your `build.gradle.kts`:

```kotlin
plugins {
    id("io.root.patcher") version "0.1.0"
}
```

For multi-project builds, apply it once in the root `build.gradle.kts`:

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

### Option 1: Environment variable

```bash
export ROOTIO_API_KEY=your-api-key
```

Good for CI/CD pipelines where the key is injected as a secret.

### Option 2: Global Gradle properties (recommended for local development)

Add to `~/.gradle/gradle.properties` — this file lives in your home directory and is never part of any project:

```properties
systemProp.ROOTIO_API_KEY=your-api-key
```

The `systemProp.` prefix is required. Gradle exposes entries with this prefix as JVM system properties, which is how the plugin reads them. Setting `ROOTIO_API_KEY=your-api-key` without the prefix will be silently ignored.

This works for every project on your machine without any per-project setup.

### Option 3: `.env` file

Create a `.env` file in the project root:

```
ROOTIO_API_KEY=your-api-key
```

Add `.env` to your `.gitignore` to keep the key out of version control. A `.env.example` template is included in this repository.

### Option 4: Build script (explicit override)

```kotlin
rootio {
    apiKey.set("your-api-key")
}
```

Use this when the key is already managed outside of source control (e.g. injected by a secrets manager at build time) and you need to pass it explicitly.

## Configuration

All settings are optional beyond the API key. Configure them inside the `rootio {}` block:

| Property | Default | Description |
|----------|---------|-------------|
| `apiKey` | — | Your Root.io API key |
| `apiUrl` | `https://api.root.io` | Root.io API base URL |
| `pkgUrl` | `https://pkg.root.io` | Root.io package registry base URL |
| `ttlHours` | `24` | Hours to cache API responses locally. Set to `0` to disable caching. |

Example — extend the cache TTL:

```kotlin
rootio {
    ttlHours.set(48)
}
```
