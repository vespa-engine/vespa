// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pending_bucket_space_db_transition.h"
#include "clusterinformation.h"
#include "pendingclusterstate.h"
#include "distributor_bucket_space.h"
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".pendingbucketspacedbtransition");

namespace storage::distributor {

using lib::Node;
using lib::NodeType;
using lib::NodeState;

PendingBucketSpaceDbTransition::PendingBucketSpaceDbTransition(const PendingClusterState &pendingClusterState,
                                                               DistributorBucketSpace &distributorBucketSpace,
                                                               bool distributionChanged,
                                                               const OutdatedNodes &outdatedNodes,
                                                               std::shared_ptr<const ClusterInformation> clusterInfo,
                                                               const lib::ClusterState &newClusterState,
                                                               api::Timestamp creationTimestamp)
    : _entries(),
      _iter(0),
      _removedBuckets(),
      _missingEntries(),
      _clusterInfo(std::move(clusterInfo)),
      _outdatedNodes(newClusterState.getNodeCount(NodeType::STORAGE)),
      _prevClusterState(distributorBucketSpace.getClusterState()),
      _newClusterState(newClusterState),
      _creationTimestamp(creationTimestamp),
      _pendingClusterState(pendingClusterState),
      _distributorBucketSpace(distributorBucketSpace),
      _distributorIndex(_clusterInfo->getDistributorIndex()),
      _bucketOwnershipTransfer(distributionChanged),
      _rejectedRequests()
{
    if (distributorChanged()) {
        _bucketOwnershipTransfer = true;
    }
    if (_bucketOwnershipTransfer) {
        markAllAvailableNodesAsRequiringRequest();
    } else {
        updateSetOfNodesThatAreOutdated();
        addAdditionalNodesToOutdatedSet(outdatedNodes);
    }
}

PendingBucketSpaceDbTransition::~PendingBucketSpaceDbTransition() = default;

PendingBucketSpaceDbTransition::Range
PendingBucketSpaceDbTransition::skipAllForSameBucket()
{
    Range r(_iter, _iter);

    for (uint64_t bkey = _entries[_iter].bucket_key;
         (_iter < _entries.size()) && (_entries[_iter].bucket_key == bkey);
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

    const auto &dist(_distributorBucketSpace.getDistribution());
    std::vector<uint16_t> order(
            dist.getIdealStorageNodes(
                    _newClusterState,
                    _entries[range.first].bucket_id(),
                    _clusterInfo->getStorageUpStates()));
    info->addNodes(copiesToAddOrUpdate, order, TrustedUpdate::DEFER);
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
PendingBucketSpaceDbTransition::databaseIteratorHasPassedBucketInfoIterator(uint64_t bucket_key) const
{
    return ((_iter < _entries.size())
            && (_entries[_iter].bucket_key < bucket_key));
}

bool
PendingBucketSpaceDbTransition::bucketInfoIteratorPointsToBucket(uint64_t bucket_key) const
{
    return _iter < _entries.size() && _entries[_iter].bucket_key == bucket_key;
}

using MergeResult = BucketDatabase::MergingProcessor::Result;

MergeResult PendingBucketSpaceDbTransition::merge(BucketDatabase::Merger& merger) {
    const uint64_t bucket_key = merger.bucket_key();

    while (databaseIteratorHasPassedBucketInfoIterator(bucket_key)) {
        LOG(spam, "Found new bucket %s, adding", _entries[_iter].bucket_id().toString().c_str());
        addToMerger(merger, skipAllForSameBucket());
    }

    auto& e = merger.current_entry();
    document::BucketId bucketId(e.getBucketId());

    LOG(spam,
        "Before merging info from nodes [%s], bucket %s had info %s",
        requestNodesToString().c_str(),
        bucketId.toString().c_str(),
        e.getBucketInfo().toString().c_str());

    bool updated(removeCopiesFromNodesThatWereRequested(e, bucketId));

    if (bucketInfoIteratorPointsToBucket(bucket_key)) {
        LOG(spam, "Updating bucket %s", _entries[_iter].bucket_id().toString().c_str());
        insertInfo(e, skipAllForSameBucket());
        updated = true;
    }

    if (updated) {
        // Remove bucket if we've previously removed all nodes from it
        if (e->getNodeCount() == 0) {
            return MergeResult::Skip;
        } else {
            e.getBucketInfo().updateTrusted();
            return MergeResult::Update;
        }
    }

    return MergeResult::KeepUnchanged;
}

void PendingBucketSpaceDbTransition::insert_remaining_at_end(BucketDatabase::TrailingInserter& inserter) {
    while (_iter < _entries.size()) {
        addToInserter(inserter, skipAllForSameBucket());
    }
}

void
PendingBucketSpaceDbTransition::addToMerger(BucketDatabase::Merger& merger, const Range& range)
{
    const auto bucket_id = _entries[range.first].bucket_id();
    LOG(spam, "Adding new bucket %s with %d copies",
        bucket_id.toString().c_str(),
        range.second - range.first);

    BucketDatabase::Entry e(bucket_id, BucketInfo());
    insertInfo(e, range);
    if (e->getLastGarbageCollectionTime() == 0) {
        e->setLastGarbageCollectionTime(
                framework::MicroSecTime(_creationTimestamp)
                        .getSeconds().getTime());
    }
    e.getBucketInfo().updateTrusted();
    merger.insert_before_current(bucket_id, e);
}

void
PendingBucketSpaceDbTransition::addToInserter(BucketDatabase::TrailingInserter& inserter, const Range& range)
{
    // TODO dedupe
    const auto bucket_id = _entries[range.first].bucket_id();
    LOG(spam, "Adding new bucket %s with %d copies",
        bucket_id.toString().c_str(),
        range.second - range.first);

    BucketDatabase::Entry e(bucket_id, BucketInfo());
    insertInfo(e, range);
    if (e->getLastGarbageCollectionTime() == 0) {
        e->setLastGarbageCollectionTime(
                framework::MicroSecTime(_creationTimestamp)
                        .getSeconds().getTime());
    }
    e.getBucketInfo().updateTrusted();
    inserter.insert_at_end(bucket_id, e);
}

void
PendingBucketSpaceDbTransition::mergeIntoBucketDatabase()
{
    BucketDatabase &db(_distributorBucketSpace.getBucketDatabase());
    std::sort(_entries.begin(), _entries.end());
    db.merge(*this);
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

bool
PendingBucketSpaceDbTransition::distributorChanged()
{
    const auto &oldState(_prevClusterState);
    const auto &newState(_newClusterState);
    if (newState.getDistributionBitCount() != oldState.getDistributionBitCount()) {
        return true;
    }

    Node myNode(NodeType::DISTRIBUTOR, _distributorIndex);
    if (oldState.getNodeState(myNode).getState() == lib::State::DOWN) {
        return true;
    }

    uint16_t oldCount = oldState.getNodeCount(NodeType::DISTRIBUTOR);
    uint16_t newCount = newState.getNodeCount(NodeType::DISTRIBUTOR);

    uint16_t maxCount = std::max(oldCount, newCount);

    for (uint16_t i = 0; i < maxCount; ++i) {
        Node node(NodeType::DISTRIBUTOR, i);

        const lib::State& old(oldState.getNodeState(node).getState());
        const lib::State& nw(newState.getNodeState(node).getState());

        if (nodeWasUpButNowIsDown(old, nw)) {
            if (nodeInSameGroupAsSelf(i) ||
                nodeNeedsOwnershipTransferFromGroupDown(i, newState)) {
                return true;
            }
        }
    }

    return false;
}

bool
PendingBucketSpaceDbTransition::nodeWasUpButNowIsDown(const lib::State& old,
                                                      const lib::State& nw)
{
    return (old.oneOf("uimr") && !nw.oneOf("uimr"));
}

bool
PendingBucketSpaceDbTransition::nodeInSameGroupAsSelf(uint16_t index) const
{
    const auto &dist(_distributorBucketSpace.getDistribution());
    if (dist.getNodeGraph().getGroupForNode(index) ==
        dist.getNodeGraph().getGroupForNode(_distributorIndex)) {
        LOG(debug,
            "Distributor %d state changed, need to request data from all "
            "storage nodes",
            index);
        return true;
    } else {
        LOG(debug,
            "Distributor %d state changed but unrelated to my group.",
            index);
        return false;
    }
}

bool
PendingBucketSpaceDbTransition::nodeNeedsOwnershipTransferFromGroupDown(
        uint16_t nodeIndex,
        const lib::ClusterState& state) const
{
    const auto &dist(_distributorBucketSpace.getDistribution());
    if (!dist.distributorAutoOwnershipTransferOnWholeGroupDown()) {
        return false; // Not doing anything for downed groups.
    }
    const lib::Group* group(dist.getNodeGraph().getGroupForNode(nodeIndex));
    // If there is no group information associated with the node (because the
    // group has changed or the node has been removed from config), we must
    // also invoke ownership transfer of buckets.
    if (group == nullptr
        || lib::Distribution::allDistributorsDown(*group, state))
    {
        LOG(debug,
            "Distributor %u state changed and is in a "
            "group that now has no distributors remaining",
            nodeIndex);
        return true;
    }
    return false;
}

uint16_t
PendingBucketSpaceDbTransition::newStateStorageNodeCount() const
{
    return _newClusterState.getNodeCount(lib::NodeType::STORAGE);
}

bool
PendingBucketSpaceDbTransition::storageNodeMayHaveLostData(uint16_t index)
{
    Node node(NodeType::STORAGE, index);
    NodeState newState = _newClusterState.getNodeState(node);
    NodeState oldState = _prevClusterState.getNodeState(node);

    return (newState.getStartTimestamp() > oldState.getStartTimestamp());
}

void
PendingBucketSpaceDbTransition::updateSetOfNodesThatAreOutdated()
{
    const uint16_t nodeCount(newStateStorageNodeCount());
    for (uint16_t index = 0; index < nodeCount; ++index) {
        if (storageNodeMayHaveLostData(index) || storageNodeChanged(index)) {
            _outdatedNodes.insert(index);
        }
    }
}

bool
PendingBucketSpaceDbTransition::storageNodeChanged(uint16_t index) {
    Node node(NodeType::STORAGE, index);
    NodeState newState = _newClusterState.getNodeState(node);
    NodeState oldNodeState = _prevClusterState.getNodeState(node);

    // similarTo() also covers disk states.
    if (!(oldNodeState.similarTo(newState))) {
        LOG(debug,
                "State for storage node %d has changed from '%s' to '%s', "
                "updating bucket information",
                index,
                oldNodeState.toString().c_str(),
                newState.toString().c_str());
        return true;
    }

    return false;
}

bool
PendingBucketSpaceDbTransition::storageNodeUpInNewState(uint16_t node) const
{
    return _newClusterState.getNodeState(Node(NodeType::STORAGE, node))
        .getState().oneOf(_clusterInfo->getStorageUpStates());
}

void
PendingBucketSpaceDbTransition::markAllAvailableNodesAsRequiringRequest()
{
    const uint16_t nodeCount(newStateStorageNodeCount());
    for (uint16_t i = 0; i < nodeCount; ++i) {
        if (storageNodeUpInNewState(i)) {
            _outdatedNodes.insert(i);
        }
    }
}

void
PendingBucketSpaceDbTransition::addAdditionalNodesToOutdatedSet(
        const std::unordered_set<uint16_t>& nodes)
{
    const uint16_t nodeCount(newStateStorageNodeCount());
    for (uint16_t node : nodes) {
        if (node < nodeCount) {
            _outdatedNodes.insert(node);
        }
    }
}

void
PendingBucketSpaceDbTransition::addNodeInfo(const document::BucketId& id, const BucketCopy& copy)
{
    _entries.emplace_back(id, copy);
}

}
