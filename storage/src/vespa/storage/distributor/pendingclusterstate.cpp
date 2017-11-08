// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pendingclusterstate.h"
#include "pending_bucket_space_db_transition.h"
#include "bucketdbupdater.h"
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/storage/common/bucketoperationlogger.h>
#include <vespa/vespalib/util/xmlstream.hpp>
#include <climits>

#include <vespa/log/log.h>
LOG_SETUP(".pendingclusterstate");

using document::BucketSpace;

namespace storage::distributor {

using lib::Node;
using lib::NodeType;
using lib::NodeState;

PendingClusterState::PendingClusterState(
        const framework::Clock& clock,
        const ClusterInformation::CSP& clusterInfo,
        DistributorMessageSender& sender,
        DistributorBucketSpaceRepo &bucketSpaceRepo,
        const std::shared_ptr<api::SetSystemStateCommand>& newStateCmd,
        const std::unordered_set<uint16_t>& outdatedNodes,
        api::Timestamp creationTimestamp)
    : _cmd(newStateCmd),
      _requestedNodes(newStateCmd->getSystemState().getNodeCount(lib::NodeType::STORAGE)),
      _outdatedNodes(newStateCmd->getSystemState().getNodeCount(lib::NodeType::STORAGE)),
      _prevClusterState(clusterInfo->getClusterState()),
      _newClusterState(newStateCmd->getSystemState()),
      _clock(clock),
      _clusterInfo(clusterInfo),
      _creationTimestamp(creationTimestamp),
      _sender(sender),
      _bucketSpaceRepo(bucketSpaceRepo),
      _bucketOwnershipTransfer(distributorChanged(_prevClusterState, _newClusterState)),
      _pendingTransition()
{
    logConstructionInformation();
    if (hasBucketOwnershipTransfer()) {
        markAllAvailableNodesAsRequiringRequest();
    } else {
        updateSetOfNodesThatAreOutdated();
        addAdditionalNodesToOutdatedSet(outdatedNodes);
    }
    constructorHelper();
}

PendingClusterState::PendingClusterState(
        const framework::Clock& clock,
        const ClusterInformation::CSP& clusterInfo,
        DistributorMessageSender& sender,
        DistributorBucketSpaceRepo &bucketSpaceRepo,
        api::Timestamp creationTimestamp)
    : _requestedNodes(clusterInfo->getStorageNodeCount()),
      _outdatedNodes(clusterInfo->getStorageNodeCount()),
      _prevClusterState(clusterInfo->getClusterState()),
      _newClusterState(clusterInfo->getClusterState()),
      _clock(clock),
      _clusterInfo(clusterInfo),
      _creationTimestamp(creationTimestamp),
      _sender(sender),
      _bucketSpaceRepo(bucketSpaceRepo),
      _bucketOwnershipTransfer(true),
      _pendingTransition()
{
    logConstructionInformation();
    markAllAvailableNodesAsRequiringRequest();
    constructorHelper();
}

PendingClusterState::~PendingClusterState() {}

void
PendingClusterState::constructorHelper()
{
    _pendingTransition = std::make_unique<PendingBucketSpaceDbTransition>(*this, _clusterInfo, _newClusterState, _creationTimestamp);
    if (shouldRequestBucketInfo()) {
        requestNodes();
    }
}

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
                    BucketSpace::placeHolder(),
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
    _pendingTransition->onRequestBucketInfoReply(*reply, node);
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

std::string
PendingClusterState::requestNodesToString() const
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

void
PendingClusterState::mergeInto(BucketDatabase& db)
{
    _pendingTransition->mergeInto(db);
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

const PendingBucketSpaceDbTransition::EntryList &
PendingClusterState::results() const
{
    return _pendingTransition->results();
}

void
PendingClusterState::addNodeInfo(const document::BucketId& id, const BucketCopy& copy)
{
    _pendingTransition->addNodeInfo(id, copy);
}

}
