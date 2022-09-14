// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_space_state_map.h"
#include "pending_bucket_space_db_transition.h"
#include "pendingclusterstate.h"
#include "top_level_bucket_db_updater.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/common/global_bucket_space_distribution_converter.h>
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/util/xmlstream.hpp>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <climits>

#include <vespa/log/bufferedlogger.h>
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
        const BucketSpaceStateMap& bucket_space_states,
        const std::shared_ptr<api::SetSystemStateCommand>& newStateCmd,
        const OutdatedNodesMap &outdatedNodesMap,
        api::Timestamp creationTimestamp)
    : _cmd(newStateCmd),
      _sentMessages(),
      _requestedNodes(newStateCmd->getSystemState().getNodeCount(lib::NodeType::STORAGE)),
      _delayedRequests(),
      _prevClusterStateBundle(clusterInfo->getClusterStateBundle()),
      _newClusterStateBundle(newStateCmd->getClusterStateBundle()),
      _clock(clock),
      _clusterInfo(clusterInfo),
      _creationTimestamp(creationTimestamp),
      _sender(sender),
      _bucket_space_states(bucket_space_states),
      _clusterStateVersion(_cmd->getClusterStateBundle().getVersion()),
      _isVersionedTransition(true),
      _bucketOwnershipTransfer(false),
      _pendingTransitions(),
      _node_features()
{
    logConstructionInformation();
    initializeBucketSpaceTransitions(false, outdatedNodesMap);
}

PendingClusterState::PendingClusterState(
        const framework::Clock& clock,
        const ClusterInformation::CSP& clusterInfo,
        DistributorMessageSender& sender,
        const BucketSpaceStateMap& bucket_space_states,
        api::Timestamp creationTimestamp)
    : _requestedNodes(clusterInfo->getStorageNodeCount()),
      _prevClusterStateBundle(clusterInfo->getClusterStateBundle()),
      _newClusterStateBundle(clusterInfo->getClusterStateBundle()),
      _clock(clock),
      _clusterInfo(clusterInfo),
      _creationTimestamp(creationTimestamp),
      _sender(sender),
      _bucket_space_states(bucket_space_states),
      _clusterStateVersion(0),
      _isVersionedTransition(false),
      _bucketOwnershipTransfer(true),
      _pendingTransitions(),
      _node_features()
{
    logConstructionInformation();
    initializeBucketSpaceTransitions(true, OutdatedNodesMap());
}

PendingClusterState::~PendingClusterState() = default;

void
PendingClusterState::initializeBucketSpaceTransitions(bool distributionChanged, const OutdatedNodesMap &outdatedNodesMap)
{
    OutdatedNodes emptyOutdatedNodes;
    for (const auto &elem : _bucket_space_states) {
        auto onItr = outdatedNodesMap.find(elem.first);
        const auto &outdatedNodes = (onItr == outdatedNodesMap.end()) ? emptyOutdatedNodes : onItr->second;
        auto pendingTransition =
            std::make_unique<PendingBucketSpaceDbTransition>(
                    elem.first, *elem.second, distributionChanged, outdatedNodes,
                    _clusterInfo, *_newClusterStateBundle.getDerivedClusterState(elem.first), _creationTimestamp);
        if (pendingTransition->getBucketOwnershipTransfer()) {
            _bucketOwnershipTransfer = true;
        }
        _pendingTransitions.emplace(elem.first, std::move(pendingTransition));
    }
    if (shouldRequestBucketInfo()) {
        requestNodes();
    }
}

void
PendingClusterState::logConstructionInformation() const
{
    const auto &distribution = _bucket_space_states.get(document::FixedBucketSpaces::default_space()).get_distribution();
    LOG(debug,
        "New PendingClusterState constructed with previous cluster "
        "state '%s', new cluster state '%s', distribution config "
        "hash: '%s'",
        getPrevClusterStateBundleString().c_str(),
        getNewClusterStateBundleString().c_str(),
        distribution.getNodeGraph().getDistributionConfigHash().c_str());
}

bool
PendingClusterState::storageNodeUpInNewState(document::BucketSpace bucketSpace, uint16_t node) const
{
    return _newClusterStateBundle.getDerivedClusterState(bucketSpace)->getNodeState(Node(NodeType::STORAGE, node))
               .getState().oneOf(_clusterInfo->getStorageUpStates());
}

PendingClusterState::OutdatedNodesMap
PendingClusterState::getOutdatedNodesMap() const
{
    OutdatedNodesMap outdatedNodesMap;
    for (const auto &elem : _pendingTransitions) {
        outdatedNodesMap.emplace(elem.first, elem.second->getOutdatedNodes());
    }
    return outdatedNodesMap;
}

uint16_t
PendingClusterState::newStateStorageNodeCount() const
{
    return _newClusterStateBundle.getBaselineClusterState()->getNodeCount(lib::NodeType::STORAGE);
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
    return _newClusterStateBundle.getBaselineClusterState()->getClusterState() == lib::State::DOWN;
}

bool
PendingClusterState::iAmDown() const
{
    const lib::NodeState& myState(
            _newClusterStateBundle.getBaselineClusterState()->getNodeState(Node(NodeType::DISTRIBUTOR,
                    _sender.getDistributorIndex())));
    return myState.getState() == lib::State::DOWN;
}

void
PendingClusterState::requestNodes()
{
    LOG(debug,
        "New system state: Old state was %s, new state is %s",
        getPrevClusterStateBundleString().c_str(),
        getNewClusterStateBundleString().c_str());

    requestBucketInfoFromStorageNodesWithChangedState();
}

