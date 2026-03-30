package io.root.patcher;

import org.gradle.api.provider.Property;

public abstract class RootIoExtension {
    public static final String LOG_PREFIX = "[Root.io] ";

    /** Root.io API key (required). Set via rootio { apiKey.set(...) }. */
    public abstract Property<String> getApiKey();

    /** Root.io API base URL. Default: <a href="https://api.root.io">...</a> */
    public abstract Property<String> getApiUrl();

    /** Cache TTL in hours. Default: 24. Set to 0 for no caching. */
    public abstract Property<Long> getTtlHours();

    /** Root.io package registry base URL. Default: <a href="https://pkg.root.io">...</a> */
    public abstract Property<String> getPkgUrl();
}
