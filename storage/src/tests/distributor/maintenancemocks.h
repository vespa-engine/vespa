// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/maintenance/maintenanceprioritygenerator.h>
#include <vespa/storage/distributor/maintenance/maintenanceoperationgenerator.h>
#include <vespa/storage/distributor/operationstarter.h>
#include <vespa/storage/distributor/operations/operation.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <sstream>

namespace storage {
namespace distributor {

class MockMaintenancePriorityGenerator
    : public MaintenancePriorityGenerator
{
    MaintenancePriorityAndType prioritize(
            const document::BucketId&,
            NodeMaintenanceStatsTracker& stats) const override
    {
        stats.incMovingOut(1);
        stats.incCopyingIn(2);
        return MaintenancePriorityAndType(
                MaintenancePriority(MaintenancePriority::VERY_HIGH),
                MaintenanceOperation::MERGE_BUCKET);
    }
};


class MockOperation : public MaintenanceOperation
{
    document::BucketId _bucketId;
    std::string _reason;
    bool _shouldBlock;
public:
    MockOperation(const document::BucketId& bucketId)
        : _bucketId(bucketId),
          _shouldBlock(false)
    {}

    std::string toString() const override {
        return _bucketId.toString();
    }

    void onClose(DistributorMessageSender&) override {}
    const char* getName() const override { return "MockOperation"; }
    const std::string& getDetailedReason() const override {
        return _reason;
    }
    void onStart(DistributorMessageSender&) override {}
    void onReceive(DistributorMessageSender&, const std::shared_ptr<api::StorageReply>&) override {}
    bool isBlocked(const PendingMessageTracker&) const override {
        return _shouldBlock;
    }
    void setShouldBlock(bool shouldBlock) {
        _shouldBlock = shouldBlock;
    }
};

class MockMaintenanceOperationGenerator
    : public MaintenanceOperationGenerator
{
public:
    MaintenanceOperation::SP generate(const document::BucketId& id) const override {
        return MaintenanceOperation::SP(new MockOperation(id));
    }

    std::vector<MaintenanceOperation::SP> generateAll(
            const document::BucketId& id,
            NodeMaintenanceStatsTracker& tracker) const override
    {
        (void) tracker;
        std::vector<MaintenanceOperation::SP> ret;
        ret.push_back(MaintenanceOperation::SP(new MockOperation(id)));
        return ret;
    }

};

class MockOperationStarter
    : public OperationStarter
{
    std::ostringstream _started;
    std::vector<Operation::SP> _operations;
    bool _shouldStart;
public:
    MockOperationStarter()
        : _shouldStart(true)
    {}

    bool start(const std::shared_ptr<Operation>& operation, Priority priority) override
    {
        if (_shouldStart) {
            _started << operation->toString()
                     << ", pri " << static_cast<int>(priority)
                     << "\n";
            _operations.push_back(operation);
        }
        return _shouldStart;
    }

    void setShouldStartOperations(bool shouldStart) {
        _shouldStart = shouldStart;
    }

    std::vector<Operation::SP>& getOperations() {
        return _operations;
    }

    std::string toString() const {
        return _started.str();
    }
};

}
}

