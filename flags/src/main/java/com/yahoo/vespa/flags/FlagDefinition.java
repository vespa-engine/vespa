// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import javax.annotation.concurrent.Immutable;
import java.util.Collections;
import java.util.List;

/**
 * @author hakonhall
 */
@Immutable
public class FlagDefinition<T> {
    private final UnboundFlag<T> unboundFlag;
    private final String description;
    private final String modificationEffect;
    private final List<FetchVector.Dimension> dimensions;

    public FlagDefinition(UnboundFlag<T> unboundFlag, String description, String modificationEffect,
                          List<FetchVector.Dimension> dimensions) {
        this.unboundFlag = unboundFlag;
        this.description = description;
        this.modificationEffect = modificationEffect;
        this.dimensions = Collections.unmodifiableList(dimensions);
    }

    public UnboundFlag<T> getUnboundFlag() {
        return unboundFlag;
    }

    public List<FetchVector.Dimension> getDimensions() {
        return dimensions;
    }

    public String getDescription() {
        return description;
    }

    public String getModificationEffect() {
        return modificationEffect;
    }
}
