// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "putoperation.h"

#include <vespa/document/fieldvalue/document.h>
#include <vespa/storage/distributor/activecopy.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/operationtargetresolverimpl.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storage/distributor/storage_node_up_states.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/distribution/idealnodecalculatorimpl.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.callback.doc.put");


using namespace storage::distributor;
using namespace storage;
using document::BucketSpace;

PutOperation::PutOperation(const DistributorNodeContext& node_ctx,
                           DistributorStripeOperationContext& op_ctx,
                           DistributorBucketSpace &bucketSpace,
                           std::shared_ptr<api::PutCommand> msg,
                           PersistenceOperationMetricSet& metric, SequencingHandle sequencingHandle)
    : SequencedOperation(std::move(sequencingHandle)),
      _trackerInstance(metric, std::make_shared<api::PutReply>(*msg), node_ctx, op_ctx, msg->getTimestamp()),
      _tracker(_trackerInstance),
      _msg(std::move(msg)),
      _op_ctx(op_ctx),
      _bucketSpace(bucketSpace)
{
}

PutOperation::~PutOperation() = default;

void
PutOperation::insertDatabaseEntryAndScheduleCreateBucket(const OperationTargetList& copies, bool setOneActive,
                                                         const api::StorageCommand& originalCommand,
                                                         std::vector<MessageTracker::ToSend>& messagesToSend)
{
    document::BucketId lastBucket;
    bool multipleBuckets = false;
    for (uint32_t i=0, n=copies.size(); i<n; ++i) {
        if (!copies[i].isNewCopy()) continue;
        if (lastBucket.getRawId() != 0 && copies[i].getBucketId() != lastBucket) {
            multipleBuckets = true;
        }
        lastBucket = copies[i].getBucketId();
        // Fake that we have a non-empty bucket so it isn't deleted.
        // Copy is inserted with timestamp 0 such that any actual bucket info
        // subsequently arriving from the storage node will always overwrite it.
        BucketCopy copy(BucketCopy::recentlyCreatedCopy(0, copies[i].getNode().getIndex()));
        _op_ctx.update_bucket_database(document::Bucket(originalCommand.getBucket().getBucketSpace(), lastBucket), copy,
                                       DatabaseUpdate::CREATE_IF_NONEXISTING);
    }
    ActiveList active;
    if (setOneActive) {
        assert(!multipleBuckets);
        (void) multipleBuckets;
        BucketDatabase::Entry entry(_bucketSpace.getBucketDatabase().get(lastBucket));
        std::vector<uint16_t> idealState(
                _bucketSpace.get_ideal_service_layer_nodes_bundle(lastBucket).get_available_nodes());
        active = ActiveCopy::calculate(idealState, _bucketSpace.getDistribution(), entry,
                                       _op_ctx.distributor_config().max_activation_inhibited_out_of_sync_groups());
        LOG(debug, "Active copies for bucket %s: %s", entry.getBucketId().toString().c_str(), active.toString().c_str());
        for (uint32_t i=0; i<active.size(); ++i) {
            BucketCopy copy(*entry->getNode(active[i]._nodeIndex));
            copy.setActive(true);
            entry->updateNode(copy);
        }
        _bucketSpace.getBucketDatabase().update(entry);
    }
    for (uint32_t i=0, n=copies.size(); i<n; ++i) {
        if (!copies[i].isNewCopy()) continue;
        document::Bucket bucket(originalCommand.getBucket().getBucketSpace(), copies[i].getBucketId());
        auto cbc = std::make_shared<api::CreateBucketCommand>(bucket);
        if (setOneActive && active.contains(copies[i].getNode().getIndex())) {
            cbc->setActive(true);
        }
        LOG(debug, "Creating bucket on node %u: %s",
            copies[i].getNode().getIndex(), cbc->toString().c_str());

        copyMessageSettings(originalCommand, *cbc);
        messagesToSend.emplace_back(std::move(cbc), copies[i].getNode().getIndex());
    }
}

void
PutOperation::sendPutToBucketOnNode(document::BucketSpace bucketSpace, const document::BucketId& bucketId,
                                    const uint16_t node, std::vector<PersistenceMessageTracker::ToSend>& putBatch)
{
    document::Bucket bucket(bucketSpace, bucketId);
    auto command = std::make_shared<api::PutCommand>(bucket, _msg->getDocument(), _msg->getTimestamp());
    LOG(debug, "Sending %s to node %u", command->toString().c_str(), node);

    copyMessageSettings(*_msg, *command);
    command->setUpdateTimestamp(_msg->getUpdateTimestamp());
    command->setCondition(_msg->getCondition());
    putBatch.emplace_back(std::move(command), node);

}

