// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <vespa/vespalib/util/trinary.h>

namespace vespalib { class nbostream; }
namespace storage::lib {
    class ClusterState;
    class Distribution;
}

namespace storage::spi {

class Bucket;

/**
 * Used to determine the state of the current node and its buckets.
 */
class ClusterState {
public:
    typedef std::shared_ptr<ClusterState> SP;

    ClusterState(const lib::ClusterState& state,
                 uint16_t nodeIndex,
                 const lib::Distribution& distribution,
                 bool maintenanceInAllSpaces = false);

    ClusterState(const ClusterState& other);
    ClusterState& operator=(const ClusterState& other) = delete;
    ~ClusterState();

    /**
     * Returns Trinary::True if the system has been set up to have
     * "ready" nodes, and the given bucket is in the ideal state
     * for readiness. Trinary ::Undefined is returned in case the bucketId is invalid (too few used bits)
     *
     * @param b The bucket to check.
     */
    vespalib::Trinary shouldBeReady(const Bucket& b) const;

    /**
     * Returns false if the cluster has been deemed down. This can happen
     * if the fleet controller has detected that too many nodes are down
     * compared to the complete list of nodes, and deigns the system to be
     * unusable.
     */
    [[nodiscard]] bool clusterUp() const noexcept;

    /**
     * Returns false if this node has been set in a state where it should not
     * receive external load.
     *
     * TODO rename to indicate bucket space affinity.
     */
    [[nodiscard]] bool nodeUp() const noexcept;

    /**
     * Returns true iff this node is marked as Initializing in the cluster state.
     *
     * TODO remove, init no longer used internally.
     */
    [[nodiscard]] bool nodeInitializing() const noexcept;

    /**
     * Returns true iff this node is marked as Retired in the cluster state.
     */
    [[nodiscard]] bool nodeRetired() const noexcept;

    /**
     * Returns true iff this node is marked as Maintenance in all bucket space cluster states.
     */
    [[nodiscard]] bool nodeMaintenance() const noexcept;

    /**
     * Returns a serialized form of this object.
     */
    void serialize(vespalib::nbostream& o) const;

private:
    std::unique_ptr<lib::ClusterState> _state;
    std::unique_ptr<lib::Distribution> _distribution;
    uint16_t _nodeIndex;
    bool _maintenanceInAllSpaces;

    void deserialize(vespalib::nbostream&);
    bool nodeHasStateOneOf(const char* states) const noexcept;
};

}
