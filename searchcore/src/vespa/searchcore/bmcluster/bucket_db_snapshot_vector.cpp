// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_db_snapshot_vector.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <cassert>

using document::BucketSpace;
using document::FixedBucketSpaces;
using storage::lib::ClusterStateBundle;
using storage::lib::Node;
using storage::lib::NodeType;

namespace search::bmcluster {

namespace {

std::vector<BucketSpace> bucket_spaces = { FixedBucketSpaces::default_space(), FixedBucketSpaces::global_space() };

}


BucketDbSnapshotVector::BucketDbSnapshotVector(const std::vector<storage::spi::PersistenceProvider *>& providers, const ClusterStateBundle &cluster_state_bundle)
    : _snapshots()
{
    for (const auto bucket_space : bucket_spaces) {
        auto &bs_snapshots = _snapshots[bucket_space];
        bs_snapshots.resize(providers.size());
        auto cluster_state = *cluster_state_bundle.getDerivedClusterState(bucket_space);
        uint32_t node_idx = 0;
        for (const auto &provider : providers) {
            auto node_state = cluster_state.getNodeState(Node(NodeType::STORAGE, node_idx));
            if (provider && node_state.getState().oneOf("ur")) {
                bs_snapshots[node_idx].populate(bucket_space, *provider);
            }
            ++node_idx;
        }
    }
}

BucketDbSnapshotVector::~BucketDbSnapshotVector() = default;

uint32_t
BucketDbSnapshotVector::count_moved_documents(const BucketDbSnapshotVector &old) const
{
    uint32_t moved_documents = 0;
    for (const auto bucket_space : bucket_spaces) {
        auto& bs_snapshots = _snapshots.find(bucket_space)->second;
        auto& old_bs_snapshots = old._snapshots.find(bucket_space)->second;
        assert(bs_snapshots.size() == old_bs_snapshots.size());
        for (uint32_t node_idx = 0; node_idx < bs_snapshots.size(); ++node_idx) {
            moved_documents += bs_snapshots[node_idx].count_new_documents(old_bs_snapshots[node_idx]);
        }
    }
    return moved_documents;
}

uint32_t
BucketDbSnapshotVector::count_lost_unique_documents(const BucketDbSnapshotVector &old) const
{
    uint32_t lost_documents = 0;
    for (const auto bucket_space : bucket_spaces) {
        auto& bs_snapshots = _snapshots.find(bucket_space)->second;
        auto& old_bs_snapshots = old._snapshots.find(bucket_space)->second;
        BucketIdSet old_buckets;
        BucketIdSet new_buckets;
        for (auto &snapshot : old_bs_snapshots) {
            snapshot.populate_bucket_id_set(old_buckets);
        }
        for (auto &snapshot : bs_snapshots) {
            snapshot.populate_bucket_id_set(new_buckets);
        }
        for (auto &old_bucket : old_buckets) {
            if (new_buckets.find(old_bucket) != new_buckets.end()) {
                continue;
            }
            for (auto &snapshot : old_bs_snapshots) {
                auto info = snapshot.try_get_bucket_info(old_bucket);
                if (info != nullptr) {
                    lost_documents += info->getDocumentCount();
                    break;
                }
            }
        }
    }
    return lost_documents;
}

}
