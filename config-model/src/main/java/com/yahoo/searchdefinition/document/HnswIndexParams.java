// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import java.util.Locale;
import java.util.Optional;

/**
 * Configuration parameters for a hnsw index used together with a 1-dimensional indexed tensor for approximate nearest neighbor search.
 *
 * @author geirst
 */
public class HnswIndexParams {

    public static final int DEFAULT_MAX_LINKS_PER_NODE = 16;
    public static final int DEFAULT_NEIGHBORS_TO_EXPLORE_AT_INSERT = 200;
    public static final DistanceMetric DEFAULT_DISTANCE_METRIC = DistanceMetric.EUCLIDEAN;

    private final Optional<Integer> maxLinksPerNode;
    private final Optional<Integer> neighborsToExploreAtInsert;
    private final Optional<DistanceMetric> distanceMetric;

    public static enum DistanceMetric { EUCLIDEAN, ANGULAR, GEODEGREES }

    public static class Builder {
        private Optional<Integer> maxLinksPerNode = Optional.empty();
        private Optional<Integer> neighborsToExploreAtInsert = Optional.empty();
        private Optional<DistanceMetric> distanceMetric = Optional.empty();

        public void setMaxLinksPerNode(int value) {
            maxLinksPerNode = Optional.of(value);
        }
        public void setNeighborsToExploreAtInsert(int value) {
            neighborsToExploreAtInsert = Optional.of(value);
        }
        public void setDistanceMetric(String value) {
            String upper = value.toUpperCase(Locale.ENGLISH);
            DistanceMetric dm = DistanceMetric.valueOf(upper);
            distanceMetric = Optional.of(dm);
        }
        public HnswIndexParams build() {
            return new HnswIndexParams(maxLinksPerNode, neighborsToExploreAtInsert, distanceMetric);
        }
    }

    public HnswIndexParams() {
        this.maxLinksPerNode = Optional.empty();
        this.neighborsToExploreAtInsert = Optional.empty();
        this.distanceMetric = Optional.empty();
    }

    public HnswIndexParams(Optional<Integer> maxLinksPerNode,
                           Optional<Integer> neighborsToExploreAtInsert,
                           Optional<DistanceMetric> distanceMetric) {
        this.maxLinksPerNode = maxLinksPerNode;
        this.neighborsToExploreAtInsert = neighborsToExploreAtInsert;
        this.distanceMetric = distanceMetric;
    }

    /**
     * Creates a new instance where values from the given parameter instance are used where they are present,
     * otherwise we use values from this.
     */
    public HnswIndexParams overrideFrom(HnswIndexParams rhs) {
        return new HnswIndexParams(rhs.maxLinksPerNode.or(() ->  maxLinksPerNode),
                rhs.neighborsToExploreAtInsert.or(() ->  neighborsToExploreAtInsert),
                rhs.distanceMetric.or(() -> distanceMetric));
    }

    public int maxLinksPerNode() {
        return maxLinksPerNode.orElse(DEFAULT_MAX_LINKS_PER_NODE);
    }

    public int neighborsToExploreAtInsert() {
        return neighborsToExploreAtInsert.orElse(DEFAULT_NEIGHBORS_TO_EXPLORE_AT_INSERT);
    }

    public DistanceMetric distanceMetric() {
        return distanceMetric.orElse(DEFAULT_DISTANCE_METRIC);
    }
}
