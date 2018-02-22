// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketgctimecalculator.h"
#include <vespa/storage/distributor/maintenance/maintenancepriority.h>
#include <vespa/storage/distributor/operations/idealstate/idealstateoperation.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <unordered_set>
#include <map>
#include <set>

namespace storage {

class DistributorConfiguration;

namespace distributor {

class DistributorComponent;
class DistributorBucketSpace;
class NodeMaintenanceStatsTracker;

/**
 * This class is used by IdealStateManager to generate ideal state operations.
 * Every time IdealStateManager wants to verify that a bucket is in its ideal
 * state, it calls a list of StateCheckers' generateOperations() methods.
 * This generates a list of operations to run.
 *
 * Each statechecker also keeps a queue of operations that have been previously
 * generated. IdealStateManager adds to this queue, and also calls
 * startOperations() to fetch operations to perform.
 *
 * The statechecker can also be used to generate metrics on what needs to be
 * done to reach the ideal state - using the generateMetrics() method.
 */
class StateChecker {
public:
    typedef std::shared_ptr<StateChecker> SP;

    /**
     * Context object used when generating operations and metrics for a
     * bucket.
     */
    struct Context
    {
        Context(const DistributorComponent&,
                const DistributorBucketSpace &distributorBucketSpace,
                NodeMaintenanceStatsTracker&,
                const document::Bucket &bucket_);
        ~Context();
        Context(const Context &) = delete;
        Context & operator =(const Context &) = delete;


        // Per bucket
        document::Bucket   bucket;
        document::BucketId siblingBucket;

        BucketDatabase::Entry entry;
        BucketDatabase::Entry siblingEntry;
        std::vector<BucketDatabase::Entry> entries;

        // Common
        const lib::ClusterState& systemState;
        const DistributorConfiguration& distributorConfig;
        const lib::Distribution& distribution;

        BucketGcTimeCalculator gcTimeCalculator;

        // Separate ideal state into ordered sequence and unordered set, as we
        // need to both know the actual order (activation prioritization etc) as
        // well as have the ability to quickly check if a node is in an ideal
        // location.
        std::vector<uint16_t> idealState;
        std::unordered_set<uint16_t> unorderedIdealState;

        const DistributorComponent& component;
        const BucketDatabase& db;
        NodeMaintenanceStatsTracker& stats;

        const BucketDatabase::Entry& getSiblingEntry() const {
            return siblingEntry;
        }

        document::Bucket getBucket() const { return bucket; }
        document::BucketId getBucketId() const { return bucket.getBucketId(); }
        document::BucketSpace getBucketSpace() const { return bucket.getBucketSpace(); }

        std::string toString() const;
    };

    class ResultImpl
    {
    public:
        virtual ~ResultImpl() {}
        virtual IdealStateOperation::UP createOperation() = 0;
        virtual MaintenancePriority getPriority() const = 0;
        virtual MaintenanceOperation::Type getType() const = 0;
    };

    class Result
    {
        std::unique_ptr<ResultImpl> _impl;
    public:
        IdealStateOperation::UP createOperation() {
            return (_impl.get() 
                    ? _impl->createOperation()
                    : IdealStateOperation::UP());
        }

        MaintenancePriority getPriority() const {
            return (_impl.get()
                    ? _impl->getPriority()
                    : MaintenancePriority());
        }

        MaintenanceOperation::Type getType() const {
            return (_impl.get()
                    ? _impl->getType()
                    : MaintenanceOperation::OPERATION_COUNT);
            
        }

        static Result noMaintenanceNeeded();
        static Result createStoredResult(
                IdealStateOperation::UP operation,
                MaintenancePriority::Priority priority);
    private:
        Result(std::unique_ptr<ResultImpl> impl)
            : _impl(std::move(impl))
        {}
    };

    /**
     * Constructor.
     */
    StateChecker();

    virtual ~StateChecker();

    /**
     * Calculates if operations need to be scheduled to rectify any issues
     * this state checker is checking for.
     *
     * @return Returns an operation to perform for the given bucket.
     */
    virtual Result check(Context& c) = 0;

    /**
     * Used by status pages to generate human-readable information
     * about the ideal state.

     * @return Returns a string containing information about the
     * problems this state checker is intended to solve.
     */
    virtual std::string getStatusText() const = 0;

    /**
     * Returns the name of this state checker.
     */
    virtual const char* getName() const = 0;
};

}

}

