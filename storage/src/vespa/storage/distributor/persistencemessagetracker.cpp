// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistencemessagetracker.h"
#include "distributor_bucket_space_repo.h"
#include "distributor_bucket_space.h"
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/storage/common/vectorprinter.h>
#include <vespa/storageapi/message/persistence.h>

#include <vespa/log/log.h>
LOG_SETUP(".persistencemessagetracker");

namespace storage::distributor {

PersistenceMessageTrackerImpl::PersistenceMessageTrackerImpl(
        PersistenceOperationMetricSet& metric,
        std::shared_ptr<api::BucketInfoReply> reply,
        const DistributorNodeContext& node_ctx,
        DistributorStripeOperationContext& op_ctx,
        api::Timestamp revertTimestamp)
    : MessageTracker(node_ctx),
      _metric(metric),
      _reply(std::move(reply)),
      _op_ctx(op_ctx),
      _revertTimestamp(revertTimestamp),
      _trace(_reply->getTrace().getLevel()),
      _requestTimer(node_ctx.clock()),
      _n_persistence_replies_total(0),
      _n_successful_persistence_replies(0),
      _priority(_reply->getPriority()),
      _success(true)
{
}

PersistenceMessageTrackerImpl::~PersistenceMessageTrackerImpl() = default;

void
PersistenceMessageTrackerImpl::updateDB()
{
    for (const auto & entry : _bucketInfo) {
        _op_ctx.update_bucket_database(entry.first, entry.second);
    }

    for (const auto & entry :  _remapBucketInfo){
        _op_ctx.update_bucket_database(entry.first, entry.second, DatabaseUpdate::CREATE_IF_NONEXISTING);
    }
}

void
PersistenceMessageTrackerImpl::updateMetrics()
{
    const api::ReturnCode& result(_reply->getResult());
    _metric.updateFromResult(result);
    _metric.latency.addValue(_requestTimer.getElapsedTimeAsDouble());
}

void
PersistenceMessageTrackerImpl::fail(MessageSender& sender, const api::ReturnCode& result) {
    if (_reply.get()) {
        _reply->setResult(result);
        updateMetrics();
        sender.sendReply(_reply);
        _reply.reset();
    }
}

uint16_t
PersistenceMessageTrackerImpl::receiveReply(
        MessageSender& sender,
        api::BucketInfoReply& reply)
{
    uint16_t node = handleReply(reply);

    if (node != (uint16_t)-1) {
        updateFromReply(sender, reply, node);
    }

    return node;
}

void
PersistenceMessageTrackerImpl::revert(
        MessageSender& sender,
        const std::vector<BucketNodePair>& revertNodes)
{
    if (_revertTimestamp != 0) {
        // Since we're reverting, all received bucket info is voided.
        _bucketInfo.clear();

        std::vector<api::Timestamp> reverts;
        reverts.push_back(_revertTimestamp);

        for (const auto & revertNode : revertNodes) {
            auto toRevert = std::make_shared<api::RevertCommand>(revertNode.first, reverts);
            toRevert->setPriority(_priority);
            queueCommand(std::move(toRevert), revertNode.second);
        }

        flushQueue(sender);
    }
}

void
PersistenceMessageTrackerImpl::queueMessageBatch(const std::vector<MessageTracker::ToSend>& messages) {
    _messageBatches.emplace_back();
    for (const auto & message : messages) {
        if (_reply) {
            message._msg->getTrace().setLevel(_reply->getTrace().getLevel());
        }

        _messageBatches.back().push_back(message._msg->getMsgId());
        queueCommand(message._msg, message._target);
    }
}

bool
PersistenceMessageTrackerImpl::canSendReplyEarly() const
{
    if (!_reply.get() || !_reply->getResult().success()) {
        LOG(spam, "Can't return early because we have already replied or failed");
        return false;
    }
    auto &bucketSpaceRepo(_op_ctx.bucket_space_repo());
    auto &bucketSpace(bucketSpaceRepo.get(_reply->getBucket().getBucketSpace()));
    const lib::Distribution& distribution = bucketSpace.getDistribution();

    if (distribution.getInitialRedundancy() == 0) {
        LOG(spam, "Not returning early because initial redundancy wasn't set");
        return false;
    }

    for (const MessageBatch & batch : _messageBatches) {
        uint32_t messagesDone = 0;

        for (uint32_t i = 0; i < batch.size(); i++) {
            if (_sentMessages.find(batch[i]) == _sentMessages.end()) {
                messagesDone++;
            } else if (distribution.ensurePrimaryPersisted() && i == 0) {
                // Primary must always be written.
                LOG(debug, "Not returning early because primary node wasn't done");
                return false;
            }
        }

        if (messagesDone < distribution.getInitialRedundancy()) {
            LOG(spam, "Not returning early because only %d messages out of %d are done",
                messagesDone, distribution.getInitialRedundancy());
            return false;
        }
    }

    return true;
}

void
PersistenceMessageTrackerImpl::addBucketInfoFromReply(
        uint16_t node,
        const api::BucketInfoReply& reply)
{
    document::Bucket bucket(reply.getBucket());
    const api::BucketInfo& bucketInfo(reply.getBucketInfo());

    if (reply.hasBeenRemapped()) {
        LOG(debug, "Bucket %s: Received remapped bucket info %s from node %d",
            bucket.toString().c_str(),
            bucketInfo.toString().c_str(),
            node);
        _remapBucketInfo[bucket].emplace_back(_op_ctx.generate_unique_timestamp(), node, bucketInfo);
    } else {
        LOG(debug, "Bucket %s: Received bucket info %s from node %d",
            bucket.toString().c_str(),
            bucketInfo.toString().c_str(),
            node);
        _bucketInfo[bucket].emplace_back(_op_ctx.generate_unique_timestamp(), node, bucketInfo);
    }
}

void
PersistenceMessageTrackerImpl::logSuccessfulReply(uint16_t node, const api::BucketInfoReply& reply) const
{
    LOG(spam, "Bucket %s: Received successful reply %s",
        reply.getBucketId().toString().c_str(),
        reply.toString().c_str());
    
    if (!reply.getBucketInfo().valid()) {
        LOG(error,
            "Reply %s from node %d contained invalid bucket "
            "information %s. This is a bug! Please report "
            "this to the Vespa team",
            reply.toString().c_str(),
            node,
            reply.getBucketInfo().toString().c_str());
    }
}

bool
PersistenceMessageTrackerImpl::shouldRevert() const
{
    return _op_ctx.distributor_config().enable_revert()
            &&  !_revertNodes.empty() && !_success && _reply;
}

bool PersistenceMessageTrackerImpl::has_majority_successful_replies() const noexcept {
    // FIXME this has questionable interaction with early client ACK since we only count
    // the number of observed replies rather than the number of total requests sent.
    // ... but the early ACK-feature dearly needs a redesign anyway.
    return (_n_successful_persistence_replies >= (_n_persistence_replies_total / 2 + 1));
}

bool PersistenceMessageTrackerImpl::has_minority_test_and_set_failure() const noexcept {
    return ((_reply->getResult().getResult() == api::ReturnCode::TEST_AND_SET_CONDITION_FAILED)
            && has_majority_successful_replies());
}

void
PersistenceMessageTrackerImpl::sendReply(MessageSender& sender)
{
    // If we've observed _partial_ TaS failures but have had a majority of good ACKs,
    // treat the reply as successful. This is because the ACKed write(s) will eventually
    // become visible across all nodes.
    if (has_minority_test_and_set_failure()) {
        _reply->setResult(api::ReturnCode());
    }

    updateMetrics();
    if ( ! _trace.isEmpty()) {
        _trace.setStrict(false);
        _reply->getTrace().addChild(std::move(_trace));
    }
    
    sender.sendReply(_reply);
    _reply = std::shared_ptr<api::BucketInfoReply>();
}

void
PersistenceMessageTrackerImpl::updateFailureResult(const api::BucketInfoReply& reply)
{
    LOG(debug, "Bucket %s: Received failed reply %s with result %s",
        reply.getBucketId().toString().c_str(),
        reply.toString().c_str(),
        reply.getResult().toString().c_str());
    if (reply.getResult().getResult() >
        _reply->getResult().getResult())
    {
        _reply->setResult(reply.getResult());
    }
    
    _success = false;
}

void
PersistenceMessageTrackerImpl::handleCreateBucketReply(
        api::BucketInfoReply& reply,
        uint16_t node)
{
    LOG(spam, "Received CreateBucket reply for %s from node %u",
        reply.getBucketId().toString().c_str(), node);
    if (!reply.getResult().success()
        && reply.getResult().getResult() != api::ReturnCode::EXISTS)
    {
        LOG(spam, "Create bucket reply failed, so deleting it from bucket db");
        // We don't know if the bucket exists at this point, so we remove it from the DB.
        // If we get subsequent write load the bucket will be implicitly created again
        // (which is an idempotent operation) and all is well. But since we don't know _if_
        // we'll get any further write load we send a RequestBucketInfo to bring the bucket
        // back into the DB if it _was_ successfully created. We have to do the latter to
        // avoid the risk of introducing an orphaned bucket replica on the content node.
        _op_ctx.remove_node_from_bucket_database(reply.getBucket(), node);
        _op_ctx.recheck_bucket_info(node, reply.getBucket());
    }
}

void
PersistenceMessageTrackerImpl::handlePersistenceReply(
        api::BucketInfoReply& reply,
        uint16_t node)
{
    ++_n_persistence_replies_total;
    if (reply.getBucketInfo().valid()) {
        addBucketInfoFromReply(node, reply);
    }
    if (reply.getResult().success()) {
        logSuccessfulReply(node, reply);
        _revertNodes.emplace_back(reply.getBucket(), node);
        ++_n_successful_persistence_replies;
    } else if (!hasSentReply()) {
        updateFailureResult(reply);
    }
}

void
PersistenceMessageTrackerImpl::updateFromReply(
        MessageSender& sender,
        api::BucketInfoReply& reply,
        uint16_t node)
{
    _trace.addChild(reply.steal_trace());

    if (reply.getType() == api::MessageType::CREATEBUCKET_REPLY) {
        handleCreateBucketReply(reply, node);
    } else {
        handlePersistenceReply(reply, node);
    }

    if (finished()) {
        bool doRevert(shouldRevert());

        updateDB();

        if (!hasSentReply()) {
            sendReply(sender);
        }
        if (doRevert) {
            revert(sender, _revertNodes);
        }
    } else if (canSendReplyEarly()) {
        LOG(debug, "Sending reply early because initial redundancy has been reached");
        sendReply(sender);
    }
}

}
