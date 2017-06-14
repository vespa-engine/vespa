// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/bucket.h>
#include <vespa/persistence/spi/clusterstate.h>

namespace storage {

namespace spi {

/**
 * Used to determine the state of the current node and its buckets.
 */
class ClusterStateImpl : public ClusterState{
public:
    ClusterStateImpl();

    ClusterStateImpl(const lib::ClusterState& state,
                     uint16_t nodeIndex,
                     const lib::Distribution& distribution);

    ClusterStateImpl(vespalib::nbostream& i);

    ClusterStateImpl(const ClusterStateImpl& other);

    ClusterStateImpl& operator=(const ClusterStateImpl& other);

    /**
     * Returns true if the given bucket is in the ideal state
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
     * Returns a serialized form of this object.
     */
    void serialize(vespalib::nbostream& o) const;

private:
    std::unique_ptr<lib::ClusterState> _state;
    uint16_t _nodeIndex;
    std::unique_ptr<lib::Distribution> _distribution;

    void deserialize(vespalib::nbostream&);
};

}

}

