// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document_db_maintenance_config.h"
#include "i_lid_space_compaction_handler.h"
#include "i_maintenance_job.h"
#include "i_operation_storer.h"

namespace proton {

class IFrozenBucketHandler;

/**
 * Job that regularly checks whether lid space compaction should be performed
 * for the given handler.
 *
 * Compaction is handled by moving documents from high lids to low free lids.
 * A handler is typically working over a single document sub db.
 */
class LidSpaceCompactionJob : public IMaintenanceJob
{
private:
    const DocumentDBLidSpaceCompactionConfig _cfg;
    ILidSpaceCompactionHandler &_handler;
    IOperationStorer           &_opStorer;
    IFrozenBucketHandler       &_frozenHandler;
    IDocumentScanIterator::UP   _scanItr;
    bool                        _retryFrozenDocument;
    bool                        _shouldCompactLidSpace;

    bool hasTooMuchLidBloat(const search::LidUsageStats &stats) const;
    bool shouldRestartScanDocuments(const search::LidUsageStats &stats) const;
    search::DocumentMetaData getNextDocument(const search::LidUsageStats &stats);
    bool scanDocuments(const search::LidUsageStats &stats);
    void compactLidSpace(const search::LidUsageStats &stats);

public:
    LidSpaceCompactionJob(const DocumentDBLidSpaceCompactionConfig &config,
                          ILidSpaceCompactionHandler &handler,
                          IOperationStorer &opStorer,
                          IFrozenBucketHandler &frozenHandler);

    // Implements IMaintenanceJob
    virtual bool run();
};

} // namespace proton

