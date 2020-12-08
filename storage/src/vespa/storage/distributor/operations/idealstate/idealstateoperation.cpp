// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "idealstateoperation.h"
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storage/distributor/idealstatemetricsset.h>
#include <vespa/storage/distributor/distributor_bucket_space_repo.h>
#include <vespa/storage/distributor/operation_sequencer.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.operation");

using namespace storage;
using namespace storage::distributor;
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
    if (_manager != NULL) {
        if (ok()) {
            _manager->getMetrics().operations[getType()]->ok.inc(1);
        } else {
            _manager->getMetrics().operations[getType()]->failed.inc(1);
        }
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

std::string
IdealStateOperation::toXML(framework::Clock& clock) const
{
    std::ostringstream ost;

    ost << "<operation bucketid=\"" << getBucketId()
        << "\" reason=\"" << _detailedReason << "\" operations=\"";

    ost << getName() << "[";
    for (uint32_t j = 0; j < getNodes().size(); j++) {
        if (j != 0) {
            ost << ",";
        }
        ost << getNodes()[j];
    }
    ost << "]";

    if (getStartTime().isSet()) {
        uint64_t timeSpent(
                (clock.getTimeInMillis() - getStartTime()).getTime());
        ost << "\" runtime_secs=\"" << timeSpent << "\"";
    } else {
        ost << "\"";
    }

    ost << "/>";
    return ost.str();
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
        (void) node;
        if (op.shouldBlockThisOperation(messageType, priority)) {
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

bool
checkNullBucketRequestBucketInfoMessage(uint16_t node,
                                        document::BucketSpace bucketSpace,
                                        const PendingMessageTracker& tracker)
{
    RequestBucketInfoChecker rchk;
    // Check messages sent to null-bucket (i.e. any bucket) for the node.
    document::Bucket nullBucket(bucketSpace, document::BucketId());
    tracker.checkPendingMessages(node, nullBucket, rchk);
    return rchk.blocked;
}

}

bool
IdealStateOperation::checkBlock(const document::Bucket &bucket,
                                const PendingMessageTracker& tracker,
                                const OperationSequencer& seq) const
{
    if (seq.is_blocked(bucket)) {
        return true;
    }
    IdealStateOpChecker ichk(*this);
    const std::vector<uint16_t>& nodes(getNodes());
    for (auto node : nodes) {
        tracker.checkPendingMessages(node, bucket, ichk);
        if (ichk.blocked) {
            return true;
        }
        if (checkNullBucketRequestBucketInfoMessage(node, bucket.getBucketSpace(), tracker)) {
            return true;
        }
    }
    return false;
}

bool
IdealStateOperation::checkBlockForAllNodes(
        const document::Bucket &bucket,
        const PendingMessageTracker& tracker,
        const OperationSequencer& seq) const
{
    if (seq.is_blocked(bucket)) {
        return true;
    }
    IdealStateOpChecker ichk(*this);
    // Check messages sent to _any node_ for _this_ particular bucket.
    tracker.checkPendingMessages(bucket, ichk);
    if (ichk.blocked) {
        return true;
    }
    const std::vector<uint16_t>& nodes(getNodes());
    for (auto node : nodes) {
        if (checkNullBucketRequestBucketInfoMessage(node, bucket.getBucketSpace(), tracker)) {
            return true;
        }
    }
    return false;
}


bool
IdealStateOperation::isBlocked(const PendingMessageTracker& tracker, const OperationSequencer& op_seq) const
{
    return checkBlock(getBucket(), tracker, op_seq);
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
                                              uint8_t) const
{
    for (uint32_t i = 0; MAINTENANCE_MESSAGE_TYPES[i] != 0; ++i) {
        if (messageType == MAINTENANCE_MESSAGE_TYPES[i]) {
            return true;
        }
    }

    return false;
}
