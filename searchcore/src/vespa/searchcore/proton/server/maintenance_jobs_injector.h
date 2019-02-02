// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
struct IDocumentMoveHandler;
class IBucketModifiedHandler;
class IClusterStateChangedNotifier;
class IBucketStateChangedNotifier;
struct IBucketStateCalculator;
struct IAttributeManager;
class AttributeUsageFilter;
class IDiskMemUsageNotifier;
namespace bucketdb { class IBucketCreateNotifier; }

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
                           bucketdb::IBucketCreateNotifier &bucketCreateNotifier,
                           const vespalib::string &docTypeName,
                           document::BucketSpace bucketSpace,
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

