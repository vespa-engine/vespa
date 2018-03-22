// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "putoperation.h"

#include <vespa/document/fieldvalue/document.h>
#include <vespa/log/log.h>
#include <vespa/storage/distributor/activecopy.h>
#include <vespa/storage/distributor/operationtargetresolverimpl.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/vdslib/distribution/idealnodecalculatorimpl.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>

LOG_SETUP(".distributor.callback.doc.put");


using namespace storage::distributor;
using namespace storage;
using document::BucketSpace;

PutOperation::PutOperation(DistributorComponent& manager,
                           DistributorBucketSpace &bucketSpace,
                           const std::shared_ptr<api::PutCommand> & msg,
                           PersistenceOperationMetricSet& metric,
                           SequencingHandle sequencingHandle)
    : SequencedOperation(std::move(sequencingHandle)),
      _trackerInstance(metric,
               std::shared_ptr<api::BucketInfoReply>(new api::PutReply(*msg)),
               manager,
               msg->getTimestamp()),
      _tracker(_trackerInstance),
      _msg(msg),
      _manager(manager),
      _bucketSpace(bucketSpace)
{
};

namespace {

bool hasNode(const std::vector<uint16_t>& vec, uint16_t value) {
    for (uint32_t i = 0; i < vec.size(); i++) {
        if (vec[i] == value) {
            return true;
        }
    }

    return false;
};

}

void
PutOperation::getTargetNodes(const std::vector<uint16_t>& idealNodes,
                             std::vector<uint16_t>& targetNodes,
                             std::vector<uint16_t>& createNodes,
                             const BucketInfo& bucketInfo,
                             uint32_t redundancy)
{
    // First insert all nodes that are trusted or already in the ideal state.
    for (uint32_t i = 0; i < bucketInfo.getNodeCount(); i++) {
        if (bucketInfo.getNodeRef(i).trusted() || hasNode(idealNodes,bucketInfo.getNodeRef(i).getNode())) {
            LOG(spam, "Adding target node %u with %s since it's trusted or in ideal state",
                i, bucketInfo.getNodeRef(i).toString().c_str());
            targetNodes.push_back(bucketInfo.getNodeRef(i).getNode());
        }
    }

    // Then insert all nodes that already exist if we need them.
    for (uint32_t i = 0; targetNodes.size() < redundancy && i < bucketInfo.getNodeCount(); i++) {
        if (!hasNode(targetNodes, bucketInfo.getNodeRef(i).getNode())) {
            LOG(spam, "Adding target node %u with %s since it already exists",
                i, bucketInfo.getNodeRef(i).toString().c_str());
            targetNodes.push_back(bucketInfo.getNodeRef(i).getNode());
        }
    }

    // Then add stuff from ideal state.
    for (uint32_t i = 0; targetNodes.size() < redundancy && i < idealNodes.size(); i++) {
        if (!hasNode(targetNodes, idealNodes[i])) {
            targetNodes.push_back(idealNodes[i]);
            LOG(spam, "Adding target+create node %u it's in ideal state",
                idealNodes[i]);
            createNodes.push_back(idealNodes[i]);
        }
    }

    std::sort(targetNodes.begin(), targetNodes.end());
    std::sort(createNodes.begin(), createNodes.end());
}

// FIXME: deprecated! remove as soon as multoperationoperation is merely
// a haunting memory of the past since it's only used by that component!
bool
PutOperation::checkCreateBucket(const lib::Distribution& dist,
                                const lib::ClusterState& state,
                                BucketDatabase::Entry& entry,
                                std::vector<uint16_t>& targetNodes,
                                std::vector<MessageTracker::ToSend>& messagesToSend,
                                const api::StorageCommand& originalCommand)
{
    BucketInfo& info = entry.getBucketInfo();

    std::vector<uint16_t> createNodes;
    std::vector<uint16_t> idealNodes(
            dist.getIdealStorageNodes(state, entry.getBucketId(), "ui"));

    getTargetNodes(idealNodes,
                   targetNodes,
                   createNodes,
                   info,
                   dist.getRedundancy());

    ActiveList active(ActiveCopy::calculate(idealNodes, dist, entry));
    LOG(debug, "Active copies for bucket %s: %s",
        entry.getBucketId().toString().c_str(), active.toString().c_str());
    // Send create buckets for all nodes in ideal state where we don't
    // currently have copies.
    for (uint32_t i = 0; i < createNodes.size(); i++) {
        document::Bucket bucket(originalCommand.getBucket().getBucketSpace(), entry.getBucketId());
        std::shared_ptr<api::CreateBucketCommand> cbc(
                new api::CreateBucketCommand(bucket));
        if (active.contains(createNodes[i])) {
            BucketCopy copy(*entry->getNode(createNodes[i]));
            copy.setActive(true);
            entry->updateNode(copy);
            cbc->setActive(true);
        }
        LOG(debug, "Creating bucket on node %u: %s",
            createNodes[i], cbc->toString().c_str());

        copyMessageSettings(originalCommand, *cbc);
        messagesToSend.push_back(MessageTracker::ToSend(cbc, createNodes[i]));
    }

    // All nodes that we are not feeding to now will no longer be trusted.
    // TODO: Refactor?
    bool mustWrite = false;
    for (uint32_t i = 0; i < info.getNodeCount(); i++) {
        bool found = false;
        for (uint32_t j = 0; j < targetNodes.size(); j++) {
            if (info.getNodeRef(i).getNode() == targetNodes[j]) {
                LOG(spam,
                    "Found matching target node %u in %s",
                    targetNodes[i],
                    info.getNodeRef(i).toString().c_str());
                found = true;
                break;
            }
        }

        if (!found && info.getNodeRef(i).trusted()) {
            LOG(spam,
                "Setting mustWrite=true since %s is trusted",
                info.getNodeRef(i).toString().c_str());

            info.clearTrusted(info.getNodeRef(i).getNode());
            mustWrite = true;
        }
    }

    return mustWrite;
}

