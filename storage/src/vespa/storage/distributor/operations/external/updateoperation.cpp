// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "updateoperation.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>

LOG_SETUP(".distributor.operations.external.update");

using document::BucketSpace;

namespace storage::distributor {

UpdateOperation::UpdateOperation(const DistributorNodeContext& node_ctx,
                                 DistributorStripeOperationContext& op_ctx,
                                 DistributorBucketSpace& bucketSpace,
                                 const std::shared_ptr<api::UpdateCommand>& msg,
                                 std::vector<BucketDatabase::Entry> entries,
                                 UpdateMetricSet& metric)
    : Operation(),
      _trackerInstance(metric, std::make_shared<api::UpdateReply>(*msg),
                       node_ctx, op_ctx, msg->getTimestamp()),
      _tracker(_trackerInstance),
      _msg(msg),
      _entries(std::move(entries)),
      _new_timestamp(_msg->getTimestamp()),
      _is_auto_create_update(_msg->getUpdate()->getCreateIfNonExistent()),
      _node_ctx(node_ctx),
      _op_ctx(op_ctx),
      _bucketSpace(bucketSpace),
      _newestTimestampLocation(),
      _infoAtSendTime(),
      _metrics(metric)
{
}

UpdateOperation::~UpdateOperation() = default;

bool
UpdateOperation::anyStorageNodesAvailable() const
{
    const auto& clusterState(_bucketSpace.getClusterState());
    const auto storageNodeCount(
            clusterState.getNodeCount(lib::NodeType::STORAGE));

    for (uint16_t i = 0; i < storageNodeCount; ++i) {
        const auto& ns(clusterState.getNodeState(
                lib::Node(lib::NodeType::STORAGE, i)));
        if (ns.getState() == lib::State::UP
            || ns.getState() == lib::State::RETIRED)
        {
            return true;
        }
    }
    return false;
}

void
UpdateOperation::onStart(DistributorStripeMessageSender& sender)
{
    LOG(debug, "Received UPDATE %s for bucket %" PRIx64,
        _msg->getDocumentId().toString().c_str(),
        _node_ctx.bucket_id_factory().getBucketId(
                _msg->getDocumentId()).getRawId());

    // Don't do anything if all nodes are down.
    if (!anyStorageNodesAvailable()) {
        _tracker.fail(sender,
                      api::ReturnCode(api::ReturnCode::NOT_CONNECTED,
                                      "Can't store document: No storage nodes "
                                      "available"));
        return;
    }

    if (_entries.empty()) {
        document::BucketId bucketId(_node_ctx.bucket_id_factory().getBucketId(_msg->getDocumentId()));
        _bucketSpace.getBucketDatabase().getParents(bucketId, _entries);
    }

    if (_entries.empty()) {
        _tracker.fail(sender,
                      api::ReturnCode(api::ReturnCode::OK,
                                      "No buckets found for given document update"));
        return;
    }

    // An UpdateOperation should only be started iff all replicas are consistent
    // with each other, so sampling a single replica should be equal to sampling them all.
    assert(_entries[0].getBucketInfo().getNodeCount() > 0); // Empty buckets are not allowed
    _infoAtSendTime = _entries[0].getBucketInfo().getNodeRef(0).getBucketInfo();

    // FIXME(vekterli): this loop will happily update all replicas in the
    // bucket sub-tree, but there is nothing here at all which will fail the
    // update if we cannot satisfy a desired replication level (not even for
    // n-of-m operations).
    for (const auto& entry : _entries) {
        LOG(spam, "Found bucket %s", entry.toString().c_str());

        const std::vector<uint16_t>& nodes = entry->getNodes();

        std::vector<MessageTracker::ToSend> messages;

        for (uint16_t node : nodes) {
            auto command = std::make_shared<api::UpdateCommand>(
                    document::Bucket(_msg->getBucket().getBucketSpace(), entry.getBucketId()),
                    _msg->getUpdate(),
                    _msg->getTimestamp());
            copyMessageSettings(*_msg, *command);
            command->setOldTimestamp(_msg->getOldTimestamp());
            command->setCondition(_msg->getCondition());
            messages.emplace_back(std::move(command), node);
        }

        _tracker.queueMessageBatch(messages);
    }

    _tracker.flushQueue(sender);
    _msg = std::shared_ptr<api::UpdateCommand>();
};

void
UpdateOperation::onReceive(DistributorStripeMessageSender& sender,
                          const std::shared_ptr<api::StorageReply> & msg)
{
    auto& reply = static_cast<api::UpdateReply&>(*msg);

    if (msg->getType() == api::MessageType::UPDATE_REPLY) {
        uint16_t node = _tracker.handleReply(reply);

        if (node != (uint16_t)-1) {
            if (reply.getResult().getResult() == api::ReturnCode::OK) {
                _results.emplace_back(reply.getBucketId(), reply.getBucketInfo(),
                                      adjusted_received_old_timestamp(reply.getOldTimestamp()), node);
            }

            if (_tracker.getReply().get()) {
                auto& replyToSend = static_cast<api::UpdateReply&>(*_tracker.getReply());

                uint64_t oldTs = 0;
                uint64_t goodNode = 0;

                // Find the highest old timestamp.
                for (uint32_t i = 0; i < _results.size(); i++) {
                    if (_results[i].oldTs > oldTs) {
                        oldTs = _results[i].oldTs;
                        goodNode = i;
                    }
                }

                replyToSend.setOldTimestamp(oldTs);

                for (uint32_t i = 0; i < _results.size(); i++) {
                    if (_results[i].oldTs < oldTs) {
                        log_inconsistency_warning(reply, _results[goodNode], _results[i]);
                        replyToSend.setNodeWithNewestTimestamp(_results[goodNode].nodeId);
                        _newestTimestampLocation.first  = _results[goodNode].bucketId;
                        _newestTimestampLocation.second = _results[goodNode].nodeId;
                        _metrics.diverging_timestamp_updates.inc();
                        break;
                    }
                }
            }

            _tracker.updateFromReply(sender, reply, node);
        }
    } else {
        _tracker.receiveReply(sender, static_cast<api::BucketInfoReply&>(*msg));
    }
}

void
UpdateOperation::log_inconsistency_warning(const api::UpdateReply& reply,
                                           const PreviousDocumentVersion& highest_timestamped_version,
                                           const PreviousDocumentVersion& low_timestamped_version)
{
    bool low_ts_node_gc = _op_ctx.has_pending_message(low_timestamped_version.nodeId, reply.getBucket(),
                                                      api::MessageType::REMOVELOCATION_ID);
    bool high_ts_node_gc = _op_ctx.has_pending_message(highest_timestamped_version.nodeId, reply.getBucket(),
                                                       api::MessageType::REMOVELOCATION_ID);

    LOG(warning, "Update operation for '%s' in bucket %s updated documents with different timestamps. "
                 "This should not happen and may indicate undetected replica divergence. "
                 "Found low ts=%" PRIu64 " on node %u (pending GC: %s), "
                 "highest ts=%" PRIu64 " on node %u (pending GC: %s)",
        reply.getDocumentId().toString().c_str(),
        reply.getBucket().toString().c_str(),
        low_timestamped_version.oldTs, low_timestamped_version.nodeId, (low_ts_node_gc ? "yes" : "no"),
        highest_timestamped_version.oldTs, highest_timestamped_version.nodeId, (high_ts_node_gc ? "yes" : "no"));

    LOG(warning, "Bucket info prior to update operation was: %s. After update, "
                 "info on node %u is %s, info on node %u is %s",
        _infoAtSendTime.toString().c_str(),
        low_timestamped_version.nodeId, low_timestamped_version.bucketInfo.toString().c_str(),
        highest_timestamped_version.nodeId, highest_timestamped_version.bucketInfo.toString().c_str());

}

void
UpdateOperation::onClose(DistributorStripeMessageSender& sender)
{
    _tracker.fail(sender, api::ReturnCode(api::ReturnCode::ABORTED, "Process is shutting down"));
}

// The backend behavior of "create-if-missing" updates is to return the timestamp of the
// _new_ update operation if the document was created from scratch. The two-phase update
// operation logic auto-detects unexpected inconsistencies and tries to reconcile
// replicas by forcing document versions to that assumed most likely to preserve the history
// of the document. Normally this is the highest updated timestamp, so to avoid newly created
// replicas from overwriting updates that actually updated existing document versions, treat
// a received timestamp == new timestamp as if it were actually a timestamp of zero.
// This mirrors the received timestamp for regular updates that do not find a matching document.
api::Timestamp UpdateOperation::adjusted_received_old_timestamp(api::Timestamp old_ts_from_node) const {
    if (!_is_auto_create_update) {
        return old_ts_from_node;
    }
    return (old_ts_from_node != _new_timestamp) ? old_ts_from_node : api::Timestamp(0);
}

}
