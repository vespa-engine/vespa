// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "idealstateoperation.h"
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storage/distributor/idealstatemetricsset.h>
#include <vespa/storage/distributor/distributor_bucket_space_repo.h>
#include <vespa/storage/distributor/operation_sequencer.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.operation");

namespace storage::distributor {

using document::BucketSpace;

const uint32_t IdealStateOperation::MAINTENANCE_MESSAGE_TYPES[] =
{
    api::MessageType::CREATEBUCKET_ID,
    api::MessageType::MERGEBUCKET_ID,
    api::MessageType::DELETEBUCKET_ID,
    api::MessageType::SPLITBUCKET_ID,
    api::MessageType::JOINBUCKETS_ID,
    api::MessageType::SETBUCKETSTATE_ID,
    0
};

IdealStateOperation::IdealStateOperation(const BucketAndNodes& bucketAndNodes)
    : _manager(nullptr),
      _bucketSpace(nullptr),
      _bucketAndNodes(bucketAndNodes),
      _ok(true),
      _priority(255)
{
}

IdealStateOperation::~IdealStateOperation() = default;

BucketAndNodes::BucketAndNodes(const document::Bucket &bucket, uint16_t node)
    : _bucket(bucket)
{
    _nodes.push_back(node);
}

BucketAndNodes::BucketAndNodes(const document::Bucket &bucket,
                               const std::vector<uint16_t>& nodes)
    : _bucket(bucket),
      _nodes(nodes)
{
    assert(!nodes.empty());
    std::sort(_nodes.begin(), _nodes.end());
}

void
BucketAndNodes::setBucketId(const document::BucketId &id)
{
    document::Bucket newBucket(_bucket.getBucketSpace(), id);
    _bucket = newBucket;
}

std::string
BucketAndNodes::toString() const
{
    std::ostringstream ost;

    ost << "[";

    for (uint32_t i = 0; i < _nodes.size(); i++) {
        if (i != 0) {
            ost << ",";
        }
        ost << _nodes[i];
    }

    ost <<  "] ";
    ost <<  _bucket.toString();
    return ost.str();
}

void
IdealStateOperation::setIdealStateManager(IdealStateManager* manager) {
    _manager = manager;
    _bucketSpace = &_manager->getBucketSpaceRepo().get(getBucket().getBucketSpace());
};

void
IdealStateOperation::done()
{
    if (_manager) {
        if (ok()) {
            _manager->getMetrics().operations[getType()]->ok.inc(1);
        } else {
            _manager->getMetrics().operations[getType()]->failed.inc(1);
        }
    }
}

void
IdealStateOperation::on_blocked()
{
    if (_manager) {
        _manager->getMetrics().operations[getType()]->blocked.inc(1);
    }
}

void
IdealStateOperation::on_throttled()
{
    if (_manager) {
        _manager->getMetrics().operations[getType()]->throttled.inc(1);
    }
}

uint32_t
IdealStateOperation::memorySize() const
{
   return sizeof(*this) + _detailedReason.size();
}

void
IdealStateOperation::setCommandMeta(api::MaintenanceCommand& cmd) const
{
    cmd.setPriority(_priority);
    cmd.setReason(_detailedReason);
}

namespace {

class IdealStateOpChecker : public PendingMessageTracker::Checker
{
public:
    bool blocked;
    const IdealStateOperation& op;

    IdealStateOpChecker(const IdealStateOperation& o)
        : blocked(false), op(o)
    {
    }

    bool check(uint32_t messageType, uint16_t node, uint8_t priority) override
    {
        if (op.shouldBlockThisOperation(messageType, node, priority)) {
            blocked = true;
            return false;
        }

        return true;
    }
};

class RequestBucketInfoChecker : public PendingMessageTracker::Checker
{
public:
    bool blocked;

    RequestBucketInfoChecker()
        : blocked(false)
    {
    }

    bool check(uint32_t messageType, uint16_t node, uint8_t priority) override
    {
        (void) node;
        (void) priority;
        // Always block for RequestBucketInfo pending to a node involved
        // in the ideal state operation.
        if (messageType == api::MessageType::REQUESTBUCKETINFO_ID) {
            blocked = true;
            return false;
        }
        return true;
    }
};

}

bool
IdealStateOperation::checkBlock(const document::Bucket &bucket,
                                const DistributorStripeOperationContext& ctx,
                                const OperationSequencer& seq) const
{
    if (seq.is_blocked(bucket)) {
        return true;
    }
    if (ctx.pending_cluster_state_or_null(bucket.getBucketSpace())) {
        return true;
    }
    IdealStateOpChecker ichk(*this);
    const std::vector<uint16_t>& nodes(getNodes());
    for (auto node : nodes) {
        ctx.pending_message_tracker().checkPendingMessages(node, bucket, ichk);
        if (ichk.blocked) {
            return true;
        }
    }
    return false;
}

bool
IdealStateOperation::checkBlockForAllNodes(
        const document::Bucket &bucket,
        const DistributorStripeOperationContext& ctx,
        const OperationSequencer& seq) const
{
    if (seq.is_blocked(bucket)) {
        return true;
    }
    if (ctx.pending_cluster_state_or_null(bucket.getBucketSpace())) {
        return true;
    }
    IdealStateOpChecker ichk(*this);
    // Check messages sent to _any node_ for _this_ particular bucket.
    ctx.pending_message_tracker().checkPendingMessages(bucket, ichk);
    return ichk.blocked;
}

bool
IdealStateOperation::isBlocked(const DistributorStripeOperationContext& ctx, const OperationSequencer& op_seq) const
{
    return checkBlock(getBucket(), ctx, op_seq);
}

std::string
IdealStateOperation::toString() const
{
    std::ostringstream ost;
    ost << getName() << " to " << _bucketAndNodes.toString()
        << " (pri " << (int)_priority << ")";

    return ost.str();
}

bool
IdealStateOperation::shouldBlockThisOperation(uint32_t messageType,
                                              [[maybe_unused]] uint16_t node,
                                              uint8_t) const
{
    for (uint32_t i = 0; MAINTENANCE_MESSAGE_TYPES[i] != 0; ++i) {
        if (messageType == MAINTENANCE_MESSAGE_TYPES[i]) {
            return true;
        }
    }
    // Also block on pending bucket-specific RequestBucketInfo since this usually
    // means there's a semi-completed merge being processed for the bucket, but
    // there will not be a pending merge command for it at the time.
    if (messageType == api::MessageType::REQUESTBUCKETINFO_ID) {
        return true;
    }

    return false;
}

}
