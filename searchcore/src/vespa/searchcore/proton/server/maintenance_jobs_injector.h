// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document_db_maintenance_config.h"
#include "maintenancecontroller.h"
#include "i_lid_space_compaction_handler.h"
#include "i_operation_storer.h"
#include "iheartbeathandler.h"
#include "icommitable.h"
#include <vespa/searchcore/proton/matching/isessioncachepruner.h>
#include <vespa/searchcore/proton/metrics/documentdb_job_trackers.h>

namespace proton {

class IPruneRemovedDocumentsHandler;
class IDocumentMoveHandler;
class IBucketModifiedHandler;
class IClusterStateChangedNotifier;
class IBucketStateChangedNotifier;
class IBucketStateCalculator;
class IAttributeManager;
class AttributeUsageFilter;
class IDiskMemUsageNotifier;

/**
 * Class that injects all concrete maintenance jobs used in document db
 * into a MaintenanceController.
 */
struct MaintenanceJobsInjector
{
    using IAttributeManagerSP = std::shared_ptr<IAttributeManager>;
    static void injectJobs(MaintenanceController &controller,
                           const DocumentDBMaintenanceConfig &config,
                           IHeartBeatHandler &hbHandler,
                           matching::ISessionCachePruner &scPruner,
                           const ILidSpaceCompactionHandler::Vector &lscHandlers,
                           IOperationStorer &opStorer,
                           IFrozenBucketHandler &fbHandler,
                           const vespalib::string &docTypeName,
                           IPruneRemovedDocumentsHandler &prdHandler,
                           IDocumentMoveHandler &moveHandler,
                           IBucketModifiedHandler &bucketModifiedHandler,
                           IClusterStateChangedNotifier &
                           clusterStateChangedNotifier,
                           IBucketStateChangedNotifier &
                           bucketStateChangedNotifier,
                           const std::shared_ptr<IBucketStateCalculator> &calc,
                           IDiskMemUsageNotifier &diskMemUsageNotifier,
                           DocumentDBJobTrackers &jobTrackers,
                           ICommitable & commit,
                           IAttributeManagerSP readyAttributeManager,
                           IAttributeManagerSP notReadyAttributeManager,
                           AttributeUsageFilter &attributeUsageFilter);
};

} // namespace proton

