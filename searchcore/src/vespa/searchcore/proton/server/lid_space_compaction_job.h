// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "blockable_maintenance_job.h"
#include "document_db_maintenance_config.h"
#include "i_disk_mem_usage_listener.h"
#include "i_lid_space_compaction_handler.h"
#include "i_operation_storer.h"
#include "ibucketstatecalculator.h"
#include "iclusterstatechangedhandler.h"
#include "iclusterstatechangednotifier.h"
#include "remove_operations_rate_tracker.h"

namespace proton {

class IFrozenBucketHandler;
class IDiskMemUsageNotifier;
class IClusterStateChangedNotifier;

/**
 * Job that regularly checks whether lid space compaction should be performed
 * for the given handler.
 *
 * Compaction is handled by moving documents from high lids to low free lids.
 * A handler is typically working over a single document sub db.
 */
class LidSpaceCompactionJob : public BlockableMaintenanceJob,
                              public IDiskMemUsageListener,
                              public IClusterStateChangedHandler
{
private:
    const DocumentDBLidSpaceCompactionConfig _cfg;
    ILidSpaceCompactionHandler   &_handler;
    IOperationStorer             &_opStorer;
    IFrozenBucketHandler         &_frozenHandler;
    IDocumentScanIterator::UP     _scanItr;
    bool                          _retryFrozenDocument;
    bool                          _shouldCompactLidSpace;
    IDiskMemUsageNotifier        &_diskMemUsageNotifier;
    IClusterStateChangedNotifier &_clusterStateChangedNotifier;
    std::shared_ptr<RemoveOperationsRateTracker> _ops_rate_tracker;
    bool _is_disabled;

    bool hasTooMuchLidBloat(const search::LidUsageStats &stats) const;
    bool shouldRestartScanDocuments(const search::LidUsageStats &stats) const;
    search::DocumentMetaData getNextDocument(const search::LidUsageStats &stats);
    bool scanDocuments(const search::LidUsageStats &stats);
    void compactLidSpace(const search::LidUsageStats &stats);
    bool remove_batch_is_ongoing() const;
    bool remove_is_ongoing() const;

public:
    LidSpaceCompactionJob(const DocumentDBLidSpaceCompactionConfig &config,
                          ILidSpaceCompactionHandler &handler,
                          IOperationStorer &opStorer,
                          IFrozenBucketHandler &frozenHandler,
                          IDiskMemUsageNotifier &diskMemUsageNotifier,
                          const BlockableMaintenanceJobConfig &blockableConfig,
                          IClusterStateChangedNotifier &clusterStateChangedNotifier,
                          bool nodeRetired);
    ~LidSpaceCompactionJob();

    // Implements IDiskMemUsageListener
    void notifyDiskMemUsage(DiskMemUsageState state) override;

    // Implements IClusterStateChangedNofifier
    void notifyClusterStateChanged(const IBucketStateCalculator::SP &newCalc) override;

    // Implements IMaintenanceJob
    bool run() override;
};

} // namespace proton

