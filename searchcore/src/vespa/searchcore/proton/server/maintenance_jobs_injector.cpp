// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmovejob.h"
#include "documentdb_commit_job.h"
#include "heart_beat_job.h"
#include "job_tracked_maintenance_job.h"
#include "lid_space_compaction_job.h"
#include "maintenance_jobs_injector.h"
#include "prune_session_cache_job.h"
#include "pruneremoveddocumentsjob.h"
#include "sample_attribute_usage_job.h"

using vespalib::system_clock;
using fastos::TimeStamp;

namespace proton {

namespace {

IMaintenanceJob::UP
trackJob(const IJobTracker::SP &tracker,
         IMaintenanceJob::UP job)
{
    return IMaintenanceJob::UP(new JobTrackedMaintenanceJob(tracker, std::move(job)));
}

void
injectLidSpaceCompactionJobs(MaintenanceController &controller,
                             const DocumentDBMaintenanceConfig &config,
                             const ILidSpaceCompactionHandler::Vector &lscHandlers,
                             IOperationStorer &opStorer,
                             IFrozenBucketHandler &fbHandler,
                             const IJobTracker::SP &tracker,
                             IDiskMemUsageNotifier &diskMemUsageNotifier,
                             IClusterStateChangedNotifier &clusterStateChangedNotifier,
                             const std::shared_ptr<IBucketStateCalculator> &calc)
{
    for (auto &lidHandler : lscHandlers) {
        IMaintenanceJob::UP job = IMaintenanceJob::UP
                (new LidSpaceCompactionJob(config.getLidSpaceCompactionConfig(),
                                           *lidHandler, opStorer, fbHandler,
                                           diskMemUsageNotifier,
                                           config.getBlockableJobConfig(),
                                           clusterStateChangedNotifier,
                                           (calc ? calc->nodeRetired() : false)));
        controller.registerJobInMasterThread(trackJob(tracker, std::move(job)));
    }
}

void
injectBucketMoveJob(MaintenanceController &controller,
                    IFrozenBucketHandler &fbHandler,
                    bucketdb::IBucketCreateNotifier &bucketCreateNotifier,
                    const vespalib::string &docTypeName,
                    document::BucketSpace bucketSpace,
                    IDocumentMoveHandler &moveHandler,
                    IBucketModifiedHandler &bucketModifiedHandler,
                    IClusterStateChangedNotifier &clusterStateChangedNotifier,
                    IBucketStateChangedNotifier &bucketStateChangedNotifier,
                    const std::shared_ptr<IBucketStateCalculator> &calc,
                    DocumentDBJobTrackers &jobTrackers,
                    IDiskMemUsageNotifier &diskMemUsageNotifier,
                    const BlockableMaintenanceJobConfig &blockableConfig)
{
    IMaintenanceJob::UP bmj;
    bmj.reset(new BucketMoveJob(calc,
                                moveHandler,
                                bucketModifiedHandler,
                                controller.getReadySubDB(),
                                controller.getNotReadySubDB(),
                                fbHandler,
                                bucketCreateNotifier,
                                clusterStateChangedNotifier,
                                bucketStateChangedNotifier,
                                diskMemUsageNotifier,
                                blockableConfig,
                                docTypeName, bucketSpace));
    controller.registerJobInMasterThread(trackJob(jobTrackers.getBucketMove(),
                                                  std::move(bmj)));
}

}

void
MaintenanceJobsInjector::injectJobs(MaintenanceController &controller,
                                    const DocumentDBMaintenanceConfig &config,
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
                                    ICommitable &commit,
                                    IAttributeManagerSP readyAttributeManager,
                                    IAttributeManagerSP notReadyAttributeManager,
                                    AttributeUsageFilter &attributeUsageFilter) {
    typedef IMaintenanceJob::UP MUP;
    controller.registerJobInMasterThread(MUP(new HeartBeatJob(hbHandler, config.getHeartBeatConfig())));
    controller.registerJobInDefaultPool(MUP(new PruneSessionCacheJob(scPruner, config.getSessionCachePruneInterval())));
    if (config.getVisibilityDelay() > 0) {
        controller.registerJobInMasterThread(MUP(new DocumentDBCommitJob(commit, config.getVisibilityDelay())));
    }
    const MaintenanceDocumentSubDB &mRemSubDB(controller.getRemSubDB());
    MUP pruneRDjob(new PruneRemovedDocumentsJob(config.getPruneRemovedDocumentsConfig(), *mRemSubDB.meta_store(),
                                                mRemSubDB.sub_db_id(), docTypeName, prdHandler, fbHandler));
    controller.registerJobInMasterThread(
            trackJob(jobTrackers.getRemovedDocumentsPrune(), std::move(pruneRDjob)));
    if (!config.getLidSpaceCompactionConfig().isDisabled()) {
        injectLidSpaceCompactionJobs(controller, config, lscHandlers, opStorer,
                                     fbHandler, jobTrackers.getLidSpaceCompact(),
                                     diskMemUsageNotifier, clusterStateChangedNotifier, calc);
    }
    injectBucketMoveJob(controller, fbHandler, bucketCreateNotifier, docTypeName, bucketSpace, moveHandler, bucketModifiedHandler,
                        clusterStateChangedNotifier, bucketStateChangedNotifier, calc, jobTrackers,
                        diskMemUsageNotifier, config.getBlockableJobConfig());
    controller.registerJobInMasterThread(std::make_unique<SampleAttributeUsageJob>
                                                 (readyAttributeManager,
                                                  notReadyAttributeManager,
                                                  attributeUsageFilter,
                                                  docTypeName,
                                                  config.getAttributeUsageSampleInterval()));
}

} // namespace proton
