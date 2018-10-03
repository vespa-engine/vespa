// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.vespa.config.GenerationCounter;

/**
 * @author Ulf Lilleengen
 * @since 5.
 */
public class MemoryGenerationCounter implements GenerationCounter {
    long value;
    @Override
    public long increment() {
        return ++value;
    }

    @Override
    public long get() {
        return value;
    }
}