bool PutOperation::has_unavailable_targets_in_pending_state(const OperationTargetList& targets) const {
    auto* pending_state = _op_ctx.pending_cluster_state_or_null(_msg->getBucket().getBucketSpace());
    if (!pending_state) {
        return false;
    }
    const char* up_states = storage_node_up_states();
    return std::any_of(targets.begin(), targets.end(), [pending_state, up_states](const auto& target){
        return !pending_state->getNodeState(target.getNode()).getState().oneOf(up_states);
    });
}

void
PutOperation::onStart(DistributorStripeMessageSender& sender)
{
    document::BucketIdFactory bucketIdFactory;
    document::BucketId bid = bucketIdFactory.getBucketId(_msg->getDocumentId());

    LOG(debug, "Received PUT %s for bucket %s", _msg->getDocumentId().toString().c_str(), bid.toString().c_str());

    lib::ClusterState systemState = _bucketSpace.getClusterState();

    // Don't do anything if all nodes are down.
    bool up = false;
    for (uint16_t i = 0; i < systemState.getNodeCount(lib::NodeType::STORAGE); i++) {
        if (systemState.getNodeState(lib::Node(lib::NodeType::STORAGE, i))
            .getState().oneOf(storage_node_up_states()))
        {
            up = true;
        }
    }

    if (up) {
        std::vector<document::BucketId> bucketsToCheckForSplit;

        OperationTargetResolverImpl targetResolver(_bucketSpace, _bucketSpace.getBucketDatabase(),
                _op_ctx.distributor_config().getMinimalBucketSplit(),
                _bucketSpace.getDistribution().getRedundancy(),
                _msg->getBucket().getBucketSpace());
        OperationTargetList targets(targetResolver.getTargets(OperationTargetResolver::PUT, bid));

        for (size_t i = 0; i < targets.size(); ++i) {
            if (_op_ctx.has_pending_message(targets[i].getNode().getIndex(), targets[i].getBucket(),
                                            api::MessageType::DELETEBUCKET_ID))
            {
                _tracker.fail(sender, api::ReturnCode(api::ReturnCode::BUCKET_DELETED,
                                "Bucket was being deleted while we got a PUT, failing operation to be safe"));
                return;
            }
        }

        if (has_unavailable_targets_in_pending_state(targets)) {
            _tracker.fail(sender, api::ReturnCode(
                    api::ReturnCode::BUSY, "One or more target content nodes are unavailable in "
                                           "the pending cluster state"));
            return;
        }

        // Mark any entries we're not feeding to as not trusted.
        std::vector<BucketDatabase::Entry> entries;
        _bucketSpace.getBucketDatabase().getParents(bid, entries);

        std::vector<PersistenceMessageTracker::ToSend> createBucketBatch;
        if (targets.hasAnyNewCopies()) {
            insertDatabaseEntryAndScheduleCreateBucket(targets, shouldImplicitlyActivateReplica(targets),
                                                       *_msg, createBucketBatch);
        }

        if (!createBucketBatch.empty()) {
            _tracker.queueMessageBatch(createBucketBatch);
        }

        std::vector<PersistenceMessageTracker::ToSend> putBatch;

        // Now send PUTs
        for (uint32_t i = 0; i < targets.size(); i++) {
            const OperationTarget& target(targets[i]);
            sendPutToBucketOnNode(_msg->getBucket().getBucketSpace(), target.getBucketId(),
                                  target.getNode().getIndex(), putBatch);
        }

        if (putBatch.size()) {
            _tracker.queueMessageBatch(putBatch);
        } else {
            const char* error = "Can't store document: No storage nodes available";
            LOG(debug, "%s", error);
            _tracker.fail(sender, api::ReturnCode(api::ReturnCode::NOT_CONNECTED, error));
            return;
        }

        // Check whether buckets are large enough to be split.
        // TODO(vekterli): only check entries for sendToExisting?
        for (uint32_t i = 0; i < entries.size(); ++i) {
            _op_ctx.send_inline_split_if_bucket_too_large(_msg->getBucket().getBucketSpace(),
                                                          entries[i], _msg->getPriority());
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
PutOperation::shouldImplicitlyActivateReplica(const OperationTargetList& targets) const
{
    const auto& config(_op_ctx.distributor_config());
    if (config.isBucketActivationDisabled()) {
        return false;
    }
    return !targets.hasAnyExistingCopies();
}

void
PutOperation::onReceive(DistributorStripeMessageSender& sender, const std::shared_ptr<api::StorageReply> & msg)
{
    LOG(debug, "Received %s", msg->toString(true).c_str());
    _tracker.receiveReply(sender, static_cast<api::BucketInfoReply&>(*msg));
}

void
PutOperation::onClose(DistributorStripeMessageSender& sender)
{
    const char* error = "Process is shutting down";
    LOG(debug, "%s", error);
    _tracker.fail(sender, api::ReturnCode(api::ReturnCode::ABORTED, error));
}
