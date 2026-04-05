package io.root.patcher;

import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

import javax.annotation.Nullable;
import java.io.File;

public abstract class RootIoValueSource implements ValueSource<String, RootIoValueSource.Parameters> {

    private static final RootIoClient CLIENT = new RootIoClient();

    public interface Parameters extends ValueSourceParameters {
        Property<String> getCoords();
        Property<String> getApiUrl();
        Property<String> getApiKey();
        Property<String> getRootDirPath();
        Property<Long>   getTtlHours();
    }

    @Override
    @Nullable
    public String obtain() {
        Parameters p = getParameters();
        return DepCache.lookup(
            p.getCoords().get(),
            new File(p.getRootDirPath().get()),
            p.getTtlHours().get(),
            () -> CLIENT.query(
                p.getCoords().get(),
                p.getApiUrl().get(),
                p.getApiKey().get()
            )
        );
    }
}
