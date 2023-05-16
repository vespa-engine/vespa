// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "check_condition.h"
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
LOG_SETUP(".distributor.operations.external.put");

using document::BucketSpace;

namespace storage::distributor {

PutOperation::PutOperation(const DistributorNodeContext& node_ctx,
                           DistributorStripeOperationContext& op_ctx,
                           DistributorBucketSpace& bucket_space,
                           std::shared_ptr<api::PutCommand> msg,
                           PersistenceOperationMetricSet& metric,
                           PersistenceOperationMetricSet& condition_probe_metrics,
                           SequencingHandle sequencing_handle)
    : SequencedOperation(std::move(sequencing_handle)),
      _tracker_instance(metric, std::make_shared<api::PutReply>(*msg), node_ctx, op_ctx, msg->getTimestamp()),
      _tracker(_tracker_instance),
      _msg(std::move(msg)),
      _doc_id_bucket_id(document::BucketIdFactory{}.getBucketId(_msg->getDocumentId())),
      _node_ctx(node_ctx),
      _op_ctx(op_ctx),
      _condition_probe_metrics(condition_probe_metrics),
      _bucket_space(bucket_space)
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
        BucketDatabase::Entry entry(_bucket_space.getBucketDatabase().get(lastBucket));
        std::vector<uint16_t> idealState(
                _bucket_space.get_ideal_service_layer_nodes_bundle(lastBucket).get_available_nodes());
        active = ActiveCopy::calculate(idealState, _bucket_space.getDistribution(), entry,
                                       _op_ctx.distributor_config().max_activation_inhibited_out_of_sync_groups());
        LOG(debug, "Active copies for bucket %s: %s", entry.getBucketId().toString().c_str(), active.toString().c_str());
        for (uint32_t i=0; i<active.size(); ++i) {
            BucketCopy copy(*entry->getNode(active[i]._nodeIndex));
            copy.setActive(true);
            entry->updateNode(copy);
        }
        _bucket_space.getBucketDatabase().update(entry);
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
                                    uint16_t node, std::vector<PersistenceMessageTracker::ToSend>& putBatch)
{
    document::Bucket bucket(bucketSpace, bucketId);
    auto command = std::make_shared<api::PutCommand>(bucket, _msg->getDocument(), _msg->getTimestamp());
    LOG(debug, "Sending %s to node %u", command->toString().c_str(), node);

    copyMessageSettings(*_msg, *command);
    command->setUpdateTimestamp(_msg->getUpdateTimestamp());
    command->setCondition(_msg->getCondition());
    command->set_create_if_non_existent(_msg->get_create_if_non_existent());
    putBatch.emplace_back(std::move(command), node);

}

bool PutOperation::has_unavailable_targets_in_pending_state(const OperationTargetList& targets) const {
    // TODO handle this explicitly as part of operation abort/cancel edge
    auto* pending_state = _op_ctx.pending_cluster_state_or_null(_msg->getBucket().getBucketSpace());
    if (!pending_state) {
        return false;
    }
    const char* up_states = storage_node_up_states();
    return std::any_of(targets.begin(), targets.end(), [pending_state, up_states](const auto& target){
        return !pending_state->getNodeState(target.getNode()).getState().oneOf(up_states);
    });
}

bool PutOperation::at_least_one_storage_node_is_available() const {
    const lib::ClusterState& cluster_state = _bucket_space.getClusterState();

    const uint16_t storage_node_index_ubound = cluster_state.getNodeCount(lib::NodeType::STORAGE);
    for (uint16_t i = 0; i < storage_node_index_ubound; i++) {
        if (cluster_state.getNodeState(lib::Node(lib::NodeType::STORAGE, i))
                .getState().oneOf(storage_node_up_states()))
        {
            return true;
        }
    }
    return false;
}

bool PutOperation::has_condition() const noexcept {
    return _msg->hasTestAndSetCondition();
}

void
PutOperation::onStart(DistributorStripeMessageSender& sender)
{
    LOG(debug, "Received Put %s for bucket %s",
        _msg->getDocumentId().toString().c_str(), _doc_id_bucket_id.toString().c_str());

    if (!has_condition()) {
        start_direct_put_dispatch(sender);
    } else {
        start_conditional_put(sender);
    }
}

