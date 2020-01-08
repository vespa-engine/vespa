// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "updateoperation.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>

LOG_SETUP(".distributor.callback.doc.update");


using namespace storage::distributor;
using namespace storage;
using document::BucketSpace;

UpdateOperation::UpdateOperation(DistributorComponent& manager,
                                 DistributorBucketSpace &bucketSpace,
                                 const std::shared_ptr<api::UpdateCommand> & msg,
                                 UpdateMetricSet& metric)
    : Operation(),
      _trackerInstance(metric, std::make_shared<api::UpdateReply>(*msg),
                       manager, msg->getTimestamp()),
      _tracker(_trackerInstance),
      _msg(msg),
      _manager(manager),
      _bucketSpace(bucketSpace),
      _newestTimestampLocation(),
      _infoAtSendTime(),
      _metrics(metric)
{
}

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
UpdateOperation::onStart(DistributorMessageSender& sender)
{
    LOG(debug, "Received UPDATE %s for bucket %" PRIx64,
        _msg->getDocumentId().toString().c_str(),
        _manager.getBucketIdFactory().getBucketId(
                _msg->getDocumentId()).getRawId());

    // Don't do anything if all nodes are down.
    if (!anyStorageNodesAvailable()) {
        _tracker.fail(sender,
                      api::ReturnCode(api::ReturnCode::NOT_CONNECTED,
                                      "Can't store document: No storage nodes "
                                      "available"));
        return;
    }

    document::BucketId bucketId(
            _manager.getBucketIdFactory().getBucketId(
                    _msg->getDocumentId()));

    std::vector<BucketDatabase::Entry> entries;
    _bucketSpace.getBucketDatabase().getParents(bucketId, entries);

    if (entries.empty()) {
        _tracker.fail(sender,
                      api::ReturnCode(api::ReturnCode::OK,
                                      "No buckets found for given document update"));
        return;
    }
    // An UpdateOperation should only be started iff all replicas are consistent
    // with each other, so sampling a single replica should be equal to sampling them all.
    assert(entries[0].getBucketInfo().getNodeCount() > 0); // Empty buckets are not allowed
    _infoAtSendTime = entries[0].getBucketInfo().getNodeRef(0).getBucketInfo();

    // FIXME(vekterli): this loop will happily update all replicas in the
    // bucket sub-tree, but there is nothing here at all which will fail the
    // update if we cannot satisfy a desired replication level (not even for
    // n-of-m operations).
    for (uint32_t j = 0; j < entries.size(); ++j) {
        LOG(spam, "Found bucket %s", entries[j].toString().c_str());

        const std::vector<uint16_t>& nodes = entries[j]->getNodes();

        std::vector<MessageTracker::ToSend> messages;

        for (uint32_t i = 0; i < nodes.size(); i++) {
            std::shared_ptr<api::UpdateCommand> command(
                    new api::UpdateCommand(document::Bucket(_msg->getBucket().getBucketSpace(), entries[j].getBucketId()),
                            _msg->getUpdate(),
                            _msg->getTimestamp()));
            copyMessageSettings(*_msg, *command);
            command->setOldTimestamp(_msg->getOldTimestamp());
            command->setCondition(_msg->getCondition());
            messages.push_back(MessageTracker::ToSend(command, nodes[i]));
        }

        _tracker.queueMessageBatch(messages);
    }

    _tracker.flushQueue(sender);
    _msg = std::shared_ptr<api::UpdateCommand>();
};

void
UpdateOperation::onReceive(DistributorMessageSender& sender,
                          const std::shared_ptr<api::StorageReply> & msg)
{
    auto& reply = static_cast<api::UpdateReply&>(*msg);

    if (msg->getType() == api::MessageType::UPDATE_REPLY) {
        uint16_t node = _tracker.handleReply(reply);

        if (node != (uint16_t)-1) {
            if (reply.getResult().getResult() == api::ReturnCode::OK) {
                _results.emplace_back(reply.getBucketId(), reply.getBucketInfo(), reply.getOldTimestamp(), node);
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
                        LOG(error, "Update operation for '%s' in bucket %s updated documents with different timestamps. "
                                   "This should not happen and may indicate undetected replica divergence. "
                                   "Found ts=%" PRIu64 " on node %u, ts=%" PRIu64 " on node %u",
                                   reply.getDocumentId().toString().c_str(),
                                   reply.getBucket().toString().c_str(),
                                   _results[i].oldTs, _results[i].nodeId,
                                   _results[goodNode].oldTs, _results[goodNode].nodeId);
                        _metrics.diverging_timestamp_updates.inc();

                        replyToSend.setNodeWithNewestTimestamp(_results[goodNode].nodeId);
                        _newestTimestampLocation.first  = _results[goodNode].bucketId;
                        _newestTimestampLocation.second = _results[goodNode].nodeId;

                        LOG(warning, "Bucket info prior to update operation was: %s. After update, "
                                     "info on node %u is %s, info on node %u is %s",
                                     _infoAtSendTime.toString().c_str(),
                                     _results[i].nodeId, _results[i].bucketInfo.toString().c_str(),
                                     _results[goodNode].nodeId, _results[goodNode].bucketInfo.toString().c_str());
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
UpdateOperation::onClose(DistributorMessageSender& sender)
{
    _tracker.fail(sender, api::ReturnCode(api::ReturnCode::ABORTED, "Process is shutting down"));
}