void
PutOperation::insertDatabaseEntryAndScheduleCreateBucket(
        const OperationTargetList& copies,
        bool setOneActive,
        const api::StorageCommand& originalCommand,
        std::vector<MessageTracker::ToSend>& messagesToSend)
{
    document::BucketId lastBucket;
    bool multipleBuckets = false;
    for (uint32_t i=0, n=copies.size(); i<n; ++i) {
        if (!copies[i].isNewCopy()) continue;
        if (lastBucket.getRawId() != 0 && copies[i].getBucketId() != lastBucket)
        {
            multipleBuckets = true;
        }
        lastBucket = copies[i].getBucketId();
        // Fake that we have a non-empty bucket so it isn't deleted.
        // Copy is inserted with timestamp 0 such that any actual bucket info
        // subsequently arriving from the storage node will always overwrite it.
        BucketCopy copy(BucketCopy::recentlyCreatedCopy(
                0, copies[i].getNode().getIndex()));
        _manager.updateBucketDatabase(document::Bucket(originalCommand.getBucket().getBucketSpace(), lastBucket), copy,
                                      DatabaseUpdate::CREATE_IF_NONEXISTING);
    }
    ActiveList active;
    if (setOneActive) {
        assert(!multipleBuckets);
        (void) multipleBuckets;
        BucketDatabase::Entry entry(
                _bucketSpace.getBucketDatabase().get(lastBucket));
        std::vector<uint16_t> idealState(
                _bucketSpace.getDistribution().getIdealStorageNodes(
                    _bucketSpace.getClusterState(), lastBucket, "ui"));
        active = ActiveCopy::calculate(idealState, _bucketSpace.getDistribution(),
                                       entry);
        LOG(debug, "Active copies for bucket %s: %s",
            entry.getBucketId().toString().c_str(), active.toString().c_str());
        for (uint32_t i=0; i<active.size(); ++i) {
            BucketCopy copy(*entry->getNode(active[i].nodeIndex));
            copy.setActive(true);
            entry->updateNode(copy);
        }
        _bucketSpace.getBucketDatabase().update(entry);
    }
    for (uint32_t i=0, n=copies.size(); i<n; ++i) {
        if (!copies[i].isNewCopy()) continue;
        document::Bucket bucket(originalCommand.getBucket().getBucketSpace(), copies[i].getBucketId());
        std::shared_ptr<api::CreateBucketCommand> cbc(
                new api::CreateBucketCommand(bucket));
        if (setOneActive && active.contains(copies[i].getNode().getIndex())) {
            cbc->setActive(true);
        }
        LOG(debug, "Creating bucket on node %u: %s",
            copies[i].getNode().getIndex(), cbc->toString().c_str());

        copyMessageSettings(originalCommand, *cbc);
        messagesToSend.push_back(MessageTracker::ToSend(
                cbc, copies[i].getNode().getIndex()));
    }
}

void
PutOperation::sendPutToBucketOnNode(
        document::BucketSpace bucketSpace,
        const document::BucketId& bucketId,
        const uint16_t node,
        std::vector<PersistenceMessageTracker::ToSend>& putBatch)
{
    document::Bucket bucket(bucketSpace, bucketId);
    std::shared_ptr<api::PutCommand> command(
            new api::PutCommand(
                    bucket,
                    _msg->getDocument(),
                    _msg->getTimestamp()));
    LOG(debug,
        "Sending %s to node %u",
        command->toString().c_str(),
        node);

    copyMessageSettings(*_msg, *command);
    command->setUpdateTimestamp(_msg->getUpdateTimestamp());
    command->setCondition(_msg->getCondition());
    putBatch.push_back(MessageTracker::ToSend(command, node));

}

