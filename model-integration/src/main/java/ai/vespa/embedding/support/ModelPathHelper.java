package ai.vespa.embedding.support;

import com.yahoo.config.ModelReference;

import java.nio.file.Path;

public interface ModelPathHelper {

    Path getModelPathResolvingIfNecessary(ModelReference modelReference);
}
