package io.root.patcher;

import org.gradle.api.provider.Property;

public abstract class RootIoExtension {
    /** Root.io API key (required). Set via rootio { apiKey.set(...) }. */
    public abstract Property<String> getApiKey();

    /** Root.io API base URL. Default: <a href="https://api.root.io">...</a> */
    public abstract Property<String> getApiUrl();

    /** Cache TTL in hours. Default: 24. Set to 0 for no caching. */
    public abstract Property<Long> getTtlHours();

    /** Max number of retry attempts on transient failures. Default: 3. Set to 0 to disable retries. */
    public abstract Property<Integer> getMaxRetries();

    /** Base delay in milliseconds for exponential backoff between retries. Default: 1000. */
    public abstract Property<Long> getRetryBaseDelayMs();

    /** Root.io package registry base URL. Default: <a href="https://pkg.root.io">...</a> */
    public abstract Property<String> getPkgUrl();

    /** Allow plain HTTP for the pkg repository. Default: false. Enable only for local/test setups. */
    public abstract Property<Boolean> getAllowInsecurePkgRepo();
}
