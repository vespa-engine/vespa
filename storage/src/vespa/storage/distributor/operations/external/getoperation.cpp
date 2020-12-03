// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "getoperation.h"
#include <vespa/storage/distributor/distributor_node_context.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/vdslib/state/nodestate.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.callback.doc.get");

using document::BucketSpace;

namespace storage::distributor {

GetOperation::GroupId::GroupId(const document::BucketId& id, uint32_t checksum, int node)
    : _id(id),
      _checksum(checksum),
      _node(node)
{
}

bool
GetOperation::GroupId::operator<(const GroupId& other) const
{
    if (_id.getRawId() != other._id.getRawId()) {
        return (_id.getRawId() < other._id.getRawId());
    }
    if (_checksum != other._checksum) {
        return (_checksum < other._checksum);
    }
    if (_node != other._node) {
        return (_node < other._node);
    }
    return false;
}

bool
GetOperation::GroupId::operator==(const GroupId& other) const
{
    return (_id == other._id
            && _checksum == other._checksum
            && _node == other._node);
}

GetOperation::GetOperation(DistributorNodeContext& node_ctx,
                           const DistributorBucketSpace &bucketSpace,
                           std::shared_ptr<BucketDatabase::ReadGuard> read_guard,
                           std::shared_ptr<api::GetCommand> msg,
                           PersistenceOperationMetricSet& metric,
                           api::InternalReadConsistency desired_read_consistency)
    : Operation(),
      _node_ctx(node_ctx),
      _bucketSpace(bucketSpace),
      _msg(std::move(msg)),
      _returnCode(api::ReturnCode::OK),
      _doc(),
      _newest_replica(),
      _metric(metric),
      _operationTimer(node_ctx.clock()),
      _desired_read_consistency(desired_read_consistency),
      _has_replica_inconsistency(false),
      _any_replicas_failed(false)
{
    assignTargetNodeGroups(*read_guard);
}

void
GetOperation::onClose(DistributorMessageSender& sender)
{
    _returnCode = api::ReturnCode(api::ReturnCode::ABORTED, "Process is shutting down");
    sendReply(sender);
}

bool
GetOperation::copyIsOnLocalNode(const BucketCopy& copy) const
{
    return (copy.getNode() == _node_ctx.node_index());
}

int
GetOperation::findBestUnsentTarget(const GroupVector& candidates) const
{
    int best = -1;
    for (uint32_t i = 0; i < candidates.size(); ++i) {
        if (candidates[i].sent) {
            continue;
        }
        if (copyIsOnLocalNode(candidates[i].copy)) {
            return i; // Can't get better match than this.
        }
        if (best == -1) {
            best = i;
        }
    }
    return best;
}

bool
GetOperation::sendForChecksum(DistributorMessageSender& sender, const document::BucketId& id, GroupVector& res)
{
    const int best = findBestUnsentTarget(res);

    if (best != -1) {
        document::Bucket bucket(_msg->getBucket().getBucketSpace(), id);
        auto command = std::make_shared<api::GetCommand>(bucket, _msg->getDocumentId(),
                                                         _msg->getFieldSet(), _msg->getBeforeTimestamp());
        copyMessageSettings(*_msg, *command);
        command->set_internal_read_consistency(_desired_read_consistency);

        LOG(spam, "Sending %s to node %d", command->toString(true).c_str(), res[best].copy.getNode());

        const auto target_node = res[best].copy.getNode();
        res[best].sent = sender.sendToNode(lib::NodeType::STORAGE, target_node, command);
        res[best].to_node = target_node;
        return true;
    }

    return false;
}

void
GetOperation::onStart(DistributorMessageSender& sender)
{
    // Send one request for each unique group (BucketId/checksum)
    bool sent = false;
    for (auto& response : _responses) {
        sent |= sendForChecksum(sender, response.first.getBucketId(), response.second);
    }

    // If nothing was sent (no useful copies), just return NOT_FOUND
    if (!sent) {
        LOG(debug, "No useful bucket copies for get on document %s. Returning without document", _msg->getDocumentId().toString().c_str());
        sendReply(sender);
    }
};

void
GetOperation::onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply>& msg)
{
    auto* getreply = dynamic_cast<api::GetReply*>(msg.get());
    assert(getreply != nullptr);

    LOG(debug, "Received %s", msg->toString(true).c_str());

    _msg->getTrace().addChild(getreply->steal_trace());
    bool allDone = true;
    for (auto& response : _responses) {
        for (uint32_t i = 0; i < response.second.size(); i++) {
            const auto& bucket_id = response.first.getBucketId();
            auto& send_state = response.second[i];
            if (send_state.sent == getreply->getMsgId()) {
                LOG(debug, "Get on %s returned %s",
                    _msg->getDocumentId().toString().c_str(),
                    getreply->getResult().toString().c_str());

                send_state.received = true;
                send_state.returnCode = getreply->getResult();

                if (getreply->getResult().success()) {
                    if (_newest_replica.has_value() && (getreply->getLastModifiedTimestamp() != _newest_replica->timestamp)) {
                        // At least two document versions returned had different timestamps.
                        _has_replica_inconsistency = true; // This is a one-way toggle.
                    }
                    if (!_newest_replica.has_value() || getreply->getLastModifiedTimestamp() > _newest_replica->timestamp) {
                        _returnCode = getreply->getResult();
                        assert(response.second[i].to_node != UINT16_MAX);
                        _newest_replica = NewestReplica::of(getreply->getLastModifiedTimestamp(), bucket_id,
                                                            send_state.to_node, getreply->is_tombstone());
                        _doc = getreply->getDocument(); // May be empty (tombstones).
                    }
                } else {
                    _any_replicas_failed = true;
                    if (!_newest_replica.has_value()) {
                        _returnCode = getreply->getResult(); // Don't overwrite if we have a good response.
                    }
                    if (!all_bucket_metadata_initially_consistent()) {
                        // If we're sending to more than a single group of replicas it means our replica set is
                        // out of sync. Since we are unable to verify the timestamp of at least one replicated
                        // document, we fail safe by marking the entire operation as inconsistent.
                        _has_replica_inconsistency = true;
                    }

                    // Try to send to another node in this checksum group.
                    bool sent = sendForChecksum(sender, bucket_id, response.second);
                    if (sent) {
                        allDone = false;
                    }
                }
            }

            if (response.second[i].sent && !response.second[i].received) {
                LOG(spam, "Have not received all replies yet, setting allDone = false");
                allDone = false;
            }
        }
    }

    if (allDone) {
        LOG(debug, "Get on %s done, returning reply %s",
            _msg->getDocumentId().toString().c_str(), _returnCode.toString().c_str());
        sendReply(sender);
    }
}

