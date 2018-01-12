// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "distributorcomponent.h"
#include "statechecker.h"
#include <vespa/storage/distributor/maintenance/maintenanceprioritygenerator.h>
#include <vespa/storage/distributor/maintenance/maintenanceoperationgenerator.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/vdslib/state/clusterstate.h>

namespace storage::distributor {

class IdealStateMetricSet;
class IdealStateOperation;
class Distributor;
class SplitBucketStateChecker;

/**
   @class IdealStateManager

   This storage link is responsible for generating maintenance operations to
   be performed on the storage nodes.

   To generate operation objects, we have a set of StateCheckers. A
   StateChecker takes a bucket and configuration information, and checks for a
   certain property on the bucket. If that property is not according to the
   configuration, it makes an Operation to correct the problem. The
   StateCheckers are run in sequence for each bucket, and only one StateChecker
   may generate Operations. Once one does so, the rest of the state checkers
   aren't run.
*/
class IdealStateManager : public framework::HtmlStatusReporter,
                          public MaintenancePriorityGenerator,
                          public MaintenanceOperationGenerator
{
public:

    IdealStateManager(Distributor& owner,
                      DistributorBucketSpaceRepo& bucketSpaceRepo,
                      DistributorComponentRegister& compReg,
                      bool manageActiveBucketCopies);

    ~IdealStateManager();

    void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    // MaintenancePriorityGenerator interface
    MaintenancePriorityAndType prioritize(
            const document::Bucket &bucket,
            NodeMaintenanceStatsTracker& statsTracker) const override;

    // MaintenanceOperationGenerator
    MaintenanceOperation::SP generate(const document::Bucket &bucket) const override;

    // MaintenanceOperationGenerator
    std::vector<MaintenanceOperation::SP> generateAll(
            const document::Bucket &bucket,
            NodeMaintenanceStatsTracker& statsTracker) const override;

    /**
     * If the given bucket is too large, generate a split operation for it,
     * with higher priority than the given one.
     */
    IdealStateOperation::SP generateInterceptingSplit(
            document::BucketSpace bucketSpace,
            const BucketDatabase::Entry& e,
            api::StorageMessage::Priority pri);

    IdealStateMetricSet& getMetrics() { return *_metrics; }

    void getBucketStatus(std::ostream& out) const;

    // HtmlStatusReporter
    void reportHtmlStatus(
            std::ostream& out, const framework::HttpUrlPath&) const override {
        getBucketStatus(out);
    }

    DistributorComponent& getDistributorComponent() {
        return _distributorComponent; }
    StorageComponent::LoadTypeSetSP getLoadTypes() {
        return _distributorComponent.getLoadTypes(); }
    DistributorBucketSpaceRepo &getBucketSpaceRepo() { return _bucketSpaceRepo; }
    const DistributorBucketSpaceRepo &getBucketSpaceRepo() const { return _bucketSpaceRepo; }

private:
    void fillParentAndChildBuckets(StateChecker::Context& c) const;
    void fillSiblingBucket(StateChecker::Context& c) const;
    StateChecker::Result generateHighestPriority(
            const document::Bucket &bucket,
            NodeMaintenanceStatsTracker& statsTracker) const;
    StateChecker::Result runStateCheckers(StateChecker::Context& c) const;

    BucketDatabase::Entry* getEntryForPrimaryBucket(StateChecker::Context& c) const;

    friend class Operation_TestCase;
    friend class RemoveBucketOperation_Test;
    friend class MergeOperation_Test;
    friend class CreateBucketOperation_Test;
    friend class SplitOperation_Test;
    friend class JoinOperation_Test;

    std::shared_ptr<IdealStateMetricSet> _metrics;
    document::BucketId _lastPrioritizedBucket;

    // Prioritized of state checkers that generate operations
    // for idealstatemanager.
    std::vector<StateChecker::SP> _stateCheckers;
    SplitBucketStateChecker* _splitBucketStateChecker;

    DistributorComponent            _distributorComponent;
    DistributorBucketSpaceRepo     &_bucketSpaceRepo;

    std::vector<IdealStateOperation::SP> generateOperationsForBucket(StateChecker::Context& c) const;

    bool iAmUp() const;

    class StatusBucketVisitor : public BucketDatabase::EntryProcessor {
        // Stats tracker to use for all generateAll() calls to avoid having
        // to create a new hash map for each single bucket processed.
        NodeMaintenanceStatsTracker _statsTracker;
        const IdealStateManager   & _ism;
        document::BucketSpace       _bucketSpace;
        std::ostream              & _out;
    public:
        StatusBucketVisitor(const IdealStateManager& ism, document::BucketSpace bucketSpace, std::ostream& out)
            : _statsTracker(), _ism(ism), _bucketSpace(bucketSpace), _out(out) {}

        bool process(const BucketDatabase::Entry& e) override {
            _ism.getBucketStatus(_bucketSpace, e, _statsTracker, _out);
            return true;
        }
    };
    friend class StatusBucketVisitor;

    void getBucketStatus(document::BucketSpace bucketSpace, const BucketDatabase::Entry& entry,
                         NodeMaintenanceStatsTracker& statsTracker, std::ostream& out) const;
    void dump_bucket_space_db_status(document::BucketSpace bucket_space, std::ostream& out) const;
};

}
