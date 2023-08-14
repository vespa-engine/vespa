// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "pending_bucket_space_db_transition_entry.h"
#include "outdated_nodes.h"
#include <vespa/document/bucket/bucketspace.h>
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <unordered_map>

namespace storage::api { class RequestBucketInfoReply; }
namespace storage::lib {
class ClusterState;
class Distribution;
class State;
}

namespace storage::distributor {

class BucketSpaceState;
class ClusterInformation;
class PendingClusterState;
class StripeAccessGuard;

/**
 * Class used by PendingClusterState to track request bucket info
 * reply result within a bucket space and apply it to the distributor
 * bucket database when switching to the pending cluster state.
 */
class PendingBucketSpaceDbTransition {
public:
    using Entry = dbtransition::Entry;
    using EntryList = std::vector<Entry>;
    using OutdatedNodes = dbtransition::OutdatedNodes;
private:
    using Range = std::pair<uint32_t, uint32_t>;

    document::BucketSpace                     _bucket_space;
    EntryList                                 _entries;
    std::vector<document::BucketId>           _removedBuckets;
    std::vector<Range>                        _missingEntries;
    std::shared_ptr<const ClusterInformation> _clusterInfo;

    // Set for all nodes that may have changed state since that previous
    // active cluster state, or that were marked as outdated when the pending
    // cluster state was constructed.
    // May be a superset of _requestedNodes, as some nodes that are outdated
    // may be down and thus cannot get a request.
    OutdatedNodes                             _outdatedNodes;

    const lib::ClusterState&                  _prevClusterState;
    const lib::ClusterState&                  _newClusterState;
    const api::Timestamp                      _creationTimestamp;
    const BucketSpaceState&                   _bucket_space_state;
    uint16_t                                  _distributorIndex;
    bool                                      _bucketOwnershipTransfer;
    std::unordered_map<uint16_t, size_t>      _rejectedRequests;
    std::unordered_map<uint16_t, size_t>      _failed_requests; // Also includes rejections

    bool distributorChanged();
    static bool nodeWasUpButNowIsDown(const lib::State &old, const lib::State &nw);
    bool storageNodeUpInNewState(uint16_t node) const;
    bool nodeInSameGroupAsSelf(uint16_t index) const;
    bool nodeNeedsOwnershipTransferFromGroupDown(uint16_t nodeIndex, const lib::ClusterState& state) const;
    uint16_t newStateStorageNodeCount() const;
    bool storageNodeMayHaveLostData(uint16_t index);
    bool storageNodeChanged(uint16_t index);
    void markAllAvailableNodesAsRequiringRequest();
    void addAdditionalNodesToOutdatedSet(const OutdatedNodes &nodes);
    void updateSetOfNodesThatAreOutdated();

public:
    // Abstracts away the details of how an entry list gathered from content nodes
    // is actually diffed and merged into a database.
    class DbMerger : public BucketDatabase::MergingProcessor {
        api::Timestamp _creation_timestamp;
        const lib::Distribution& _distribution;
        const lib::ClusterState& _new_state;
        const char* _storage_up_states;
        const OutdatedNodes & _outdated_nodes; // TODO hash_set
        const std::vector<dbtransition::Entry>& _entries;
        uint32_t _iter;
    public:
        DbMerger(api::Timestamp creation_timestamp,
                 const lib::Distribution& distribution,
                 const lib::ClusterState& new_state,
                 const char* storage_up_states,
                 const OutdatedNodes & outdated_nodes,
                 const std::vector<dbtransition::Entry>& entries)
            : _creation_timestamp(creation_timestamp),
              _distribution(distribution),
              _new_state(new_state),
              _storage_up_states(storage_up_states),
              _outdated_nodes(outdated_nodes),
              _entries(entries),
              _iter(0)
        {}
        ~DbMerger() override = default;

        BucketDatabase::MergingProcessor::Result merge(BucketDatabase::Merger&) override;
        void insert_remaining_at_end(BucketDatabase::TrailingInserter&) override;

        /**
         * Skips through all entries for the same bucket and returns
         * the range in the entry list for which they were found.
         * The range is [from, to>
         */
        Range skipAllForSameBucket();

        std::vector<BucketCopy> getCopiesThatAreNewOrAltered(BucketDatabase::Entry& info, const Range& range);
        void insertInfo(BucketDatabase::Entry& info, const Range& range);
        void addToMerger(BucketDatabase::Merger& merger, const Range& range);
        void addToInserter(BucketDatabase::TrailingInserter& inserter, const Range& range);

        // Returns whether at least one replica was removed from the entry.
        // Does NOT implicitly update trusted status on remaining replicas; caller must do
        // this explicitly.
        bool removeCopiesFromNodesThatWereRequested(BucketDatabase::Entry& e, const document::BucketId& bucketId);

        // Helper methods for iterating over _entries
        bool databaseIteratorHasPassedBucketInfoIterator(uint64_t bucket_key) const;
        bool bucketInfoIteratorPointsToBucket(uint64_t bucket_key) const;

        bool nodeIsOutdated(uint16_t node) const {
            return (_outdated_nodes.find(node) != _outdated_nodes.end());
        }
    };

    PendingBucketSpaceDbTransition(document::BucketSpace bucket_space,
                                   const BucketSpaceState &bucket_space_state,
                                   bool distributionChanged,
                                   const OutdatedNodes &outdatedNodes,
                                   std::shared_ptr<const ClusterInformation> clusterInfo,
                                   const lib::ClusterState &newClusterState,
                                   api::Timestamp creationTimestamp);
    ~PendingBucketSpaceDbTransition();

    // Merges all the results with the corresponding bucket database.
    void merge_into_bucket_databases(StripeAccessGuard& guard);

    // Adds the info from the reply to our list of information.
    void onRequestBucketInfoReply(const api::RequestBucketInfoReply &reply, uint16_t node);

    const OutdatedNodes &getOutdatedNodes() { return _outdatedNodes; }
    bool getBucketOwnershipTransfer() const { return _bucketOwnershipTransfer; }

    // Methods used by unit tests.
    const EntryList& results() const { return _entries; }
    void addNodeInfo(const document::BucketId& id, const BucketCopy& copy);

    void incrementRequestRejections(uint16_t node) {
        _rejectedRequests[node]++;
    }
    size_t rejectedRequests(uint16_t node) const {
        auto iter = _rejectedRequests.find(node);
        return ((iter != _rejectedRequests.end()) ? iter->second : 0);
    }
    void increment_request_failures(uint16_t node) {
        _failed_requests[node]++;
    }
    [[nodiscard]] size_t request_failures(uint16_t node) const noexcept {
        auto iter = _failed_requests.find(node);
        return ((iter != _failed_requests.end()) ? iter->second : 0);
    }
};

}
