// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.vespa.config.GenerationCounter;

/**
 * @author Ulf Lilleengen
 */
public class MemoryGenerationCounter implements GenerationCounter {
    private long value;

    @Override
    public long increment() {
        return ++value;
    }

    @Override
    public long get() {
        return value;
    }
}