void
PendingClusterState::requestBucketInfoFromStorageNodesWithChangedState()
{
    for (auto &elem : _pendingTransitions) {
        const OutdatedNodes &outdatedNodes(elem.second->getOutdatedNodes());
        for (uint16_t idx : outdatedNodes) {
            if (storageNodeUpInNewState(elem.first, idx)) {
                requestNode(BucketSpaceAndNode(elem.first, idx));
            }
        }
    }
}

void
PendingClusterState::requestNode(BucketSpaceAndNode bucketSpaceAndNode)
{
    const auto &distribution = _bucket_space_states.get(bucketSpaceAndNode.bucketSpace).get_distribution();
    vespalib::string distributionHash = distribution.getNodeGraph().getDistributionConfigHash();

    LOG(debug,
        "Requesting bucket info for bucket space %" PRIu64 " node %d with cluster state '%s' "
        "and distribution hash '%s'",
        bucketSpaceAndNode.bucketSpace.getId(),
        bucketSpaceAndNode.node,
        _newClusterStateBundle.getDerivedClusterState(bucketSpaceAndNode.bucketSpace)->toString().c_str(),
        distributionHash.c_str());

    auto cmd = std::make_shared<api::RequestBucketInfoCommand>(
                    bucketSpaceAndNode.bucketSpace,
                    _sender.getDistributorIndex(),
                    *_newClusterStateBundle.getDerivedClusterState(bucketSpaceAndNode.bucketSpace),
                    distributionHash);

    cmd->setPriority(api::StorageMessage::HIGH);
    cmd->setTimeout(vespalib::duration::max());

    _sentMessages.emplace(cmd->getMsgId(), bucketSpaceAndNode);

    _sender.sendToNode(NodeType::STORAGE, bucketSpaceAndNode.node, cmd);
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
PendingClusterState::Summary::~Summary() = default;

void PendingClusterState::update_reply_failure_statistics(const api::ReturnCode& result, const BucketSpaceAndNode& source) {
    auto transition_iter = _pendingTransitions.find(source.bucketSpace);
    assert(transition_iter != _pendingTransitions.end());
    auto& transition = *transition_iter->second;
    transition.increment_request_failures(source.node);
    // Edge triggered (rate limited) warning for content node bucket fetching failures
    if (transition.request_failures(source.node) == RequestFailureWarningEdgeTriggerThreshold) {
        LOGBP(warning, "Have failed multiple bucket info fetch requests towards node %u. Last received error is: %s",
              source.node, result.toString().c_str());
    }
    if (result.getResult() == api::ReturnCode::REJECTED) {
        transition.incrementRequestRejections(source.node);
    }
}

bool
PendingClusterState::onRequestBucketInfoReply(const std::shared_ptr<api::RequestBucketInfoReply>& reply)
{
    auto iter = _sentMessages.find(reply->getMsgId());

    if (iter == _sentMessages.end()) {
        return false;
    }
    const BucketSpaceAndNode bucketSpaceAndNode = iter->second;

    api::ReturnCode result(reply->getResult());
    if (!result.success()) {
        framework::MilliSecTime resendTime(_clock);
        resendTime += framework::MilliSecTime(100);
        _delayedRequests.emplace_back(resendTime, bucketSpaceAndNode);
        _sentMessages.erase(iter);
        update_reply_failure_statistics(result, bucketSpaceAndNode);
        return true;
    }

    setNodeReplied(bucketSpaceAndNode.node);
    auto transitionIter = _pendingTransitions.find(bucketSpaceAndNode.bucketSpace);
    assert(transitionIter != _pendingTransitions.end());
    transitionIter->second->onRequestBucketInfoReply(*reply, bucketSpaceAndNode.node);

    update_node_supported_features_from_reply(iter->second.node, *reply);

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
PendingClusterState::merge_into_bucket_databases(StripeAccessGuard& guard)
{
    for (auto &elem : _pendingTransitions) {
        elem.second->merge_into_bucket_databases(guard);
    }
}

void
PendingClusterState::printXml(vespalib::XmlOutputStream& xos) const
{
    using namespace vespalib::xml;
    xos << XmlTag("systemstate_pending")
        << XmlAttribute("state", *_newClusterStateBundle.getBaselineClusterState());
    for (auto &elem : _sentMessages) {
        xos << XmlTag("pending")
            << XmlAttribute("node", elem.second.node)
            << XmlEndTag();
    }
    xos << XmlEndTag();
}

PendingClusterState::Summary
PendingClusterState::getSummary() const
{
    return Summary(getPrevClusterStateBundleString(),
                   getNewClusterStateBundleString(),
                   (_clock.getTimeInMicros().getTime() - _creationTimestamp));
}

PendingBucketSpaceDbTransition &
PendingClusterState::getPendingBucketSpaceDbTransition(document::BucketSpace bucketSpace)
{
    auto transitionIter = _pendingTransitions.find(bucketSpace);
    assert(transitionIter != _pendingTransitions.end());
    return *transitionIter->second;
}

std::string
PendingClusterState::getNewClusterStateBundleString() const {
    return _newClusterStateBundle.getBaselineClusterState()->toString();
}
std::string
PendingClusterState::getPrevClusterStateBundleString() const {
    return _prevClusterStateBundle.getBaselineClusterState()->toString();
}

void
PendingClusterState::update_node_supported_features_from_reply(uint16_t node, const api::RequestBucketInfoReply& reply)
{
    const auto& src_feat = reply.supported_node_features();
    NodeSupportedFeatures dest_feat;
    dest_feat.unordered_merge_chaining               = src_feat.unordered_merge_chaining;
    dest_feat.two_phase_remove_location              = src_feat.two_phase_remove_location;
    dest_feat.no_implicit_indexing_of_active_buckets = src_feat.no_implicit_indexing_of_active_buckets;
    // This will overwrite per bucket-space reply, but does not matter since it's independent of bucket space.
    _node_features.insert(std::make_pair(node, dest_feat));
}

}
