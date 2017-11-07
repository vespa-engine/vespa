// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pending_bucket_space_db_transition.h"
#include "clusterinformation.h"
#include "pendingclusterstate.h"
#include <vespa/storage/common/bucketoperationlogger.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".pendingclusterstateprocessor");

namespace storage::distributor {

PendingBucketSpaceDbTransition::PendingBucketSpaceDbTransition(const PendingClusterState &pendingClusterState,
                                                               std::shared_ptr<const ClusterInformation> clusterInfo,
                                                               const lib::ClusterState &newClusterState,
                                                               api::Timestamp creationTimestamp)
    : _entries(),
      _iter(0),
      _removedBuckets(),
      _missingEntries(),
      _clusterInfo(std::move(clusterInfo)),
      _outdatedNodes(pendingClusterState.getOutdatedNodeSet()),
      _newClusterState(newClusterState),
      _creationTimestamp(creationTimestamp),
      _pendingClusterState(pendingClusterState)
{
}

PendingBucketSpaceDbTransition::~PendingBucketSpaceDbTransition()
{
}

PendingBucketSpaceDbTransition::Range
PendingBucketSpaceDbTransition::skipAllForSameBucket()
{
    Range r(_iter, _iter);

    for (document::BucketId& bid = _entries[_iter].bucketId;
         _iter < _entries.size() && _entries[_iter].bucketId == bid;
         ++_iter)
    {
    }

    r.second = _iter;
    return r;
}

std::vector<BucketCopy>
PendingBucketSpaceDbTransition::getCopiesThatAreNewOrAltered(BucketDatabase::Entry& info, const Range& range)
{
    std::vector<BucketCopy> copiesToAdd;
    for (uint32_t i = range.first; i < range.second; ++i) {
        const BucketCopy& candidate(_entries[i].copy);
        const BucketCopy* cp = info->getNode(candidate.getNode());

        if (!cp || !(cp->getBucketInfo() == candidate.getBucketInfo())) {
            copiesToAdd.push_back(candidate);
        }
    }
    return copiesToAdd;
}

void
PendingBucketSpaceDbTransition::insertInfo(BucketDatabase::Entry& info, const Range& range)
{
    std::vector<BucketCopy> copiesToAddOrUpdate(
            getCopiesThatAreNewOrAltered(info, range));

    std::vector<uint16_t> order(
            _clusterInfo->getIdealStorageNodesForState(
                    _newClusterState,
                    _entries[range.first].bucketId));
    info->addNodes(copiesToAddOrUpdate, order, TrustedUpdate::DEFER);

    LOG_BUCKET_OPERATION_NO_LOCK(
            _entries[range.first].bucketId,
            vespalib::make_string("insertInfo: %s",
                                        info.toString().c_str()));
}

std::string
PendingBucketSpaceDbTransition::requestNodesToString()
{
    return _pendingClusterState.requestNodesToString();
}

bool
PendingBucketSpaceDbTransition::removeCopiesFromNodesThatWereRequested(BucketDatabase::Entry& e, const document::BucketId& bucketId)
{
    bool updated = false;
    for (uint32_t i = 0; i < e->getNodeCount();) {
        auto& info(e->getNodeRef(i));
        const uint16_t entryNode(info.getNode());
        // Don't remove an entry if it's been updated in the time after the
        // bucket info requests were sent, as this would erase newer state.
        // Don't immediately update trusted state, as that could erroneously
        // mark a single remaining replica as trusted even though there might
        // be one or more additional replicas pending merge into the database.
        if (nodeIsOutdated(entryNode)
            && (info.getTimestamp() < _creationTimestamp)
            && e->removeNode(entryNode, TrustedUpdate::DEFER))
        {
            LOG(spam,
                "Removed bucket %s from node %d",
                bucketId.toString().c_str(),
                entryNode);
            updated = true;
            // After removing current node, getNodeRef(i) will point to the _next_ node, so don't increment `i`.
        } else {
            ++i;
        }
    }
    return updated;
}

bool
PendingBucketSpaceDbTransition::databaseIteratorHasPassedBucketInfoIterator(const document::BucketId& bucketId) const
{
    return (_iter < _entries.size()
            && _entries[_iter].bucketId.toKey() < bucketId.toKey());
}

bool
PendingBucketSpaceDbTransition::bucketInfoIteratorPointsToBucket(const document::BucketId& bucketId) const
{
    return _iter < _entries.size() && _entries[_iter].bucketId == bucketId;
}

bool
PendingBucketSpaceDbTransition::process(BucketDatabase::Entry& e)
{
    document::BucketId bucketId(e.getBucketId());

    LOG(spam,
        "Before merging info from nodes [%s], bucket %s had info %s",
        requestNodesToString().c_str(),
        bucketId.toString().c_str(),
        e.getBucketInfo().toString().c_str());

    while (databaseIteratorHasPassedBucketInfoIterator(bucketId)) {
        LOG(spam, "Found new bucket %s, adding",
            _entries[_iter].bucketId.toString().c_str());

        _missingEntries.push_back(skipAllForSameBucket());
    }

    bool updated(removeCopiesFromNodesThatWereRequested(e, bucketId));

    if (bucketInfoIteratorPointsToBucket(bucketId)) {
        LOG(spam, "Updating bucket %s",
            _entries[_iter].bucketId.toString().c_str());

        insertInfo(e, skipAllForSameBucket());
        updated = true;
    }

    if (updated) {
        // Remove bucket if we've previously removed all nodes from it
        if (e->getNodeCount() == 0) {
            _removedBuckets.push_back(bucketId);
        } else {
            e.getBucketInfo().updateTrusted();
        }
    }

    LOG(spam,
        "After merging info from nodes [%s], bucket %s had info %s",
        requestNodesToString().c_str(),
        bucketId.toString().c_str(),
        e.getBucketInfo().toString().c_str());

    return true;
}

void
PendingBucketSpaceDbTransition::addToBucketDB(BucketDatabase& db, const Range& range)
{
    LOG(spam, "Adding new bucket %s with %d copies",
        _entries[range.first].bucketId.toString().c_str(),
        range.second - range.first);

    BucketDatabase::Entry e(_entries[range.first].bucketId, BucketInfo());
    insertInfo(e, range);
    if (e->getLastGarbageCollectionTime() == 0) {
        e->setLastGarbageCollectionTime(
                framework::MicroSecTime(_creationTimestamp)
                    .getSeconds().getTime());
    }
    e.getBucketInfo().updateTrusted();
    db.update(e);
}

void
PendingBucketSpaceDbTransition::mergeInto(BucketDatabase& db)
{
    std::sort(_entries.begin(), _entries.end());

    db.forEach(*this);

    for (uint32_t i = 0; i < _removedBuckets.size(); ++i) {
        db.remove(_removedBuckets[i]);
    }
    _removedBuckets.clear();

    // All of the remaining were not already in the bucket database.
    while (_iter < _entries.size()) {
        _missingEntries.push_back(skipAllForSameBucket());
    }

    for (uint32_t i = 0; i < _missingEntries.size(); ++i) {
        addToBucketDB(db, _missingEntries[i]);
    }
}

void
PendingBucketSpaceDbTransition::onRequestBucketInfoReply(const api::RequestBucketInfoReply &reply, uint16_t node)
{
    for (const auto &entry : reply.getBucketInfo()) {
        _entries.emplace_back(entry._bucketId,
                              BucketCopy(_creationTimestamp,
                                         node,
                                         entry._info));
    }
}

void
PendingBucketSpaceDbTransition::addNodeInfo(const document::BucketId& id, const BucketCopy& copy)
{
    _entries.emplace_back(id, copy);
}

}
