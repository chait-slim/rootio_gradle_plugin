package io.root.patcher;

import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

import javax.annotation.Nullable;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public abstract class RootIoValueSource implements ValueSource<String, RootIoValueSource.Parameters> {

    private static final AtomicReference<RootIoClient> clientRef = new AtomicReference<>();

    public interface Parameters extends ValueSourceParameters {
        Property<String> getCoords();
        Property<String> getApiUrl();
        Property<String> getApiKey();
        Property<String> getRootDirPath();
        Property<Long> getTtlHours();
        Property<Integer> getMaxRetries();
        Property<Long> getRetryBaseDelayMs();
    }

    @Override
    @Nullable
    public String obtain() {
        Parameters p = getParameters();
        RootIoClient client = clientRef.updateAndGet(existing ->
            existing != null ? existing : new RootIoClient(p.getMaxRetries().get(), p.getRetryBaseDelayMs().get()));
        return DepCache.lookup(
            p.getCoords().get(),
            new File(p.getRootDirPath().get()),
            p.getTtlHours().get(),
            () -> client.query(
                p.getCoords().get(),
                p.getApiUrl().get(),
                p.getApiKey().getOrNull()
            )
        );
    }
}
