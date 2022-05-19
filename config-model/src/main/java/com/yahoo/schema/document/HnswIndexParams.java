// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import java.util.Optional;

/**
 * Configuration parameters for a hnsw index used together with a 1-dimensional indexed tensor for approximate nearest neighbor search.
 *
 * @author geirst
 */
public class HnswIndexParams {

    public static final int DEFAULT_MAX_LINKS_PER_NODE = 16;
    public static final int DEFAULT_NEIGHBORS_TO_EXPLORE_AT_INSERT = 200;

    private final Optional<Integer> maxLinksPerNode;
    private final Optional<Integer> neighborsToExploreAtInsert;
    private final Optional<Boolean> multiThreadedIndexing;

    public static class Builder {
        private Optional<Integer> maxLinksPerNode = Optional.empty();
        private Optional<Integer> neighborsToExploreAtInsert = Optional.empty();
        private Optional<Boolean> multiThreadedIndexing = Optional.empty();

        public void setMaxLinksPerNode(int value) {
            maxLinksPerNode = Optional.of(value);
        }
        public void setNeighborsToExploreAtInsert(int value) {
            neighborsToExploreAtInsert = Optional.of(value);
        }
        public void setMultiThreadedIndexing(boolean value) {
            multiThreadedIndexing = Optional.of(value);
        }
        public HnswIndexParams build() {
            return new HnswIndexParams(maxLinksPerNode, neighborsToExploreAtInsert, multiThreadedIndexing);
        }
    }

    public HnswIndexParams() {
        this.maxLinksPerNode = Optional.empty();
        this.neighborsToExploreAtInsert = Optional.empty();
        this.multiThreadedIndexing = Optional.empty();
    }

    public HnswIndexParams(Optional<Integer> maxLinksPerNode,
                           Optional<Integer> neighborsToExploreAtInsert,
                           Optional<Boolean> multiThreadedIndexing) {
        this.maxLinksPerNode = maxLinksPerNode;
        this.neighborsToExploreAtInsert = neighborsToExploreAtInsert;
        this.multiThreadedIndexing = multiThreadedIndexing;
    }

    /**
     * Creates a new instance where values from the given parameter instance are used where they are present,
     * otherwise we use values from this.
     */
    public HnswIndexParams overrideFrom(Optional<HnswIndexParams> other) {
        if (! other.isPresent()) return this;
        HnswIndexParams rhs = other.get();
        return new HnswIndexParams(rhs.maxLinksPerNode.or(() ->  maxLinksPerNode),
                rhs.neighborsToExploreAtInsert.or(() ->  neighborsToExploreAtInsert),
                rhs.multiThreadedIndexing.or(() -> multiThreadedIndexing));
    }

    public int maxLinksPerNode() {
        return maxLinksPerNode.orElse(DEFAULT_MAX_LINKS_PER_NODE);
    }

    public int neighborsToExploreAtInsert() {
        return neighborsToExploreAtInsert.orElse(DEFAULT_NEIGHBORS_TO_EXPLORE_AT_INSERT);
    }

    public boolean multiThreadedIndexing() {
        return multiThreadedIndexing.orElse(true);
    }
}
