// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketspace.h>
#include <vespa/persistence/spi/clusterstate.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <cassert>
#include <unordered_map>

namespace storage::lib {
    class ClusterState;
    class Distribution;
}

namespace storage::distributor {

/**
 * Represents cluster state and distribution for a given bucket space.
 */
class BucketSpaceState {
private:
    std::shared_ptr<const lib::ClusterState> _cluster_state;
    std::shared_ptr<const lib::Distribution> _distribution;

public:
    explicit BucketSpaceState();

    BucketSpaceState(const BucketSpaceState&) = delete;
    BucketSpaceState& operator=(const BucketSpaceState&) = delete;
    BucketSpaceState(BucketSpaceState&&) = delete;
    BucketSpaceState& operator=(BucketSpaceState&&) = delete;

    void set_cluster_state(std::shared_ptr<const lib::ClusterState> cluster_state);
    void set_distribution(std::shared_ptr<const lib::Distribution> distribution);

    const lib::ClusterState& get_cluster_state() const noexcept {
        assert(_cluster_state);
        return *_cluster_state;
    }
    const lib::Distribution& get_distribution() const noexcept {
        assert(_distribution);
        return *_distribution;
    }
};

/**
 * Provides mapping from bucket space to state for that space.
 */
class BucketSpaceStateMap {
private:
    using StateMap = std::unordered_map<document::BucketSpace, std::unique_ptr<BucketSpaceState>, document::BucketSpace::hash>;

    StateMap _map;

public:
    explicit BucketSpaceStateMap();

    BucketSpaceStateMap(const BucketSpaceStateMap&&) = delete;
    BucketSpaceStateMap& operator=(const BucketSpaceStateMap&) = delete;
    BucketSpaceStateMap(BucketSpaceStateMap&&) = delete;
    BucketSpaceStateMap& operator=(BucketSpaceStateMap&&) = delete;

    StateMap::const_iterator begin() const { return _map.begin(); }
    StateMap::const_iterator end() const { return _map.end(); }

    const BucketSpaceState& get(document::BucketSpace space) const;
    BucketSpaceState& get(document::BucketSpace space);

    void set_cluster_state(std::shared_ptr<const lib::ClusterState> cluster_state);
    void set_distribution(std::shared_ptr<const lib::Distribution> distribution);

    const lib::ClusterState& get_cluster_state(document::BucketSpace space) const;
    const lib::Distribution& get_distribution(document::BucketSpace space) const;
};

}
