// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "joinoperation.h"
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/idealstatemanager.h>
#include <climits>
#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".distributor.operation.idealstate.join");

using namespace storage::distributor;

JoinOperation::JoinOperation(const ClusterContext &clusterName,
                             const BucketAndNodes& nodes,
                             const std::vector<document::BucketId>& bucketsToJoin)
    : IdealStateOperation(nodes),
      _tracker(clusterName),
      _bucketsToJoin(bucketsToJoin)
{}

JoinOperation::~JoinOperation() = default;

void
JoinOperation::onStart(DistributorMessageSender& sender)
{
    _ok = false;

    if (_bucketsToJoin.size() == 1) {
        LOG(debug, "Starting join operation for %s -> %s",
            _bucketsToJoin[0].toString().c_str(), getBucketId().toString().c_str());
    } else {
        LOG(debug, "Starting join operation for (%s,%s) -> %s",
            _bucketsToJoin[0].toString().c_str(),
            _bucketsToJoin[1].toString().c_str(),
            getBucketId().toString().c_str());
    }

    std::sort(_bucketsToJoin.begin(), _bucketsToJoin.end());

    auto nodeToBuckets = resolveSourceBucketsPerTargetNode();
    fillMissingSourceBucketsForInconsistentJoins(nodeToBuckets);

    _ok = enqueueJoinMessagePerTargetNode(nodeToBuckets);

    if (!_ok) {
        LOGBP(debug, "Unable to join bucket %s, since no copies are available (some in maintenance?)", getBucketId().toString().c_str());
        done();
    } else {
        _tracker.flushQueue(sender);
    }
}

JoinOperation::NodeToBuckets
JoinOperation::resolveSourceBucketsPerTargetNode() const
{
    NodeToBuckets nodeToBuckets;
    const auto& db(_bucketSpace->getBucketDatabase());
    for (const auto& bucket : _bucketsToJoin) {
        BucketDatabase::Entry entry(db.get(bucket));

        for (uint32_t j = 0; j < entry->getNodeCount(); j++) {
            nodeToBuckets[entry->getNodeRef(j).getNode()].push_back(bucket);
        }
    }
    return nodeToBuckets;
}

void
JoinOperation::fillMissingSourceBucketsForInconsistentJoins(
        NodeToBuckets& nodeToBuckets) const
{
    for (auto& node : nodeToBuckets) {
        if (node.second.size() == 1) {
            document::BucketId source = node.second.front();
            node.second.push_back(source);
        }
    }
}

bool
JoinOperation::enqueueJoinMessagePerTargetNode(
        const NodeToBuckets& nodeToBuckets)
{
    if (nodeToBuckets.empty()) {
        return false;
    }
    for (const auto& node : nodeToBuckets) {
        std::shared_ptr<api::JoinBucketsCommand> msg(
                new api::JoinBucketsCommand(getBucket()));
        msg->getSourceBuckets() = node.second;
        msg->setTimeout(vespalib::duration::max());
        setCommandMeta(*msg);
        _tracker.queueCommand(msg, node.first);
    }
    return true;
}

void
JoinOperation::onReceive(DistributorMessageSender&, const api::StorageReply::SP& msg)
{
    api::JoinBucketsReply& rep = static_cast<api::JoinBucketsReply&>(*msg);
    uint16_t node = _tracker.handleReply(rep);
    if (node == 0xffff) {
        LOG(debug, "Ignored reply since node was max uint16_t for unknown reasons");
        return;
    }

    if (rep.getResult().success()) {
        const std::vector<document::BucketId>& sourceBuckets(
                rep.getSourceBuckets());
        for (uint32_t i = 0; i < sourceBuckets.size(); i++) {
            document::Bucket sourceBucket(msg->getBucket().getBucketSpace(), sourceBuckets[i]);
            _manager->getDistributorComponent().removeNodeFromDB(sourceBucket, node);
        }

        // Add new buckets.
        if (!rep.getBucketInfo().valid()) {
            LOG(debug, "Invalid bucketinfo for bucket %s returned in join",
                getBucketId().toString().c_str());
        } else {
            _manager->getDistributorComponent().updateBucketDatabase(
                    getBucket(),
                    BucketCopy(_manager->getDistributorComponent().getUniqueTimestamp(),
                               node,
                               rep.getBucketInfo()),
                    DatabaseUpdate::CREATE_IF_NONEXISTING);

            LOG(spam, "Adding joined bucket %s", getBucketId().toString().c_str());
        }
    } else if (rep.getResult().getResult() == api::ReturnCode::BUCKET_NOT_FOUND
            && _bucketSpace->getBucketDatabase().get(getBucketId())->getNode(node) != 0)
    {
        _manager->getDistributorComponent().recheckBucketInfo(node, getBucket());
        LOGBP(warning, "Join failed to find %s: %s",
              getBucketId().toString().c_str(),
              rep.getResult().toString().c_str());
    } else if (rep.getResult().isBusy()) {
        LOG(debug, "Join failed for %s, node was busy. Will retry later",
            getBucketId().toString().c_str());
    } else if (rep.getResult().isCriticalForMaintenance()) {
        LOGBP(warning, "Join failed for %s: %s with error '%s'",
              getBucketId().toString().c_str(), msg->toString().c_str(),
              msg->getResult().toString().c_str());
    } else {
        LOG(debug, "Join failed for %s with non-critical failure: %s",
            getBucketId().toString().c_str(),
            rep.getResult().toString().c_str());
    }
    _ok = rep.getResult().success();

    LOG(debug, "Bucket %s join finished", getBucketId().toString().c_str());
    if (_tracker.finished()) {
        done();
    }
}

document::Bucket
JoinOperation::getJoinBucket(size_t idx) const
{
    return document::Bucket(getBucket().getBucketSpace(), _bucketsToJoin[idx]);
}

bool
JoinOperation::isBlocked(const PendingMessageTracker& tracker, const OperationSequencer& op_seq) const
{
    return (checkBlock(getBucket(), tracker, op_seq) ||
            checkBlock(getJoinBucket(0), tracker, op_seq) ||
            (_bucketsToJoin.size() > 1 && checkBlock(getJoinBucket(1), tracker, op_seq)));
}

