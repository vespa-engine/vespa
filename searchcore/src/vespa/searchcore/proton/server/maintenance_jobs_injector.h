// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document_db_maintenance_config.h"
#include "maintenancecontroller.h"
#include "i_lid_space_compaction_handler.h"
#include "i_operation_storer.h"
#include "iheartbeathandler.h"
#include <vespa/searchcore/proton/matching/isessioncachepruner.h>
#include <vespa/searchcore/proton/metrics/documentdb_job_trackers.h>

namespace storage::spi {struct BucketExecutor; }
namespace proton {

class AttributeUsageFilter;
class IBucketModifiedHandler;
class IBucketStateChangedNotifier;
class IClusterStateChangedNotifier;
class IDiskMemUsageNotifier;
class IPruneRemovedDocumentsHandler;
struct IAttributeManager;
struct IBucketStateCalculator;
struct IDocumentMoveHandler;
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
                           storage::spi::BucketExecutor & bucketExecutor,
                           IHeartBeatHandler &hbHandler,
                           matching::ISessionCachePruner &scPruner,
                           IOperationStorer &opStorer,
                           bucketdb::IBucketCreateNotifier &bucketCreateNotifier,
                           document::BucketSpace bucketSpace,
                           IPruneRemovedDocumentsHandler &prdHandler,
                           IDocumentMoveHandler &moveHandler,
                           IBucketModifiedHandler &bucketModifiedHandler,
                           IClusterStateChangedNotifier & clusterStateChangedNotifier,
                           IBucketStateChangedNotifier & bucketStateChangedNotifier,
                           const std::shared_ptr<IBucketStateCalculator> &calc,
                           IDiskMemUsageNotifier &diskMemUsageNotifier,
                           DocumentDBJobTrackers &jobTrackers,
                           IAttributeManagerSP readyAttributeManager,
                           IAttributeManagerSP notReadyAttributeManager,
                           AttributeUsageFilter &attributeUsageFilter);
};

} // namespace proton

