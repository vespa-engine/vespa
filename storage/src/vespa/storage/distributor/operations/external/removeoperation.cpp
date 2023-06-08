// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "removeoperation.h"
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/vdslib/state/clusterstate.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.operations.external.remove");

using document::BucketSpace;

namespace storage::distributor {

RemoveOperation::RemoveOperation(const DistributorNodeContext& node_ctx,
                                 DistributorStripeOperationContext& op_ctx,
                                 DistributorBucketSpace& bucketSpace,
                                 std::shared_ptr<api::RemoveCommand> msg,
                                 PersistenceOperationMetricSet& metric,
                                 PersistenceOperationMetricSet& condition_probe_metrics,
                                 SequencingHandle sequencingHandle)
    : SequencedOperation(std::move(sequencingHandle)),
      _tracker_instance(metric,
               std::make_shared<api::RemoveReply>(*msg),
               node_ctx, op_ctx, msg->getTimestamp()),
      _tracker(_tracker_instance),
      _msg(std::move(msg)),
      _doc_id_bucket_id(document::BucketIdFactory{}.getBucketId(_msg->getDocumentId())),
      _node_ctx(node_ctx),
      _op_ctx(op_ctx),
      _condition_probe_metrics(condition_probe_metrics),
      _bucket_space(bucketSpace),
      _check_condition()
{
}

RemoveOperation::~RemoveOperation() = default;

void RemoveOperation::onStart(DistributorStripeMessageSender& sender) {
    LOG(spam, "Received remove on document %s", _msg->getDocumentId().toString().c_str());

    if (!has_condition()) {
        start_direct_remove_dispatch(sender);
    } else {
        start_conditional_remove(sender);
    }
}

void RemoveOperation::start_conditional_remove(DistributorStripeMessageSender& sender) {
    document::Bucket bucket(_msg->getBucket().getBucketSpace(), _doc_id_bucket_id);
    _check_condition = CheckCondition::create_if_inconsistent_replicas(bucket, _bucket_space, _msg->getDocumentId(),
                                                                       _msg->getCondition(), _node_ctx, _op_ctx,
                                                                       _condition_probe_metrics, _msg->getTrace().getLevel());
    if (!_check_condition) {
        start_direct_remove_dispatch(sender);
    } else {
        // Inconsistent replicas; need write repair
        _check_condition->start_and_send(sender);
        auto& outcome = _check_condition->maybe_outcome(); // Might be done immediately
        if (outcome) {
            on_completed_check_condition(*outcome, sender);
        }
    }
}

void RemoveOperation::start_direct_remove_dispatch(DistributorStripeMessageSender& sender) {
    LOG(spam, "Started remove on document %s", _msg->getDocumentId().toString().c_str());

    document::BucketId bucketId(
            _node_ctx.bucket_id_factory().getBucketId(
                    _msg->getDocumentId()));

    std::vector<BucketDatabase::Entry> entries;
    _bucket_space.getBucketDatabase().getParents(bucketId, entries);

    bool sent = false;

    for (const BucketDatabase::Entry& e : entries) {
        std::vector<MessageTracker::ToSend> messages;
        messages.reserve(e->getNodeCount());
        for (uint32_t i = 0; i < e->getNodeCount(); i++) {
            auto command = std::make_shared<api::RemoveCommand>(document::Bucket(_msg->getBucket().getBucketSpace(), e.getBucketId()),
                                                                _msg->getDocumentId(),
                                                                _msg->getTimestamp());

            copyMessageSettings(*_msg, *command);
            command->getTrace().setLevel(_msg->getTrace().getLevel());
            command->setCondition(_msg->getCondition());

            messages.emplace_back(std::move(command), e->getNodeRef(i).getNode());
            sent = true;
        }

        _tracker.queueMessageBatch(messages);
    }

    if (!sent) {
        LOG(debug,
            "Remove document %s failed since no available nodes found. "
            "System state is %s",
            _msg->getDocumentId().toString().c_str(),
            _bucket_space.getClusterState().toString().c_str());

        _tracker.fail(sender, api::ReturnCode(api::ReturnCode::OK));
    } else {
        _tracker.flushQueue(sender);
    }
};

void
RemoveOperation::onReceive(DistributorStripeMessageSender& sender, const std::shared_ptr<api::StorageReply> & msg)
{
    if (_check_condition) {
        _check_condition->handle_reply(sender, msg);
        auto& outcome = _check_condition->maybe_outcome();
        if (!outcome) {
            return; // Condition check not done yet
        }
        return on_completed_check_condition(*outcome, sender);
    }

    auto& reply = static_cast<api::RemoveReply&>(*msg);

    if (_tracker.getReply().get()) {
        api::RemoveReply& replyToSend =
            static_cast<api::RemoveReply&>(*_tracker.getReply());

        if (reply.getOldTimestamp() > replyToSend.getOldTimestamp()) {
            replyToSend.setOldTimestamp(reply.getOldTimestamp());
        }
    }

    _tracker.receiveReply(sender, reply);
}

void RemoveOperation::on_completed_check_condition(CheckCondition::Outcome& outcome,
                                                   DistributorStripeMessageSender& sender)
{
    if (!outcome.trace().isEmpty()) {
        _tracker.add_trace_tree_to_reply(outcome.steal_trace());
    }
    if (outcome.matched_condition()) {
        _msg->clear_condition(); // Transform to unconditional Remove
        start_direct_remove_dispatch(sender);
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
RemoveOperation::onClose(DistributorStripeMessageSender& sender)
{
    _tracker.fail(sender, api::ReturnCode(api::ReturnCode::ABORTED, "Process is shutting down"));
}

bool RemoveOperation::has_condition() const noexcept {
    return _msg->hasTestAndSetCondition();
}

}
