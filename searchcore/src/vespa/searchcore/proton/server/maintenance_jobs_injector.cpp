// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maintenance_jobs_injector.h"
#include "bucketmovejob.h"
#include "heart_beat_job.h"
#include "job_tracked_maintenance_job.h"
#include "lid_space_compaction_job.h"
#include "lid_space_compaction_handler.h"
#include "prune_session_cache_job.h"
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
                             IDiskMemUsageNotifier &diskMemUsageNotifier,
                             IClusterStateChangedNotifier &clusterStateChangedNotifier,
                             const std::shared_ptr<IBucketStateCalculator> &calc,
                             document::BucketSpace bucketSpace)
{
    for (auto &lidHandler : lscHandlers) {
        auto job = lidspace::CompactionJob::create(config.getLidSpaceCompactionConfig(), controller.retainDB(),
                                                   std::move(lidHandler), opStorer, controller.masterThread(),
                                                   bucketExecutor, diskMemUsageNotifier,config.getBlockableJobConfig(),
                                                   clusterStateChangedNotifier, calc && calc->nodeRetired(), bucketSpace);
        controller.registerJobInMasterThread(trackJob(tracker, std::move(job)));
    }
}

void
injectBucketMoveJob(MaintenanceController &controller,
                    const DocumentDBMaintenanceConfig &config,
                    storage::spi::BucketExecutor & bucketExecutor,
                    bucketdb::IBucketCreateNotifier &bucketCreateNotifier,
                    const vespalib::string &docTypeName,
                    document::BucketSpace bucketSpace,
                    IDocumentMoveHandler &moveHandler,
                    IBucketModifiedHandler &bucketModifiedHandler,
                    IClusterStateChangedNotifier &clusterStateChangedNotifier,
                    IBucketStateChangedNotifier &bucketStateChangedNotifier,
                    std::shared_ptr<IBucketStateCalculator> calc,
                    DocumentDBJobTrackers &jobTrackers,
                    IDiskMemUsageNotifier &diskMemUsageNotifier)
{
    auto bmj = BucketMoveJob::create(std::move(calc), controller.retainDB(), moveHandler, bucketModifiedHandler, controller.masterThread(),
                                     bucketExecutor, controller.getReadySubDB(), controller.getNotReadySubDB(),
                                     bucketCreateNotifier, clusterStateChangedNotifier, bucketStateChangedNotifier,
                                     diskMemUsageNotifier, config.getBlockableJobConfig(), docTypeName, bucketSpace);
    controller.registerJobInMasterThread(trackJob(jobTrackers.getBucketMove(), std::move(bmj)));
}

}

void
MaintenanceJobsInjector::injectJobs(MaintenanceController &controller,
                                    const DocumentDBMaintenanceConfig &config,
                                    storage::spi::BucketExecutor & bucketExecutor,
                                    IHeartBeatHandler &hbHandler,
                                    matching::ISessionCachePruner &scPruner,
                                    IOperationStorer &opStorer,
                                    bucketdb::IBucketCreateNotifier &bucketCreateNotifier,
                                    document::BucketSpace bucketSpace,
                                    IPruneRemovedDocumentsHandler &prdHandler,
                                    IDocumentMoveHandler &moveHandler,
                                    IBucketModifiedHandler &bucketModifiedHandler,
                                    IClusterStateChangedNotifier &clusterStateChangedNotifier,
                                    IBucketStateChangedNotifier &bucketStateChangedNotifier,
                                    const std::shared_ptr<IBucketStateCalculator> &calc,
                                    IDiskMemUsageNotifier &diskMemUsageNotifier,
                                    DocumentDBJobTrackers &jobTrackers,
                                    IAttributeManagerSP readyAttributeManager,
                                    IAttributeManagerSP notReadyAttributeManager,
                                    AttributeUsageFilter &attributeUsageFilter)
{
    controller.registerJobInMasterThread(std::make_unique<HeartBeatJob>(hbHandler, config.getHeartBeatConfig()));
    controller.registerJobInSharedExecutor(
            std::make_unique<PruneSessionCacheJob>(scPruner, config.getSessionCachePruneInterval()));

    const auto & docTypeName = controller.getDocTypeName().getName();
    const MaintenanceDocumentSubDB &mRemSubDB(controller.getRemSubDB());

    controller.registerJobInMasterThread(
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
                                     opStorer, jobTrackers.getLidSpaceCompact(), diskMemUsageNotifier,
                                     clusterStateChangedNotifier, calc, bucketSpace);
    }

    injectBucketMoveJob(controller, config, bucketExecutor, bucketCreateNotifier, docTypeName, bucketSpace,
                        moveHandler, bucketModifiedHandler, clusterStateChangedNotifier, bucketStateChangedNotifier,
                        calc, jobTrackers, diskMemUsageNotifier);

    controller.registerJobInMasterThread(
            std::make_unique<SampleAttributeUsageJob>(std::move(readyAttributeManager),
                                                      std::move(notReadyAttributeManager),
                                                      attributeUsageFilter, docTypeName,
                                                      config.getAttributeUsageSampleInterval()));
}

} // namespace proton