void GetOperation::update_internal_metrics() {
    auto metric = _metric.locked();
    if (_returnCode.success()) {
        metric->ok.inc();
    } else if (_returnCode.getResult() == api::ReturnCode::TIMEOUT) {
        metric->failures.timeout.inc();
    } else if (_returnCode.isBusy()) {
        metric->failures.busy.inc();
    } else if (_returnCode.isNodeDownOrNetwork()) {
        metric->failures.notconnected.inc();
    } else {
        metric->failures.storagefailure.inc();
    }
    if (!_doc.get()) {
        metric->failures.notfound.inc();
    }
    metric->latency.addValue(_operationTimer.getElapsedTimeAsDouble());
}

void
GetOperation::sendReply(DistributorMessageSender& sender)
{
    if (_msg.get()) {
        const auto newest = _newest_replica.value_or(NewestReplica::make_empty());
        // If the newest entry is a tombstone (remove entry), the externally visible
        // behavior is as if the document was not found. In this case _doc will also
        // be empty. This means we also currently don't propagate tombstone status outside
        // of this operation (except via the newest_replica() functionality).
        const auto timestamp = (newest.is_tombstone ? api::Timestamp(0) : newest.timestamp);
        auto repl = std::make_shared<api::GetReply>(*_msg, _doc, timestamp, !_has_replica_inconsistency);
        repl->setResult(_returnCode);
        update_internal_metrics();
        sender.sendReply(repl);
        _msg.reset();
    }

}

void
GetOperation::assignTargetNodeGroups(const BucketDatabase::ReadGuard& read_guard)
{
    document::BucketIdFactory bucketIdFactory;
    document::BucketId bid = bucketIdFactory.getBucketId(_msg->getDocumentId());

    auto entries = read_guard.find_parents_and_self(bid);

    for (uint32_t j = 0; j < entries.size(); ++j) {
        const BucketDatabase::Entry& e = entries[j];

        LOG(spam, "Entry for %s: %s", e.getBucketId().toString().c_str(),
            e->toString().c_str());

        for (uint32_t i = 0; i < e->getNodeCount(); i++) {
            const BucketCopy& copy = e->getNodeRef(i);

            // TODO this could ideally be a set
            _replicas_in_db.emplace_back(e.getBucketId(), copy.getNode());

            if (!copy.valid()) {
                _responses[GroupId(e.getBucketId(), copy.getChecksum(), copy.getNode())].emplace_back(copy);
            } else if (!copy.empty()) {
                _responses[GroupId(e.getBucketId(), copy.getChecksum(), -1)].emplace_back(copy);
            }
        }
    }
}

bool
GetOperation::all_bucket_metadata_initially_consistent() const
{
    // TODO rename, calling this "responses" is confusing as it's populated before sending anything.
    return _responses.size() == 1;
}

}
