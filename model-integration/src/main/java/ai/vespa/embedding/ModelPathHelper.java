// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.embedding;

import com.yahoo.config.ModelReference;

import java.nio.file.Path;

public interface ModelPathHelper {

    Path getModelPathResolvingIfNecessary(ModelReference modelReference);
}
