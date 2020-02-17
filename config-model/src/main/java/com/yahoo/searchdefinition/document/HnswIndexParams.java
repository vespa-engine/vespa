// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import java.util.OptionalInt;

/**
 * Configuration parameters for a hnsw index used together with a 1-dimensional indexed tensor for approximate nearest neighbor search.
 *
 * @author geirst
 */
public class HnswIndexParams {

    public static final int DEFAULT_MAX_LINKS_PER_NODE = 16;
    public static final int DEFAULT_NEIGHBORS_TO_EXPLORE_AT_INSERT = 200;

    private final OptionalInt maxLinksPerNode;
    private final OptionalInt neighborsToExploreAtInsert;

    public static class Builder {
        private OptionalInt maxLinksPerNode = OptionalInt.empty();
        private OptionalInt neighborsToExploreAtInsert = OptionalInt.empty();

        public void setMaxLinksPerNode(int value) {
            maxLinksPerNode = OptionalInt.of(value);
        }
        public void setNeighborsToExploreAtInsert(int value) {
            neighborsToExploreAtInsert = OptionalInt.of(value);
        }
        public HnswIndexParams build() {
            return new HnswIndexParams(maxLinksPerNode, neighborsToExploreAtInsert);
        }
    }

    public HnswIndexParams() {
        this.maxLinksPerNode = OptionalInt.empty();
        this.neighborsToExploreAtInsert = OptionalInt.empty();
    }

    public HnswIndexParams(OptionalInt maxLinksPerNode, OptionalInt neighborsToExploreAtInsert) {
        this.maxLinksPerNode = maxLinksPerNode;
        this.neighborsToExploreAtInsert = neighborsToExploreAtInsert;
    }

    /**
     * Creates a new instance where values from the given parameter instance are used where they are present,
     * otherwise we use values from this.
     */
    public HnswIndexParams overrideFrom(HnswIndexParams rhs) {
        return new HnswIndexParams(rhs.maxLinksPerNode.isPresent() ? rhs.maxLinksPerNode : maxLinksPerNode,
                rhs.neighborsToExploreAtInsert.isPresent() ? rhs.neighborsToExploreAtInsert : neighborsToExploreAtInsert);
    }

    public int maxLinksPerNode() {
        return maxLinksPerNode.orElse(DEFAULT_MAX_LINKS_PER_NODE);
    }

    public int neighborsToExploreAtInsert() {
        return neighborsToExploreAtInsert.orElse(DEFAULT_NEIGHBORS_TO_EXPLORE_AT_INSERT);
    }
}
