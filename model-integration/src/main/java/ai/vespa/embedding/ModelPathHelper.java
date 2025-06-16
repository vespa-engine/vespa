package ai.vespa.embedding;

import com.yahoo.config.ModelReference;

import java.nio.file.Path;

public interface ModelPathHelper {

    Path getModelPathResolvingIfNecessary(ModelReference modelReference);
}