void PutOperation::start_conditional_put(DistributorStripeMessageSender& sender) {
    document::Bucket bucket(_msg->getBucket().getBucketSpace(), _doc_id_bucket_id);
    _check_condition = CheckCondition::create_if_inconsistent_replicas(bucket, _bucket_space, _msg->getDocumentId(),
                                                                       _msg->getCondition(), _node_ctx, _op_ctx,
                                                                       _condition_probe_metrics, _msg->getTrace().getLevel());
    if (!_check_condition) {
        start_direct_put_dispatch(sender);
    } else {
        // Inconsistent replicas; need write repair
        _check_condition->start_and_send(sender);
        auto& outcome = _check_condition->maybe_outcome(); // Might be done immediately
        if (outcome) {
            on_completed_check_condition(*outcome, sender);
        }
    }
}

void PutOperation::start_direct_put_dispatch(DistributorStripeMessageSender& sender) {
    LOG(debug, "Starting fast path Put %s for bucket %s",
        _msg->getDocumentId().toString().c_str(), _doc_id_bucket_id.toString().c_str());

    if (at_least_one_storage_node_is_available()) {
        std::vector<document::BucketId> bucketsToCheckForSplit;

        OperationTargetResolverImpl targetResolver(_bucket_space, _bucket_space.getBucketDatabase(),
                                                   _op_ctx.distributor_config().getMinimalBucketSplit(),
                                                   _bucket_space.getDistribution().getRedundancy(),
                                                   _msg->getBucket().getBucketSpace());
        OperationTargetList targets(targetResolver.getTargets(OperationTargetResolver::PUT, _doc_id_bucket_id));

        for (const auto& target : targets) {
            if (_op_ctx.has_pending_message(target.getNode().getIndex(), target.getBucket(),
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
        _bucket_space.getBucketDatabase().getParents(_doc_id_bucket_id, entries);

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
        for (const auto& target : targets) {
            sendPutToBucketOnNode(_msg->getBucket().getBucketSpace(), target.getBucketId(),
                                  target.getNode().getIndex(), putBatch);
        }

        if (!putBatch.empty()) {
            _tracker.queueMessageBatch(putBatch);
        } else {
            const char* error = "Can't store document: No storage nodes available";
            LOG(debug, "%s", error);
            _tracker.fail(sender, api::ReturnCode(api::ReturnCode::NOT_CONNECTED, error));
            return;
        }

        // Check whether buckets are large enough to be split.
        // TODO(vekterli): only check entries for sendToExisting?
        for (const auto& entry : entries) {
            _op_ctx.send_inline_split_if_bucket_too_large(_msg->getBucket().getBucketSpace(),
                                                          entry, _msg->getPriority());
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
    const auto& config = _op_ctx.distributor_config();
    if (config.isBucketActivationDisabled()) {
        return false;
    }
    return !targets.hasAnyExistingCopies();
}

void
PutOperation::onReceive(DistributorStripeMessageSender& sender, const std::shared_ptr<api::StorageReply>& msg)
{
    LOG(debug, "Received %s", msg->toString(true).c_str());
    if (!_check_condition) {
        _tracker.receiveReply(sender, dynamic_cast<api::BucketInfoReply&>(*msg));
    } else {
        _check_condition->handle_reply(sender, msg);
        auto& outcome = _check_condition->maybe_outcome();
        if (!outcome) {
            return; // Condition check not done yet
        }
        on_completed_check_condition(*outcome, sender);
    }
}

void
PutOperation::on_completed_check_condition(CheckCondition::Outcome& outcome,
                                           DistributorStripeMessageSender& sender)
{
    if (!outcome.trace().isEmpty()) {
        _tracker.add_trace_tree_to_reply(outcome.steal_trace());
    }
    const bool effectively_matched = (outcome.matched_condition()
                                      || (outcome.not_found() && _msg->get_create_if_non_existent()));
    if (effectively_matched) {
        _msg->clear_condition(); // Transform to unconditional Put
        start_direct_put_dispatch(sender);
    } else if (outcome.not_found()) {
        // TODO "not found" not a TaS error...
        _tracker.fail(sender, api::ReturnCode(api::ReturnCode::TEST_AND_SET_CONDITION_FAILED,
                                              "Document does not exist"));
    } else if (outcome.failed()) {
        api::ReturnCode wrapped_error(outcome.error_code().getResult(),
                                      "Failed during write repair condition probe step. Reason: " + outcome.error_code().getMessage());
        _tracker.fail(sender, wrapped_error);
    } else {
        _tracker.fail(sender, api::ReturnCode(api::ReturnCode::TEST_AND_SET_CONDITION_FAILED,
                                              "Condition did not match document"));
    }
    _check_condition.reset();
}

void
PutOperation::onClose(DistributorStripeMessageSender& sender)
{
    if (_check_condition) {
        _check_condition->cancel(sender);
    }
    _tracker.fail(sender, api::ReturnCode(api::ReturnCode::ABORTED, "Process is shutting down"));
}

}
