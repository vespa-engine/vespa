// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.path.Path;
import com.yahoo.vespa.config.GenerationCounter;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.recipes.CuratorCounter;

/**
 * Distributed global generation counter for the super model.
 *
 * @author Ulf Lilleengen
 */
public class SuperModelGenerationCounter implements GenerationCounter {

    private static final Path counterPath = Path.fromString("/config/v2/RPC/superModelGeneration");
    private final CuratorCounter counter;

    public SuperModelGenerationCounter(Curator curator) {
        this.counter = new CuratorCounter(curator, counterPath);
    }

    /**
     * Increment counter and return next value. This method is thread safe and provides an atomic value
     * across zookeeper clusters.
     *
     * @return incremented counter value.
     */
    public synchronized long increment() {
        return counter.next();
    }

    /**
     * @return current counter value.
     */
    public synchronized long get() {
        return counter.get();
    }

}
