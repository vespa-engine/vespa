package ai.vespa.embedding.support;

import com.yahoo.config.ModelReference;

public interface ModelPathHelper {

    String getModelPathResolvingIfNecessary(ModelReference modelReference);
}
