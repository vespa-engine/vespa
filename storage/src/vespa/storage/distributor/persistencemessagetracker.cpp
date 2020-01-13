// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistencemessagetracker.h"
#include <vespa/storage/common/vectorprinter.h>
#include <vespa/storage/common/bucketoperationlogger.h>
#include <vespa/storageapi/message/persistence.h>
#include "distributor_bucket_space_repo.h"
#include "distributor_bucket_space.h"

#include <vespa/log/log.h>

LOG_SETUP(".persistencemessagetracker");

namespace storage::distributor {

PersistenceMessageTrackerImpl::PersistenceMessageTrackerImpl(
        PersistenceOperationMetricSet& metric,
        std::shared_ptr<api::BucketInfoReply> reply,
        DistributorComponent& link,
        api::Timestamp revertTimestamp)
    : MessageTracker(link.getClusterName()),
      _metric(metric),
      _reply(reply),
      _manager(link),
      _revertTimestamp(revertTimestamp),
      _requestTimer(link.getClock()),
      _priority(reply->getPriority()),
      _success(true)
{
}

PersistenceMessageTrackerImpl::~PersistenceMessageTrackerImpl() {}

void
PersistenceMessageTrackerImpl::updateDB()
{
    for (BucketInfoMap::iterator iter = _bucketInfo.begin();
         iter != _bucketInfo.end();
         iter++)
    {
        _manager.updateBucketDatabase(iter->first, iter->second);
    }

    for (BucketInfoMap::iterator iter = _remapBucketInfo.begin();
         iter != _remapBucketInfo.end();
         iter++)
    {
        _manager.updateBucketDatabase(iter->first, iter->second,
                                      DatabaseUpdate::CREATE_IF_NONEXISTING);
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

        for (uint32_t i = 0; i < revertNodes.size(); i++) {
            std::shared_ptr<api::RevertCommand> toRevert(
                    new api::RevertCommand(revertNodes[i].first, reverts));
            toRevert->setPriority(_priority);
            queueCommand(toRevert, revertNodes[i].second);
        }

        flushQueue(sender);
    }
}

void
PersistenceMessageTrackerImpl::queueMessageBatch(const std::vector<MessageTracker::ToSend>& messages) {
    _messageBatches.push_back(MessageBatch());
    for (uint32_t i = 0; i < messages.size(); i++) {
        if (_reply.get()) {
            messages[i]._msg->getTrace().setLevel(_reply->getTrace().getLevel());
        }

        _messageBatches.back().push_back(messages[i]._msg->getMsgId());
        queueCommand(messages[i]._msg, messages[i]._target);
    }
}

bool
PersistenceMessageTrackerImpl::canSendReplyEarly() const
{
    if (!_reply.get() || !_reply->getResult().success()) {
        LOG(spam, "Can't return early because we have already replied or failed");
        return false;
    }
    auto &bucketSpaceRepo(_manager.getBucketSpaceRepo());
    auto &bucketSpace(bucketSpaceRepo.get(_reply->getBucket().getBucketSpace()));
    const lib::Distribution& distribution = bucketSpace.getDistribution();

    if (distribution.getInitialRedundancy() == 0) {
        LOG(spam, "Not returning early because initial redundancy wasn't set");
        return false;
    }

    for (uint32_t i = 0; i < _messageBatches.size(); i++) {
        uint32_t messagesDone = 0;

        for (uint32_t j = 0; j < _messageBatches[i].size(); j++) {
            if (_sentMessages.find(_messageBatches[i][j]) == _sentMessages.end()) {
                messagesDone++;
            } else if (distribution.ensurePrimaryPersisted() && j == 0) {
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
PersistenceMessageTrackerImpl::checkCopiesDeleted()
{
    if (!_reply.get()) {
        return;
    }

    // Don't check the buckets that have been remapped here, as we will
    // create them.
    const auto &bucketSpaceRepo(_manager.getBucketSpaceRepo());
    for (BucketInfoMap::const_iterator iter = _bucketInfo.begin();
         iter != _bucketInfo.end();
         iter++)
    {
        const auto &bucketSpace(bucketSpaceRepo.get(iter->first.getBucketSpace()));
        const auto &bucketDb(bucketSpace.getBucketDatabase());
        BucketDatabase::Entry dbentry = bucketDb.get(iter->first.getBucketId());

        if (!dbentry.valid()) {
            continue;
        }

        std::vector<uint16_t> missing;
        std::vector<uint16_t> total;

        for (uint32_t i = 0; i < iter->second.size(); ++i) {
            if (dbentry->getNode(iter->second[i].getNode()) == NULL) {
                missing.push_back(iter->second[i].getNode());
            }

            total.push_back(iter->second[i].getNode());
        }

        if (!missing.empty()) {
            std::ostringstream msg;
            msg << iter->first.toString() << " was deleted from nodes ["
                << commaSeparated(missing)
                << "] after message was sent but before it was done. Sent to ["
                << commaSeparated(total)
                << "]";

            LOG(debug, "%s", msg.str().c_str());
            _reply->setResult(api::ReturnCode(api::ReturnCode::BUCKET_DELETED,
                                              msg.str()));
            break;
        }
    }
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
        _remapBucketInfo[bucket].push_back(
                BucketCopy(_manager.getUniqueTimestamp(),
                           node,
                           bucketInfo));
    } else {
        LOG(debug, "Bucket %s: Received bucket info %s from node %d",
            bucket.toString().c_str(),
            bucketInfo.toString().c_str(),
            node);
        _bucketInfo[bucket].push_back(
                BucketCopy(_manager.getUniqueTimestamp(),
                           node,
                           bucketInfo));
    }
}

void
PersistenceMessageTrackerImpl::logSuccessfulReply(uint16_t node, 
                                              const api::BucketInfoReply& reply) const
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
    return _manager.getDistributorConfig().enableRevert
            && _revertNodes.size() && !_success && _reply.get();
}

void
PersistenceMessageTrackerImpl::sendReply(MessageSender& sender)
{
    updateMetrics();
    _trace.setStrict(false);
    _reply->getTrace().getRoot().addChild(_trace);
    
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
        _manager.removeNodeFromDB(reply.getBucket(), node);
        LOG_BUCKET_OPERATION_NO_LOCK(
                reply.getBucketId(),
                vespalib::make_string(
                    "Deleted bucket on node %u due to failing create bucket %s",
                    node, reply.getResult().toString().c_str()));
    }
}

void
PersistenceMessageTrackerImpl::handlePersistenceReply(
        api::BucketInfoReply& reply,
        uint16_t node)
{
    if (reply.getBucketInfo().valid()) {
        addBucketInfoFromReply(node, reply);
    }
    if (reply.getResult().success()) {
        logSuccessfulReply(node, reply);
        _revertNodes.emplace_back(reply.getBucket(), node);
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
    _trace.addChild(reply.getTrace().getRoot());

    if (reply.getType() == api::MessageType::CREATEBUCKET_REPLY) {
        handleCreateBucketReply(reply, node);
    } else {
        handlePersistenceReply(reply, node);
    }

    if (finished()) {
        bool doRevert(shouldRevert());

        checkCopiesDeleted();
        updateDB();

        if (!hasSentReply()) {
            sendReply(sender);
        }
        if (doRevert) {
            revert(sender, _revertNodes);
        }
    } else if (canSendReplyEarly()) {
        LOG(debug, "Sending reply early because initial redundancy has been reached");
        checkCopiesDeleted();
        sendReply(sender);
    }
}

}
