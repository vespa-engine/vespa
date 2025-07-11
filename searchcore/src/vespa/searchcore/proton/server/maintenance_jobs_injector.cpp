// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maintenance_jobs_injector.h"
#include "bucketmovejob.h"
#include "clear_imported_attribute_search_cache_job.h"
#include "heart_beat_job.h"
#include "job_tracked_maintenance_job.h"
#include "lid_space_compaction_job.h"
#include "lid_space_compaction_handler.h"
#include "pruneremoveddocumentsjob.h"
#include "sample_attribute_usage_job.h"

using vespalib::system_clock;

namespace proton {

namespace {

IMaintenanceJob::UP
trackJob(std::shared_ptr<IJobTracker> tracker, std::shared_ptr<IMaintenanceJob> job)
{
    return std::make_unique<JobTrackedMaintenanceJob>(std::move(tracker), std::move(job));
}

void
injectLidSpaceCompactionJobs(MaintenanceController &controller,
                             const DocumentDBMaintenanceConfig &config,
                             storage::spi::BucketExecutor & bucketExecutor,
                             ILidSpaceCompactionHandler::Vector lscHandlers,
                             IOperationStorer &opStorer,
                             std::shared_ptr<IJobTracker> tracker,
                             IResourceUsageNotifier &resource_usage_notifier,
                             IClusterStateChangedNotifier &clusterStateChangedNotifier,
                             const std::shared_ptr<IBucketStateCalculator> &calc,
                             document::BucketSpace bucketSpace,
                             std::shared_ptr<MaintenanceJobTokenSource> maintenance_job_token_source)
{
    for (auto &lidHandler : lscHandlers) {
        auto job = lidspace::CompactionJob::create(config.getLidSpaceCompactionConfig(), controller.retainDB(),
                                                   std::move(lidHandler), opStorer, controller.masterThread(),
                                                   bucketExecutor, resource_usage_notifier, config.getBlockableJobConfig(),
                                                   clusterStateChangedNotifier, calc && calc->node_retired_or_maintenance(), bucketSpace,
                                                   maintenance_job_token_source);
        controller.registerJob(trackJob(tracker, std::move(job)));
    }
}

void
injectBucketMoveJob(MaintenanceController &controller,
                    const DocumentDBMaintenanceConfig &config,
                    storage::spi::BucketExecutor & bucketExecutor,
                    bucketdb::IBucketCreateNotifier &bucketCreateNotifier,
                    const std::string &docTypeName,
                    document::BucketSpace bucketSpace,
                    IDocumentMoveHandler &moveHandler,
                    IBucketModifiedHandler &bucketModifiedHandler,
                    IClusterStateChangedNotifier &clusterStateChangedNotifier,
                    IBucketStateChangedNotifier &bucketStateChangedNotifier,
                    std::shared_ptr<IBucketStateCalculator> calc,
                    DocumentDBJobTrackers &jobTrackers,
                    IResourceUsageNotifier &resource_usage_notifier)
{
    auto bmj = BucketMoveJob::create(std::move(calc), controller.retainDB(), moveHandler, bucketModifiedHandler, controller.masterThread(),
                                     bucketExecutor, controller.getReadySubDB(), controller.getNotReadySubDB(),
                                     bucketCreateNotifier, clusterStateChangedNotifier, bucketStateChangedNotifier,
                                     resource_usage_notifier, config.getBlockableJobConfig(), docTypeName, bucketSpace);
    controller.registerJob(trackJob(jobTrackers.getBucketMove(), std::move(bmj)));
}

}

void
MaintenanceJobsInjector::injectJobs(MaintenanceController &controller,
                                    const DocumentDBMaintenanceConfig &config,
                                    storage::spi::BucketExecutor & bucketExecutor,
                                    IHeartBeatHandler &hbHandler,
                                    IOperationStorer &opStorer,
                                    bucketdb::IBucketCreateNotifier &bucketCreateNotifier,
                                    document::BucketSpace bucketSpace,
                                    IPruneRemovedDocumentsHandler &prdHandler,
                                    IDocumentMoveHandler &moveHandler,
                                    IBucketModifiedHandler &bucketModifiedHandler,
                                    IClusterStateChangedNotifier &clusterStateChangedNotifier,
                                    IBucketStateChangedNotifier &bucketStateChangedNotifier,
                                    const std::shared_ptr<IBucketStateCalculator> &calc,
                                    IResourceUsageNotifier &resource_usage_notifier,
                                    DocumentDBJobTrackers &jobTrackers,
                                    IAttributeManagerSP readyAttributeManager,
                                    IAttributeManagerSP notReadyAttributeManager,
                                    AttributeUsageFilter &attributeUsageFilter,
                                    std::shared_ptr<MaintenanceJobTokenSource> lid_space_compaction_job_token_source,
                                    std::shared_ptr<searchcorespi::IIndexManager> index_manager)
{
    controller.registerJob(std::make_unique<HeartBeatJob>(hbHandler, config.getHeartBeatConfig()));
    auto visibility_delay = config.getVisibilityDelay();
    if (visibility_delay != vespalib::duration::zero()) {
        if (visibility_delay < 100ms) {
            visibility_delay = 100ms;
        }
        controller.registerJob(std::make_unique<ClearImportedAttributeSearchCacheJob>(readyAttributeManager, config.getVisibilityDelay()));
    }

    const auto & docTypeName = controller.getDocTypeName().getName();
    const MaintenanceDocumentSubDB &mRemSubDB(controller.getRemSubDB());

    controller.registerJob(
            trackJob(jobTrackers.getRemovedDocumentsPrune(),
                     PruneRemovedDocumentsJob::create(config.getPruneRemovedDocumentsConfig(), controller.retainDB(),
                                                      *mRemSubDB.meta_store(), mRemSubDB.sub_db_id(), bucketSpace,
                                                      docTypeName, prdHandler, controller.masterThread(),
                                                      bucketExecutor)));


    if (!config.getLidSpaceCompactionConfig().isDisabled()) {
        ILidSpaceCompactionHandler::Vector lidSpaceCompactionHandlers;
        lidSpaceCompactionHandlers.push_back(std::make_shared<LidSpaceCompactionHandler>(controller.getReadySubDB(), docTypeName));
        lidSpaceCompactionHandlers.push_back(std::make_shared<LidSpaceCompactionHandler>(controller.getRemSubDB(), docTypeName));
        lidSpaceCompactionHandlers.push_back(std::make_shared<LidSpaceCompactionHandler>(controller.getNotReadySubDB(), docTypeName));
        injectLidSpaceCompactionJobs(controller, config, bucketExecutor, std::move(lidSpaceCompactionHandlers),
                                     opStorer, jobTrackers.getLidSpaceCompact(), resource_usage_notifier,
                                     clusterStateChangedNotifier, calc, bucketSpace, std::move(lid_space_compaction_job_token_source));
    }

    injectBucketMoveJob(controller, config, bucketExecutor, bucketCreateNotifier, docTypeName, bucketSpace,
                        moveHandler, bucketModifiedHandler, clusterStateChangedNotifier, bucketStateChangedNotifier,
                        calc, jobTrackers, resource_usage_notifier);

    controller.registerJob(
            std::make_unique<SampleAttributeUsageJob>(std::move(readyAttributeManager),
                                                      std::move(notReadyAttributeManager),
                                                      attributeUsageFilter, docTypeName,
                                                      config.getAttributeUsageSampleInterval(),
                                                      std::move(index_manager)));
}

} // namespace proton
