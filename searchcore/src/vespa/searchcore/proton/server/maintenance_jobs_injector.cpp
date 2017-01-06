// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.maintenance_jobs_injector");

#include "heart_beat_job.h"
#include "job_tracked_maintenance_job.h"
#include "lid_space_compaction_job.h"
#include "maintenance_jobs_injector.h"
#include "prune_session_cache_job.h"
#include "wipe_old_removed_fields_job.h"
#include <vespa/fastos/timestamp.h>
#include "pruneremoveddocumentsjob.h"
#include "documentdb_commit_job.h"
#include "documentbucketmover.h"
#include "bucketmovejob.h"
#include "sample_attribute_usage_job.h"

using fastos::ClockSystem;
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
                             IDiskMemUsageNotifier &diskMemUsageNotifier)
{
    for (auto &lidHandler : lscHandlers) {
        IMaintenanceJob::UP job = IMaintenanceJob::UP
                (new LidSpaceCompactionJob(config.getLidSpaceCompactionConfig(),
                                           *lidHandler, opStorer, fbHandler,
                                           diskMemUsageNotifier));
        controller.registerJob(std::move(trackJob(tracker,
                std::move(job))));
    }
}


void
injectBucketMoveJob(MaintenanceController &controller,
                    IFrozenBucketHandler &fbHandler,
                    const vespalib::string &docTypeName,
                    IDocumentMoveHandler &moveHandler,
                    IBucketModifiedHandler &bucketModifiedHandler,
                    IClusterStateChangedNotifier &clusterStateChangedNotifier,
                    IBucketStateChangedNotifier &bucketStateChangedNotifier,
                    const std::shared_ptr<IBucketStateCalculator> &calc,
                    DocumentDBJobTrackers &jobTrackers,
                    IDiskMemUsageNotifier &diskMemUsageNotifier)
{
    IMaintenanceJob::UP bmj;
    bmj.reset(new BucketMoveJob(calc,
                                moveHandler,
                                bucketModifiedHandler,
                                controller.getReadySubDB(),
                                controller.getNotReadySubDB(),
                                fbHandler,
                                clusterStateChangedNotifier,
                                bucketStateChangedNotifier,
                                diskMemUsageNotifier,
                                docTypeName));
    controller.registerJob(std::move(trackJob(jobTrackers.getBucketMove(),
                                              std::move(bmj))));
}

}

void
MaintenanceJobsInjector::injectJobs(MaintenanceController &controller,
                                    const DocumentDBMaintenanceConfig &config,
                                    IHeartBeatHandler &hbHandler,
                                    matching::ISessionCachePruner &scPruner,
                                    IWipeOldRemovedFieldsHandler &worfHandler,
                                    const ILidSpaceCompactionHandler::Vector &lscHandlers,
                                    IOperationStorer &opStorer,
                                    IFrozenBucketHandler &fbHandler,
                                    const vespalib::string &docTypeName,
                                    IPruneRemovedDocumentsHandler &prdHandler,
                                    IDocumentMoveHandler &moveHandler,
                                    IBucketModifiedHandler &
                                    bucketModifiedHandler,
                                    IClusterStateChangedNotifier &
                                    clusterStateChangedNotifier,
                                    IBucketStateChangedNotifier &
                                    bucketStateChangedNotifier,
                                    const std::shared_ptr<IBucketStateCalculator> & calc,
                                    IDiskMemUsageNotifier &diskMemUsageNotifier,
                                    DocumentDBJobTrackers &jobTrackers,
                                    ICommitable & commit,
                                    IAttributeManagerSP readyAttributeManager,
                                    IAttributeManagerSP
                                    notReadyAttributeManager,
                                    AttributeUsageFilter &attributeUsageFilter)
{
    typedef IMaintenanceJob::UP MUP;
    controller.registerJob(MUP(new HeartBeatJob(hbHandler, config.getHeartBeatConfig())));
    controller.registerJob(MUP(new PruneSessionCacheJob(scPruner, config.getSessionCachePruneInterval())));
    if (config.getVisibilityDelay() > 0) {
        controller.registerJob(MUP(new DocumentDBCommitJob(commit, config.getVisibilityDelay())));
    }
    controller.registerJob(MUP(new WipeOldRemovedFieldsJob(worfHandler, config.getWipeOldRemovedFieldsConfig())));
    const MaintenanceDocumentSubDB &mRemSubDB(controller.getRemSubDB());
    MUP pruneRDjob(new PruneRemovedDocumentsJob(config.getPruneRemovedDocumentsConfig(), *mRemSubDB._metaStore,
                                                mRemSubDB._subDbId, docTypeName, prdHandler, fbHandler));
    controller.registerJob(std::move(trackJob(jobTrackers.getRemovedDocumentsPrune(), std::move(pruneRDjob))));
    injectLidSpaceCompactionJobs(controller, config, lscHandlers, opStorer,
                                 fbHandler, jobTrackers.getLidSpaceCompact(),
                                 diskMemUsageNotifier);
    injectBucketMoveJob(controller, fbHandler, docTypeName, moveHandler, bucketModifiedHandler,
                        clusterStateChangedNotifier, bucketStateChangedNotifier, calc, jobTrackers, diskMemUsageNotifier);
    controller.registerJob(std::make_unique<SampleAttributeUsageJob>
                           (readyAttributeManager,
                            notReadyAttributeManager,
                            attributeUsageFilter,
                            docTypeName,
                            config.getAttributeUsageSampleInterval()));
}

} // namespace proton
