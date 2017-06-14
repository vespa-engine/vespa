// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "idealstateoperation.h"
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storage/distributor/idealstatemetricsset.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storageapi/messageapi/maintenancecommand.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.operation");

using namespace storage;
using namespace storage::distributor;

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
        : _manager(NULL),
          _bucketAndNodes(bucketAndNodes),
          _ok(true),
          _priority(255)
{
}

IdealStateOperation::~IdealStateOperation()
{
}

BucketAndNodes::BucketAndNodes(const document::BucketId& id, uint16_t node)
    : _id(id)
{
    _nodes.push_back(node);
}

BucketAndNodes::BucketAndNodes(const document::BucketId& id,
                               const std::vector<uint16_t>& nodes)
    : _id(id),
      _nodes(nodes)
{
    assert(!nodes.empty());
    std::sort(_nodes.begin(), _nodes.end());
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
    ost <<  _id;
    return ost.str();
}

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
    cmd.setLoadType(
            (*_manager->getLoadTypes())["maintenance"]);
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

}

bool
IdealStateOperation::checkBlock(const document::BucketId& bId,
                                const PendingMessageTracker& tracker) const
{
    IdealStateOpChecker ichk(*this);
    RequestBucketInfoChecker rchk;
    const std::vector<uint16_t>& nodes(getNodes());
    for (size_t i = 0; i < nodes.size(); ++i) {
        tracker.checkPendingMessages(nodes[i], bId, ichk);
        if (ichk.blocked) {
            return true;
        }
        // Check messages sent to null-bucket (i.e. any bucket) for the node.
        tracker.checkPendingMessages(nodes[i], document::BucketId(), rchk);
        if (rchk.blocked) {
            return true;
        }
    }
    return false;
}

bool
IdealStateOperation::checkBlockForAllNodes(
        const document::BucketId& bid,
        const PendingMessageTracker& tracker) const
{
    IdealStateOpChecker ichk(*this);
    // Check messages sent to _any node_ for _this_ particular bucket.
    tracker.checkPendingMessages(bid, ichk);
    if (ichk.blocked) {
        return true;
    }
    RequestBucketInfoChecker rchk;
    // Check messages sent to null-bucket (i.e. _any bucket_) for the node.
    const std::vector<uint16_t>& nodes(getNodes());
    for (size_t i = 0; i < nodes.size(); ++i) {
        tracker.checkPendingMessages(nodes[i], document::BucketId(), rchk);
        if (rchk.blocked) {
            return true;
        }
    }
    return false;
}


bool
IdealStateOperation::isBlocked(const PendingMessageTracker& tracker) const
{
    return checkBlock(getBucketId(), tracker);
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
