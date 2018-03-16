// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mergeoperation.h"
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".distributor.operation.idealstate.merge");

namespace storage::distributor {

MergeOperation::~MergeOperation() {}

std::string
MergeOperation::getStatus() const
{
    return
        Operation::getStatus() +
        vespalib::make_string(" . Sent MergeBucketCommand at %s",
                              _sentMessageTime.toString().c_str());
}

void
MergeOperation::addIdealNodes(
        const std::vector<uint16_t>& idealNodes,
        const std::vector<MergeMetaData>& nodes,
        std::vector<MergeMetaData>& result)
{
    // Add all ideal nodes first. These are never marked source-only.
    for (uint32_t i = 0; i < idealNodes.size(); i++) {
        const MergeMetaData* entry = nullptr;
        for (uint32_t j = 0; j < nodes.size(); j++) {
            if (idealNodes[i] == nodes[j]._nodeIndex) {
                entry = &nodes[j];
                break;
            }
        }

        if (entry != nullptr) {
            result.push_back(*entry);
            result.back()._sourceOnly = false;
        }
    }
}

void
MergeOperation::addCopiesNotAlreadyAdded(
        uint16_t redundancy,
        const std::vector<MergeMetaData>& nodes,
        std::vector<MergeMetaData>& result)
{
    for (uint32_t i = 0; i < nodes.size(); i++) {
        bool found = false;
        for (uint32_t j = 0; j < result.size(); j++) {
            if (result[j]._nodeIndex == nodes[i]._nodeIndex) {
                found = true;
            }
        }

        if (!found) {
            result.push_back(nodes[i]);
            result.back()._sourceOnly = (result.size() > redundancy);
        }
    }
}

void
MergeOperation::generateSortedNodeList(
        const lib::Distribution& distribution,
        const lib::ClusterState& state,
        const document::BucketId& bucketId,
        MergeLimiter& limiter,
        std::vector<MergeMetaData>& nodes)
{
    std::vector<uint16_t> idealNodes(
            distribution.getIdealStorageNodes(state, bucketId, "ui"));

    std::vector<MergeMetaData> result;
    const uint16_t redundancy = distribution.getRedundancy();
    addIdealNodes(idealNodes, nodes, result);
    addCopiesNotAlreadyAdded(redundancy, nodes, result);
    // TODO optimization: when merge case is obviously a replica move (all existing N replicas
    // are in sync and new replicas are empty), could prune away N-1 lowest indexed replicas
    // from the node list. This would minimize the number of nodes involved in the merge without
    // sacrificing the end result. Avoiding the lower indexed nodes would take pressure off the
    // merge throttling "locks" and could potentially greatly speed up node retirement in the common
    // case. Existing replica could also be marked as source-only if it's not in the ideal state.
    limiter.limitMergeToMaxNodes(result);
    result.swap(nodes);
}

namespace {

struct NodeIndexComparator
{
    bool operator()(const storage::api::MergeBucketCommand::Node& a,
                    const storage::api::MergeBucketCommand::Node& b) const
    {
        return a.index < b.index;
    }
};

}

void
MergeOperation::onStart(DistributorMessageSender& sender)
{
    BucketDatabase::Entry entry = _bucketSpace->getBucketDatabase().get(getBucketId());
    if (!entry.valid()) {
        LOGBP(debug, "Unable to merge nonexisting bucket %s", getBucketId().toString().c_str());
        _ok = false;
        done();
        return;
    }

    const lib::ClusterState& clusterState(_bucketSpace->getClusterState());
    std::vector<std::unique_ptr<BucketCopy> > newCopies;
    std::vector<MergeMetaData> nodes;

    for (uint32_t i = 0; i < getNodes().size(); ++i) {
        const BucketCopy* copy = entry->getNode(getNodes()[i]);
        if (copy == nullptr) { // New copies?
            newCopies.emplace_back(std::make_unique<BucketCopy>(BucketCopy::recentlyCreatedCopy(0, getNodes()[i])));
            copy = newCopies.back().get();
        }
        nodes.emplace_back(getNodes()[i], *copy);
    }
    _infoBefore = entry.getBucketInfo();

    generateSortedNodeList(_bucketSpace->getDistribution(),
                           clusterState,
                           getBucketId(),
                           _limiter,
                           nodes);
    for (uint32_t i=0; i<nodes.size(); ++i) {
        _mnodes.push_back(api::MergeBucketCommand::Node(
                nodes[i]._nodeIndex, nodes[i]._sourceOnly));
    }

    if (_mnodes.size() > 1) {
        auto msg = std::make_shared<api::MergeBucketCommand>(
                getBucket(),
                _mnodes,
                _manager->getDistributorComponent().getUniqueTimestamp(),
                clusterState.getVersion());

        // Due to merge forwarding/chaining semantics, we must always send
        // the merge command to the lowest indexed storage node involved in
        // the merge in order to avoid deadlocks.
        std::sort(_mnodes.begin(), _mnodes.end(), NodeIndexComparator());

        LOG(debug, "Sending %s to storage node %u", msg->toString().c_str(),
            _mnodes[0].index);

        // Set timeout to one hour to prevent hung nodes that manage to keep
        // connections open from stalling merges in the cluster indefinitely.
        msg->setTimeout(60 * 60 * 1000);
        setCommandMeta(*msg);

        sender.sendToNode(lib::NodeType::STORAGE, _mnodes[0].index, msg);

        _sentMessageTime = _manager->getDistributorComponent().getClock().getTimeInSeconds();
    } else {
        LOGBP(debug,
              "Unable to merge bucket %s, since only one copy is available. System state %s",
              getBucketId().toString().c_str(), clusterState.toString().c_str());
        _ok = false;
        done();
    }
}

bool
MergeOperation::sourceOnlyCopyChangedDuringMerge(
        const BucketDatabase::Entry& currentState) const
{
    assert(currentState.valid());
    for (size_t i = 0; i < _mnodes.size(); ++i) {
        const BucketCopy* copyBefore(_infoBefore.getNode(_mnodes[i].index));
        if (!copyBefore) {
            continue;
        }
        const BucketCopy* copyAfter(currentState->getNode(_mnodes[i].index));
        if (!copyAfter) {
            LOG(debug, "Copy of %s on node %u removed during merge. Was %s",
                getBucketId().toString().c_str(),
                _mnodes[i].index,
                copyBefore->toString().c_str());
            continue;
        }
        if (_mnodes[i].sourceOnly
            && !copyBefore->consistentWith(*copyAfter))
        {
            LOG(debug, "Source-only copy of %s on node %u changed from "
                "%s to %s during the course of the merge. Failing it.",
                getBucketId().toString().c_str(),
                _mnodes[i].index,
                copyBefore->toString().c_str(),
                copyAfter->toString().c_str());
            return true;
        }
    }

    return false;
}

void
MergeOperation::deleteSourceOnlyNodes(
        const BucketDatabase::Entry& currentState,
        DistributorMessageSender& sender)
{
    assert(currentState.valid());
    std::vector<uint16_t> sourceOnlyNodes;
    for (uint32_t i = 0; i < _mnodes.size(); ++i) {
        const uint16_t nodeIndex = _mnodes[i].index;
        const BucketCopy* copy = currentState->getNode(nodeIndex);
        if (!copy) {
            continue; // No point in deleting what's not even there now.
        }
        if (_mnodes[i].sourceOnly) {
            sourceOnlyNodes.push_back(nodeIndex);
        }
    }

    LOG(debug, "Attempting to delete %zu source only copies for %s",
        sourceOnlyNodes.size(),
        getBucketId().toString().c_str());

    if (!sourceOnlyNodes.empty()) {
        _removeOperation.reset(
                new RemoveBucketOperation(
                        _manager->getDistributorComponent().getClusterName(),
                        BucketAndNodes(getBucket(), sourceOnlyNodes)));
        // Must not send removes to source only copies if something has caused
        // pending load to the copy after the merge was sent!
        if (_removeOperation->isBlocked(sender.getPendingMessageTracker())) {
            LOG(debug,
                "Source only removal for %s was blocked by a pending "
                "operation",
                getBucketId().toString().c_str());
            _ok = false;
            done();
            return;
        }
        _removeOperation->setIdealStateManager(_manager);
        
        if (_removeOperation->onStartInternal(sender)) {
            _ok = _removeOperation->ok();
            done();
        }
    }
    // FIXME what about the else-case here...? done() is not invoked by caller in this branch.
    // Not calling done() doesn't leak anything, but causes metric updates to be missed.
}

void
MergeOperation::onReceive(DistributorMessageSender& sender,
                          const std::shared_ptr<api::StorageReply> & msg)
{
    if (_removeOperation.get()) {
        if (_removeOperation->onReceiveInternal(msg)) {
            _ok = _removeOperation->ok();
            done();
        }

        return;
    }

    api::MergeBucketReply& reply(dynamic_cast<api::MergeBucketReply&>(*msg));
    LOG(debug,
        "Merge operation for bucket %s finished",
        getBucketId().toString().c_str());

    api::ReturnCode result = reply.getResult();
    _ok = result.success();
    if (_ok) {
        BucketDatabase::Entry entry(
                _bucketSpace->getBucketDatabase().get(getBucketId()));
        if (!entry.valid()) {
            LOG(debug, "Bucket %s no longer exists after merge",
                getBucketId().toString().c_str());
            done(); // Nothing more we can do.
            return;
        }
        if (sourceOnlyCopyChangedDuringMerge(entry)) {
            _ok = false;
            done();
            return;
        }
        deleteSourceOnlyNodes(entry, sender);
        return;
    } else if (result.isBusy()) {
    } else if (result.isCriticalForMaintenance()) {
        LOGBP(warning,
              "Merging failed for %s: %s with error '%s'",
              getBucketId().toString().c_str(),
              msg->toString().c_str(),
              msg->getResult().toString().c_str());
    } else {
        LOG(debug, "Merge failed for %s with non-critical failure: %s",
            getBucketId().toString().c_str(), result.toString().c_str());
    }
    done();
}

namespace {

constexpr std::array<uint32_t, 7> WRITE_FEED_MESSAGE_TYPES {{
    api::MessageType::PUT_ID,
    api::MessageType::REMOVE_ID,
    api::MessageType::UPDATE_ID,
    api::MessageType::REMOVELOCATION_ID,
    api::MessageType::BATCHPUTREMOVE_ID,
    api::MessageType::BATCHDOCUMENTUPDATE_ID
}};

}

bool MergeOperation::shouldBlockThisOperation(uint32_t messageType, uint8_t pri) const {
    for (auto blocking_type : WRITE_FEED_MESSAGE_TYPES) {
        if (messageType == blocking_type) {
            return true;
        }
    }

    return IdealStateOperation::shouldBlockThisOperation(messageType, pri);
}

bool MergeOperation::isBlocked(const PendingMessageTracker& pending_tracker) const {
    const auto& node_info = pending_tracker.getNodeInfo();
    for (auto node : getNodes()) {
        if (node_info.isBusy(node)) {
            return true;
        }
    }
    return IdealStateOperation::isBlocked(pending_tracker);
}

}
