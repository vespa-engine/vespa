// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pendingclusterstate.h"
#include "bucketdbupdater.h"
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/storage/common/bucketoperationlogger.h>
#include <vespa/vespalib/util/xmlstream.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".pendingclusterstate");

namespace storage::distributor {

using lib::Node;
using lib::NodeType;
using lib::NodeState;

PendingClusterState::PendingClusterState(
        const framework::Clock& clock,
        const ClusterInformation::CSP& clusterInfo,
        DistributorMessageSender& sender,
        const std::shared_ptr<api::SetSystemStateCommand>& newStateCmd,
        const std::unordered_set<uint16_t>& outdatedNodes,
        api::Timestamp creationTimestamp)
    : _cmd(newStateCmd),
      _requestedNodes(newStateCmd->getSystemState().getNodeCount(lib::NodeType::STORAGE)),
      _outdatedNodes(newStateCmd->getSystemState().getNodeCount(lib::NodeType::STORAGE)),
      _iter(0),
      _prevClusterState(clusterInfo->getClusterState()),
      _newClusterState(newStateCmd->getSystemState()),
      _clock(clock),
      _clusterInfo(clusterInfo),
      _creationTimestamp(creationTimestamp),
      _sender(sender),
      _bucketOwnershipTransfer(distributorChanged(_prevClusterState, _newClusterState))
{
    logConstructionInformation();
    if (hasBucketOwnershipTransfer()) {
        markAllAvailableNodesAsRequiringRequest();
    } else {
        updateSetOfNodesThatAreOutdated();
        addAdditionalNodesToOutdatedSet(outdatedNodes);
    }
    if (shouldRequestBucketInfo()) {
        requestNodes();
    }
}

PendingClusterState::PendingClusterState(
        const framework::Clock& clock,
        const ClusterInformation::CSP& clusterInfo,
        DistributorMessageSender& sender,
        api::Timestamp creationTimestamp)
    : _requestedNodes(clusterInfo->getStorageNodeCount()),
      _outdatedNodes(clusterInfo->getStorageNodeCount()),
      _iter(0),
      _prevClusterState(clusterInfo->getClusterState()),
      _newClusterState(clusterInfo->getClusterState()),
      _clock(clock),
      _clusterInfo(clusterInfo),
      _creationTimestamp(creationTimestamp),
      _sender(sender),
      _bucketOwnershipTransfer(true)
{
    logConstructionInformation();
    markAllAvailableNodesAsRequiringRequest();
    if (shouldRequestBucketInfo()) {
        requestNodes();
    }
}

PendingClusterState::~PendingClusterState() {}

void
PendingClusterState::logConstructionInformation() const
{
    LOG(debug,
        "New PendingClusterState constructed with previous cluster "
        "state '%s', new cluster state '%s', distribution config "
        "hash: '%s'",
        _prevClusterState.toString().c_str(),
        _newClusterState.toString().c_str(),
        _clusterInfo->getDistribution().getNodeGraph().getDistributionConfigHash().c_str());
}

bool
PendingClusterState::storageNodeUpInNewState(uint16_t node) const
{
    return _newClusterState.getNodeState(Node(NodeType::STORAGE, node))
               .getState().oneOf(_clusterInfo->getStorageUpStates());
}

void
PendingClusterState::markAllAvailableNodesAsRequiringRequest()
{
    const uint16_t nodeCount(newStateStorageNodeCount());
    for (uint16_t i = 0; i < nodeCount; ++i) {
        if (storageNodeUpInNewState(i)) {
            _outdatedNodes.insert(i);
        }
    }
}

void
PendingClusterState::addAdditionalNodesToOutdatedSet(
        const std::unordered_set<uint16_t>& nodes)
{
    const uint16_t nodeCount(newStateStorageNodeCount());
    for (uint16_t node : nodes) {
        if (node < nodeCount) {
            _outdatedNodes.insert(node);
        }
    }
}

std::unordered_set<uint16_t>
PendingClusterState::getOutdatedNodeSet() const
{
    return _outdatedNodes;
}

uint16_t
PendingClusterState::newStateStorageNodeCount() const
{
    return _newClusterState.getNodeCount(lib::NodeType::STORAGE);
}

bool
PendingClusterState::shouldRequestBucketInfo() const
{
    if (clusterIsDown()) {
        LOG(debug, "Received system state where the cluster is down");
        return false;
    }
    if (iAmDown()) {
        LOG(debug, "Received system state where our node is down");
        return false;
    }
    return true;
}

bool
PendingClusterState::clusterIsDown() const
{
    return _newClusterState.getClusterState() == lib::State::DOWN;
}

bool
PendingClusterState::iAmDown() const
{
    const lib::NodeState& myState(
            _newClusterState.getNodeState(Node(NodeType::DISTRIBUTOR,
                                               _sender.getDistributorIndex())));
    return myState.getState() == lib::State::DOWN;
}

bool
PendingClusterState::storageNodeMayHaveLostData(uint16_t index)
{
    Node node(NodeType::STORAGE, index);
    NodeState newState = _newClusterState.getNodeState(node);
    NodeState oldState = _prevClusterState.getNodeState(node);

    return (newState.getStartTimestamp() > oldState.getStartTimestamp());
}

void
PendingClusterState::updateSetOfNodesThatAreOutdated()
{
    const uint16_t nodeCount(newStateStorageNodeCount());
    for (uint16_t index = 0; index < nodeCount; ++index) {
        if (storageNodeMayHaveLostData(index) || storageNodeChanged(index)) {
            _outdatedNodes.insert(index);
        }
    }
}

bool
PendingClusterState::storageNodeChanged(uint16_t index) {
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

void
PendingClusterState::requestNodes()
{
    LOG(debug,
        "New system state: Old state was %s, new state is %s",
        _prevClusterState.toString().c_str(),
        _newClusterState.toString().c_str());

    requestBucketInfoFromStorageNodesWithChangedState();
}

void
PendingClusterState::requestBucketInfoFromStorageNodesWithChangedState()
{
    for (uint16_t idx : _outdatedNodes) {
        if (storageNodeUpInNewState(idx)) {
            requestNode(idx);
        }
    }
}

bool
PendingClusterState::distributorChanged(
        const lib::ClusterState& oldState,
        const lib::ClusterState& newState)
{
    if (newState.getDistributionBitCount() !=
        oldState.getDistributionBitCount())
    {
        return true;
    }

    Node myNode(NodeType::DISTRIBUTOR, _sender.getDistributorIndex());
    if (oldState.getNodeState(myNode).getState() ==
        lib::State::DOWN)
    {
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
            return (nodeInSameGroupAsSelf(i)
                    || nodeNeedsOwnershipTransferFromGroupDown(i, newState));
        }
    }

    return false;
}

bool
PendingClusterState::nodeWasUpButNowIsDown(const lib::State& old,
                                           const lib::State& nw) const
{
    return (old.oneOf("uimr") && !nw.oneOf("uimr"));
}

bool
PendingClusterState::nodeInSameGroupAsSelf(uint16_t index) const
{
    if (_clusterInfo->nodeInSameGroupAsSelf(index)) {
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
PendingClusterState::nodeNeedsOwnershipTransferFromGroupDown(
        uint16_t nodeIndex,
        const lib::ClusterState& state) const
{
    const lib::Distribution& dist(_clusterInfo->getDistribution());
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

void
PendingClusterState::requestNode(uint16_t node)
{
    vespalib::string distributionHash(_clusterInfo->getDistributionHash());
    LOG(debug,
        "Requesting bucket info for node %d with cluster state '%s' "
        "and distribution hash '%s'",
        node,
        _newClusterState.toString().c_str(),
        distributionHash.c_str());

    std::shared_ptr<api::RequestBucketInfoCommand> cmd(
            new api::RequestBucketInfoCommand(
                    _sender.getDistributorIndex(),
                    _newClusterState,
                    distributionHash));

    cmd->setPriority(api::StorageMessage::HIGH);
    cmd->setTimeout(INT_MAX);

    _sentMessages[cmd->getMsgId()] = node;

    _sender.sendToNode(NodeType::STORAGE, node, cmd);
}


PendingClusterState::Summary::Summary(const std::string& prevClusterState,
                                      const std::string& newClusterState,
                                      uint32_t processingTime)
    : _prevClusterState(prevClusterState),
      _newClusterState(newClusterState),
      _processingTime(processingTime)
{}

PendingClusterState::Summary::Summary(const Summary &) = default;
PendingClusterState::Summary & PendingClusterState::Summary::operator = (const Summary &) = default;
PendingClusterState::Summary::~Summary() { }

bool
PendingClusterState::onRequestBucketInfoReply(const std::shared_ptr<api::RequestBucketInfoReply>& reply)
{
    auto iter = _sentMessages.find(reply->getMsgId());

    if (iter == _sentMessages.end()) {
        return false;
    }
    const uint16_t node = iter->second;

    if (!reply->getResult().success()) {
        framework::MilliSecTime resendTime(_clock);
        resendTime += framework::MilliSecTime(100);
        _delayedRequests.push_back(std::make_pair(resendTime, node));
        _sentMessages.erase(iter);
        return true;
    }

    setNodeReplied(node);

    for (uint32_t i = 0; i < reply->getBucketInfo().size(); ++i) {
        addNodeInfo(reply->getBucketInfo()[i]._bucketId,
                    BucketCopy(_creationTimestamp,
                               node,
                               reply->getBucketInfo()[i]._info));
    }

    _sentMessages.erase(iter);

    return true;
}

void
PendingClusterState::resendDelayedMessages() {
    if (_delayedRequests.empty()) return; // Don't fetch time if not needed
    framework::MilliSecTime currentTime(_clock);
    while (!_delayedRequests.empty()
           && currentTime >= _delayedRequests.front().first)
    {
        requestNode(_delayedRequests.front().second);
        _delayedRequests.pop_front();
    }
}

void
PendingClusterState::addNodeInfo(
        const document::BucketId& id,
        const BucketCopy& copy)
{
    _entries.push_back(Entry(id, copy));
}

PendingClusterState::Range
PendingClusterState::skipAllForSameBucket()
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

void
PendingClusterState::insertInfo(
        BucketDatabase::Entry& info,
        const Range& range)
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
            vespalib::make_vespa_string("insertInfo: %s",
                                        info.toString().c_str()));
}

std::vector<BucketCopy>
PendingClusterState::getCopiesThatAreNewOrAltered(
        BucketDatabase::Entry& info,
        const Range& range)
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

std::string
PendingClusterState::requestNodesToString()
{
    std::ostringstream ost;
    for (uint32_t i = 0; i < _requestedNodes.size(); ++i) {
        if (_requestedNodes[i]) {
            if (ost.str().length() > 0) {
                ost << ",";
            }
            ost << i;
        }
    }
    return ost.str();
}

bool
PendingClusterState::removeCopiesFromNodesThatWereRequested(
        BucketDatabase::Entry& e,
        const document::BucketId& bucketId)
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
PendingClusterState::databaseIteratorHasPassedBucketInfoIterator(
        const document::BucketId& bucketId) const
{
    return (_iter < _entries.size()
            && _entries[_iter].bucketId.toKey() < bucketId.toKey());
}

bool
PendingClusterState::bucketInfoIteratorPointsToBucket(
        const document::BucketId& bucketId) const
{
    return _iter < _entries.size() && _entries[_iter].bucketId == bucketId;
}

bool
PendingClusterState::process(BucketDatabase::Entry& e)
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
PendingClusterState::addToBucketDB(BucketDatabase& db,
                                   const Range& range)
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
PendingClusterState::mergeInto(BucketDatabase& db)
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
PendingClusterState::printXml(vespalib::XmlOutputStream& xos) const
{
    using namespace vespalib::xml;
    xos << XmlTag("systemstate_pending")
        << XmlAttribute("state", _newClusterState);
    for (std::map<uint64_t, uint16_t>::const_iterator iter
            = _sentMessages.begin(); iter != _sentMessages.end(); ++iter)
    {
        xos << XmlTag("pending")
            << XmlAttribute("node", iter->second)
            << XmlEndTag();
    }
    xos << XmlEndTag();
}

PendingClusterState::Summary
PendingClusterState::getSummary() const
{
    return Summary(_prevClusterState.toString(),
                   _newClusterState.toString(),
                   (_clock.getTimeInMicros().getTime() - _creationTimestamp));
}

}
