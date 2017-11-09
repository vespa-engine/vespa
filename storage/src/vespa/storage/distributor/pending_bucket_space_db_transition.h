// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "pending_bucket_space_db_transition_entry.h"
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <unordered_set>

namespace storage::api { class RequestBucketInfoReply; }
namespace storage::lib { class ClusterState; }

namespace storage::distributor {

class ClusterInformation;
class PendingClusterState;

/**
 * Class used by PendingClusterState to track request bucket info
 * reply result within a bucket space and apply it to the distributor
 * bucket database when switching to the pending cluster state.
 */
class PendingBucketSpaceDbTransition : public BucketDatabase::MutableEntryProcessor
{
public:
    using Entry = dbtransition::Entry;
    using EntryList = std::vector<Entry>;
private:
    using Range = std::pair<uint32_t, uint32_t>;

    EntryList                                 _entries;
    uint32_t                                  _iter;
    std::vector<document::BucketId>           _removedBuckets;
    std::vector<Range>                        _missingEntries;
    std::shared_ptr<const ClusterInformation> _clusterInfo;

    // Set for all nodes that may have changed state since that previous
    // active cluster state, or that were marked as outdated when the pending
    // cluster state was constructed.
    // May be a superset of _requestedNodes, as some nodes that are outdated
    // may be down and thus cannot get a request.
    const std::unordered_set<uint16_t>        _outdatedNodes;

    const lib::ClusterState                  &_newClusterState;
    const api::Timestamp                      _creationTimestamp;
    const PendingClusterState                &_pendingClusterState;

    // BucketDataBase::MutableEntryProcessor API
    bool process(BucketDatabase::Entry& e) override;

    /**
     * Skips through all entries for the same bucket and returns
     * the range in the entry list for which they were found.
     * The range is [from, to>
     */
    Range skipAllForSameBucket();

    std::vector<BucketCopy> getCopiesThatAreNewOrAltered(BucketDatabase::Entry& info, const Range& range);
    void insertInfo(BucketDatabase::Entry& info, const Range& range);
    void addToBucketDB(BucketDatabase& db, const Range& range);

    bool nodeIsOutdated(uint16_t node) const {
        return (_outdatedNodes.find(node) != _outdatedNodes.end());
    }

    // Returns whether at least one replica was removed from the entry.
    // Does NOT implicitly update trusted status on remaining replicas; caller must do
    // this explicitly.
    bool removeCopiesFromNodesThatWereRequested(BucketDatabase::Entry& e, const document::BucketId& bucketId);

    // Helper methods for iterating over _entries
    bool databaseIteratorHasPassedBucketInfoIterator(const document::BucketId& bucketId) const;
    bool bucketInfoIteratorPointsToBucket(const document::BucketId& bucketId) const;
    std::string requestNodesToString();

public:
    PendingBucketSpaceDbTransition(const PendingClusterState &pendingClusterState,
                                   std::shared_ptr<const ClusterInformation> clusterInfo,
                                   const lib::ClusterState &newClusterState,
                                   api::Timestamp creationTimestamp);
    ~PendingBucketSpaceDbTransition();

    // Merges all the results with the given bucket database.
    void mergeInto(BucketDatabase& db);

    // Adds the info from the reply to our list of information.
    void onRequestBucketInfoReply(const api::RequestBucketInfoReply &reply, uint16_t node);

    // Methods used by unit tests.
    const EntryList& results() const { return _entries; }
    void addNodeInfo(const document::BucketId& id, const BucketCopy& copy);
};

}
