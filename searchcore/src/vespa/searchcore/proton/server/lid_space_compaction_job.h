// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document_db_maintenance_config.h"
#include "i_lid_space_compaction_handler.h"
#include "i_maintenance_job.h"
#include "i_operation_storer.h"
#include "i_disk_mem_usage_listener.h"

namespace proton {

class IFrozenBucketHandler;
class IDiskMemUsageNotifier;

/**
 * Job that regularly checks whether lid space compaction should be performed
 * for the given handler.
 *
 * Compaction is handled by moving documents from high lids to low free lids.
 * A handler is typically working over a single document sub db.
 */
class LidSpaceCompactionJob : public IMaintenanceJob,
                              public IDiskMemUsageListener
{
private:
    const DocumentDBLidSpaceCompactionConfig _cfg;
    ILidSpaceCompactionHandler &_handler;
    IOperationStorer           &_opStorer;
    IFrozenBucketHandler       &_frozenHandler;
    IDocumentScanIterator::UP   _scanItr;
    bool                        _retryFrozenDocument;
    bool                        _shouldCompactLidSpace;
    bool                        _resourcesOK;
    bool                        _runnable;  // can try to perform work
    IMaintenanceJobRunner      *_runner;
    IDiskMemUsageNotifier      &_diskMemUsageNotifier;
    double                      _resourceLimitFactor;

    bool hasTooMuchLidBloat(const search::LidUsageStats &stats) const;
    bool shouldRestartScanDocuments(const search::LidUsageStats &stats) const;
    search::DocumentMetaData getNextDocument(const search::LidUsageStats &stats);
    bool scanDocuments(const search::LidUsageStats &stats);
    void compactLidSpace(const search::LidUsageStats &stats);
    void refreshRunnable();

public:
    LidSpaceCompactionJob(const DocumentDBLidSpaceCompactionConfig &config,
                          ILidSpaceCompactionHandler &handler,
                          IOperationStorer &opStorer,
                          IFrozenBucketHandler &frozenHandler,
                          IDiskMemUsageNotifier &diskMemUsageNotifier,
                          double resourceLimitFactor);
    ~LidSpaceCompactionJob();

    // Implements IDiskMemUsageListener
    virtual void notifyDiskMemUsage(DiskMemUsageState state) override;

    // Implements IMaintenanceJob
    virtual bool run() override;
    virtual void registerRunner(IMaintenanceJobRunner *runner) override;
};

} // namespace proton