void
PutOperation::onStart(DistributorMessageSender& sender)
{
    document::BucketIdFactory bucketIdFactory;
    document::BucketId bid = bucketIdFactory.getBucketId(_msg->getDocumentId());

    LOG(debug,
        "Received PUT %s for bucket %s",
        _msg->getDocumentId().toString().c_str(),
        bid.toString().c_str());

    lib::ClusterState systemState = _bucketSpace.getClusterState();

    // Don't do anything if all nodes are down.
    bool up = false;
    for (uint16_t i = 0; i < systemState.getNodeCount(lib::NodeType::STORAGE); i++) {
        if (systemState.getNodeState(lib::Node(lib::NodeType::STORAGE, i))
            .getState().oneOf(_manager.getDistributor().getStorageNodeUpStates()))
        {
            up = true;
        }
    }

    if (up) {
        std::vector<document::BucketId> bucketsToCheckForSplit;

        lib::IdealNodeCalculatorImpl idealNodeCalculator;
        idealNodeCalculator.setDistribution(_bucketSpace.getDistribution());
        idealNodeCalculator.setClusterState(_bucketSpace.getClusterState());
        OperationTargetResolverImpl targetResolver(
                _bucketSpace.getBucketDatabase(),
                idealNodeCalculator,
                _manager.getDistributor().getConfig().getMinimalBucketSplit(),
                _bucketSpace.getDistribution().getRedundancy(),
                _msg->getBucket().getBucketSpace());
        OperationTargetList targets(targetResolver.getTargets(
                OperationTargetResolver::PUT, bid));

        for (size_t i = 0; i < targets.size(); ++i) {
            if (_manager.getDistributor().getPendingMessageTracker().
                hasPendingMessage(targets[i].getNode().getIndex(),
                                  targets[i].getBucket(),
                                  api::MessageType::DELETEBUCKET_ID))
            {
                _tracker.fail(sender, api::ReturnCode(api::ReturnCode::BUCKET_DELETED,
                                "Bucket was being deleted while we got a PUT, failing "
                                "operation to be safe"));
                return;
            }
        }

        // Mark any entries we're not feeding to as not trusted.
        std::vector<BucketDatabase::Entry> entries;
        _bucketSpace.getBucketDatabase().getParents(bid, entries);

        std::vector<PersistenceMessageTracker::ToSend> createBucketBatch;
        if (targets.hasAnyNewCopies()) {
            insertDatabaseEntryAndScheduleCreateBucket(
                    targets,
                    shouldImplicitlyActivateReplica(targets),
                    *_msg,
                    createBucketBatch);
        }

        if (!createBucketBatch.empty()) {
            _tracker.queueMessageBatch(createBucketBatch);
        }

        std::vector<PersistenceMessageTracker::ToSend> putBatch;

        // Now send PUTs
        for (uint32_t i = 0; i < targets.size(); i++) {
            const OperationTarget& target(targets[i]);
            sendPutToBucketOnNode(_msg->getBucket().getBucketSpace(),
                                  target.getBucketId(), target.getNode().getIndex(),
                                  putBatch);
        }

        if (putBatch.size()) {
            _tracker.queueMessageBatch(putBatch);
        } else {
            const char* error = "Can't store document: No storage nodes available";
            LOG(debug, "%s", error);
            _tracker.fail(sender,
                          api::ReturnCode(api::ReturnCode::NOT_CONNECTED, error));
            return;
        }

        // Check whether buckets are large enough to be split.
        // TODO(vekterli): only check entries for sendToExisting?
        for (uint32_t i = 0; i < entries.size(); ++i) {
            _manager.getDistributor().checkBucketForSplit(
                    _msg->getBucket().getBucketSpace(),
                    entries[i],
                    _msg->getPriority());
        }

        _tracker.flushQueue(sender);
    } else {
        const char* error = "Can't store document: No storage nodes available";
        LOG(debug, "%s", error);
        _tracker.fail(sender, api::ReturnCode(api::ReturnCode::NOT_CONNECTED, error));
    }

    _msg = std::shared_ptr<api::PutCommand>();
}

bool
PutOperation::shouldImplicitlyActivateReplica(
        const OperationTargetList& targets) const
{
    const auto& config(_manager.getDistributor().getConfig());
    if (config.isBucketActivationDisabled()) {
        return false;
    }
    return !targets.hasAnyExistingCopies();
}

void
PutOperation::onReceive(DistributorMessageSender& sender,
                        const std::shared_ptr<api::StorageReply> & msg)
{
    LOG(debug, "Received %s", msg->toString(true).c_str());
    _tracker.receiveReply(sender, static_cast<api::BucketInfoReply&>(*msg));
}

void
PutOperation::onClose(DistributorMessageSender& sender)
{
    const char* error = "Process is shutting down";
    LOG(debug, "%s", error);
    _tracker.fail(sender, api::ReturnCode(api::ReturnCode::ABORTED, error));
}

