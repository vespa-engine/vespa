// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/storage/distributor/statechecker.h>
#include <vespa/log/log.h>
#include <vespa/storage/distributor/distributorcomponent.h>

#include <algorithm>

LOG_SETUP(".distributor.statechecker");

namespace storage {

namespace distributor {

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

    IdealStateOperation::UP createOperation() {
        return std::move(_operation);
    }

    MaintenancePriority getPriority() const {
        return _priority;
    }

    MaintenanceOperation::Type getType() const {
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
                               NodeMaintenanceStatsTracker& statsTracker,
                               const document::BucketId& bid)
    : bucketId(bid),
      siblingBucket(c.getSibling(bid)),
      systemState(c.getClusterState()),
      distributorConfig(c.getDistributor().getConfig()),
      distribution(c.getDistribution()),
      gcTimeCalculator(c.getDistributor().getBucketIdHasher(),
                       std::chrono::seconds(distributorConfig
                            .getGarbageCollectionInterval())),
      component(c),
      db(c.getBucketDatabase()),
      stats(statsTracker)
{
    idealState =
        distribution.getIdealStorageNodes(systemState, bucketId);
    unorderedIdealState.insert(idealState.begin(), idealState.end());
}

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

}
