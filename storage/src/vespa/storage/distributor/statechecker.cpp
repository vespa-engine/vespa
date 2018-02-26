// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "statechecker.h"
#include "distributorcomponent.h"
#include "distributor_bucket_space.h"

#include <vespa/log/log.h>
LOG_SETUP(".distributor.statechecker");

namespace storage::distributor {

namespace {

class StoredResultImpl
    : public StateChecker::ResultImpl
{
    mutable IdealStateOperation::UP _operation;
    MaintenancePriority _priority;
public:
    StoredResultImpl(const StoredResultImpl &) = delete;
    StoredResultImpl & operator = (const StoredResultImpl &) = delete;
    StoredResultImpl()
        : _operation(),
          _priority(MaintenancePriority::NO_MAINTENANCE_NEEDED)
    {}

    StoredResultImpl(IdealStateOperation::UP operation,
                     MaintenancePriority priority)
        : _operation(std::move(operation)),
          _priority(priority)
    {}

    IdealStateOperation::UP createOperation() override {
        return std::move(_operation);
    }

    MaintenancePriority getPriority() const override {
        return _priority;
    }

    MaintenanceOperation::Type getType() const override {
        assert(_operation.get());
        return _operation->getType();
    }
};

}

StateChecker::Result
StateChecker::Result::noMaintenanceNeeded()
{
    return Result(std::unique_ptr<ResultImpl>());
}

StateChecker::Result
StateChecker::Result::createStoredResult(
        IdealStateOperation::UP operation,
        MaintenancePriority::Priority priority)
{
    return Result(std::unique_ptr<ResultImpl>(new StoredResultImpl(std::move(operation), MaintenancePriority(priority))));
}

StateChecker::Context::Context(const DistributorComponent& c,
                               const DistributorBucketSpace &distributorBucketSpace,
                               NodeMaintenanceStatsTracker& statsTracker,
                               const document::Bucket &bucket_)
    : bucket(bucket_),
      siblingBucket(c.getSibling(bucket.getBucketId())),
      systemState(distributorBucketSpace.getClusterState()),
      distributorConfig(c.getDistributor().getConfig()),
      distribution(distributorBucketSpace.getDistribution()),
      gcTimeCalculator(c.getDistributor().getBucketIdHasher(),
                       std::chrono::seconds(distributorConfig
                            .getGarbageCollectionInterval())),
      component(c),
      db(distributorBucketSpace.getBucketDatabase()),
      stats(statsTracker)
{
    idealState =
        distribution.getIdealStorageNodes(systemState, bucket.getBucketId());
    unorderedIdealState.insert(idealState.begin(), idealState.end());
}

StateChecker::Context::~Context() {}

std::string
StateChecker::Context::toString() const
{
    std::ostringstream ss;
    ss << "entries: {";

    for (uint32_t i = 0; i < entries.size(); ++i) {
        if (i != 0) {
            ss << ", ";
        }
        ss << entries[i].getBucketId() << ": [" << entries[i]->toString() << "]";
    }

    ss << "}, state: " << systemState;
    return ss.str();
}

StateChecker::StateChecker()
{
}

StateChecker::~StateChecker()
{
}

}
