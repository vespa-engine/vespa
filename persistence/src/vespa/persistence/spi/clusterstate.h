// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucket.h"


namespace storage::lib {
    class ClusterState;
    class Distribution;
}

namespace storage::spi {

/**
 * Used to determine the state of the current node and its buckets.
 */
class ClusterState {
public:
    typedef std::shared_ptr<ClusterState> SP;

    ClusterState(const lib::ClusterState& state,
                 uint16_t nodeIndex,
                 const lib::Distribution& distribution);

    ClusterState(vespalib::nbostream& i);

    ClusterState(const ClusterState& other);
    ClusterState& operator=(const ClusterState& other);
    ~ClusterState();

    /**
     * Returns true if the system has been set up to have
     * "ready" nodes, and the given bucket is in the ideal state
     * for readiness.
     *
     * @param b The bucket to check.
     */
    bool shouldBeReady(const Bucket& b) const;

    /**
     * Returns false if the cluster has been deemed down. This can happen
     * if the fleet controller has detected that too many nodes are down
     * compared to the complete list of nodes, and deigns the system to be
     * unusable.
     */
    bool clusterUp() const;

    /**
     * Returns false if this node has been set in a state where it should not
     * receive external load.
     */
    bool nodeUp() const;

    /**
     * Returns true iff this node is marked as Initializing in the cluster state.
     */
    bool nodeInitializing() const;

    /**
     * Returns true iff this node is marked as Retired in the cluster state.
     */
    bool nodeRetired() const;

    /**
     * Returns a serialized form of this object.
     */
    void serialize(vespalib::nbostream& o) const;

private:
    std::unique_ptr<lib::ClusterState> _state;
    uint16_t _nodeIndex;
    std::unique_ptr<lib::Distribution> _distribution;

    void deserialize(vespalib::nbostream&);
    bool nodeHasStateOneOf(const char* states) const;
};

}
