// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmovejob.h"
#include "bucketmovejobv2.h"
#include "heart_beat_job.h"
#include "job_tracked_maintenance_job.h"
#include "lid_space_compaction_job.h"
#include "lid_space_compaction_job_take2.h"
#include "maintenance_jobs_injector.h"
#include "prune_session_cache_job.h"
#include "pruneremoveddocumentsjob.h"
#include "sample_attribute_usage_job.h"
#include <vespa/searchcore/proton/attribute/attribute_config_inspector.h>

using vespalib::system_clock;

namespace proton {

namespace {

IMaintenanceJob::UP
trackJob(const IJobTracker::SP &tracker, IMaintenanceJob::UP job)
{
    return std::make_unique<JobTrackedMaintenanceJob>(tracker, std::move(job));
}

void
injectLidSpaceCompactionJobs(MaintenanceController &controller,
                             const DocumentDBMaintenanceConfig &config,
                             storage::spi::BucketExecutor & bucketExecutor,
                             const ILidSpaceCompactionHandler::Vector &lscHandlers,
                             IOperationStorer &opStorer,
                             IFrozenBucketHandler &fbHandler,
                             const IJobTracker::SP &tracker,
                             IDiskMemUsageNotifier &diskMemUsageNotifier,
                             IClusterStateChangedNotifier &clusterStateChangedNotifier,
                             const std::shared_ptr<IBucketStateCalculator> &calc,
                             document::BucketSpace bucketSpace)
{
    for (auto &lidHandler : lscHandlers) {
        std::unique_ptr<IMaintenanceJob> job;
        if (config.getLidSpaceCompactionConfig().useBucketExecutor()) {
            job = std::make_unique<lidspace::CompactionJob>(
                    config.getLidSpaceCompactionConfig(),
                    std::move(lidHandler), opStorer, controller.masterThread(), bucketExecutor,
                    diskMemUsageNotifier,
                    config.getBlockableJobConfig(),
                    clusterStateChangedNotifier,
                    (calc ? calc->nodeRetired() : false),
                    bucketSpace);
        } else {
            job = std::make_unique<LidSpaceCompactionJob>(
                    config.getLidSpaceCompactionConfig(),
                    std::move(lidHandler), opStorer, fbHandler,
                    diskMemUsageNotifier,
                    config.getBlockableJobConfig(),
                    clusterStateChangedNotifier,
                    (calc ? calc->nodeRetired() : false));
        }
        controller.registerJobInMasterThread(trackJob(tracker, std::move(job)));
    }
}

void
injectBucketMoveJob(MaintenanceController &controller,
                    const DocumentDBMaintenanceConfig &config,
                    IFrozenBucketHandler &fbHandler,
                    storage::spi::BucketExecutor & bucketExecutor,
                    bucketdb::IBucketCreateNotifier &bucketCreateNotifier,
                    const vespalib::string &docTypeName,
                    document::BucketSpace bucketSpace,
                    IDocumentMoveHandler &moveHandler,
                    IBucketModifiedHandler &bucketModifiedHandler,
                    IClusterStateChangedNotifier &clusterStateChangedNotifier,
                    IBucketStateChangedNotifier &bucketStateChangedNotifier,
                    const std::shared_ptr<IBucketStateCalculator> &calc,
                    DocumentDBJobTrackers &jobTrackers,
                    IDiskMemUsageNotifier &diskMemUsageNotifier)
{
    std::unique_ptr<IMaintenanceJob> bmj;
    if (config.getBucketMoveConfig().useBucketExecutor()) {
        bmj = std::make_unique<BucketMoveJobV2>(calc,
                                                moveHandler,
                                                bucketModifiedHandler,
                                                controller.masterThread(),
                                                bucketExecutor,
                                                controller.getReadySubDB(),
                                                controller.getNotReadySubDB(),
                                                bucketCreateNotifier,
                                                clusterStateChangedNotifier,
                                                bucketStateChangedNotifier,
                                                diskMemUsageNotifier,
                                                config.getBlockableJobConfig(),
                                                docTypeName, bucketSpace);
    } else {
        bmj = std::make_unique<BucketMoveJob>(calc,
                                              moveHandler,
                                              bucketModifiedHandler,
                                              controller.getReadySubDB(),
                                              controller.getNotReadySubDB(),
                                              fbHandler,
                                              bucketCreateNotifier,
                                              clusterStateChangedNotifier,
                                              bucketStateChangedNotifier,
                                              diskMemUsageNotifier,
                                              config.getBlockableJobConfig(),
                                              docTypeName, bucketSpace);
    }
    controller.registerJobInMasterThread(trackJob(jobTrackers.getBucketMove(), std::move(bmj)));
}

}

void
MaintenanceJobsInjector::injectJobs(MaintenanceController &controller,
                                    const DocumentDBMaintenanceConfig &config,
                                    storage::spi::BucketExecutor & bucketExecutor,
                                    IHeartBeatHandler &hbHandler,
                                    matching::ISessionCachePruner &scPruner,
                                    const ILidSpaceCompactionHandler::Vector &lscHandlers,
                                    IOperationStorer &opStorer,
                                    IFrozenBucketHandler &fbHandler,
                                    bucketdb::IBucketCreateNotifier &bucketCreateNotifier,
                                    const vespalib::string &docTypeName,
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
                                    std::unique_ptr<const AttributeConfigInspector> attribute_config_inspector,
                                    std::shared_ptr<TransientMemoryUsageProvider> transient_memory_usage_provider,
                                    AttributeUsageFilter &attributeUsageFilter)
{
    controller.registerJobInMasterThread(std::make_unique<HeartBeatJob>(hbHandler, config.getHeartBeatConfig()));
    controller.registerJobInDefaultPool(std::make_unique<PruneSessionCacheJob>(scPruner, config.getSessionCachePruneInterval()));

    const MaintenanceDocumentSubDB &mRemSubDB(controller.getRemSubDB());
    auto pruneRDjob = std::make_unique<PruneRemovedDocumentsJob>(config.getPruneRemovedDocumentsConfig(), *mRemSubDB.meta_store(),
                                                                 mRemSubDB.sub_db_id(), docTypeName, prdHandler, fbHandler);
    controller.registerJobInMasterThread(trackJob(jobTrackers.getRemovedDocumentsPrune(), std::move(pruneRDjob)));

    if (!config.getLidSpaceCompactionConfig().isDisabled()) {
        injectLidSpaceCompactionJobs(controller, config, bucketExecutor, lscHandlers, opStorer, fbHandler,
                                     jobTrackers.getLidSpaceCompact(), diskMemUsageNotifier,
                                     clusterStateChangedNotifier, calc, bucketSpace);
    }

    injectBucketMoveJob(controller, config, fbHandler, bucketExecutor, bucketCreateNotifier, docTypeName, bucketSpace,
                        moveHandler, bucketModifiedHandler, clusterStateChangedNotifier, bucketStateChangedNotifier,
                        calc, jobTrackers, diskMemUsageNotifier);

    controller.registerJobInMasterThread(
            std::make_unique<SampleAttributeUsageJob>(readyAttributeManager, notReadyAttributeManager,
                                                      attributeUsageFilter, docTypeName,
                                                      config.getAttributeUsageSampleInterval(),
                                                      std::move(attribute_config_inspector),
                                                      transient_memory_usage_provider));
}

} // namespace proton
