// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketgctimecalculator.h"
#include "ideal_service_layer_nodes_bundle.h"
#include <vespa/storage/distributor/maintenance/maintenancepriority.h>
#include <vespa/storage/distributor/operations/idealstate/idealstateoperation.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <map>
#include <set>

namespace storage::lib {
    class ClusterState;
}
namespace storage { class DistributorConfiguration; }

namespace storage::distributor {

class DistributorBucketSpace;
class DistributorNodeContext;
class DistributorStripeOperationContext;
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
    using SP = std::shared_ptr<StateChecker>;

    /**
     * Context object used when generating operations and metrics for a
     * bucket.
     */
    struct Context
    {
        Context(const DistributorNodeContext& node_ctx_in,
                const DistributorStripeOperationContext& op_ctx_in,
                const DistributorBucketSpace &distributorBucketSpace,
                NodeMaintenanceStatsTracker&,
                const document::Bucket& bucket_);
        ~Context();
        Context(const Context&) = delete;
        Context& operator=(const Context&) = delete;


        // Per bucket
        document::Bucket                   bucket;
        document::BucketId                 siblingBucket;
        BucketDatabase::Entry              entry;
        BucketDatabase::Entry              siblingEntry;
        std::vector<BucketDatabase::Entry> entries;

        // Common
        const lib::ClusterState                 & systemState;
        const lib::ClusterState                 * pending_cluster_state; // nullptr if no state is pending.
        const DistributorConfiguration          & distributorConfig;
        const lib::Distribution                 & distribution;
        BucketGcTimeCalculator                    gcTimeCalculator;
        const IdealServiceLayerNodesBundle      & idealStateBundle;
        const DistributorNodeContext            & node_ctx;
        const DistributorStripeOperationContext & op_ctx;
        const BucketDatabase                    & db;
        NodeMaintenanceStatsTracker             & stats;
        const bool                                merges_inhibited_in_bucket_space;

        const BucketDatabase::Entry& getSiblingEntry() const noexcept { return siblingEntry; }
        IdealServiceLayerNodesBundle::ConstNodesRef idealState() const noexcept {
            return idealStateBundle.available_nonretired_or_maintenance_nodes();
        }

        document::Bucket getBucket() const noexcept { return bucket; }
        document::BucketId getBucketId() const noexcept { return bucket.getBucketId(); }
        document::BucketSpace getBucketSpace() const noexcept { return bucket.getBucketSpace(); }

        std::string toString() const;
    };

    class ResultImpl
    {
    public:
        virtual ~ResultImpl() = default;
        virtual IdealStateOperation::UP createOperation() = 0;
        virtual MaintenancePriority getPriority() const = 0;
        virtual MaintenanceOperation::Type getType() const = 0;
    };

    class Result
    {
        std::unique_ptr<ResultImpl> _impl;
    public:
        IdealStateOperation::UP createOperation() {
            return (_impl ? _impl->createOperation() : IdealStateOperation::UP());
        }

        MaintenancePriority getPriority() const {
            return (_impl ? _impl->getPriority() : MaintenancePriority());
        }

        MaintenanceOperation::Type getType() const {
            return (_impl ? _impl->getType() : MaintenanceOperation::OPERATION_COUNT);
        }

        static Result noMaintenanceNeeded();
        static Result createStoredResult(IdealStateOperation::UP operation, MaintenancePriority::Priority priority);
    private:
        explicit Result(std::unique_ptr<ResultImpl> impl)
            : _impl(std::move(impl))
        {}
    };

    StateChecker() noexcept = default;
    virtual ~StateChecker() = default;

    /**
     * Calculates if operations need to be scheduled to rectify any issues
     * this state checker is checking for.
     *
     * @return Returns an operation to perform for the given bucket.
     */
    virtual Result check(Context& c) const = 0;

    /**
     * Returns the name of this state checker.
     */
    virtual const char* getName() const noexcept = 0;
};

}
