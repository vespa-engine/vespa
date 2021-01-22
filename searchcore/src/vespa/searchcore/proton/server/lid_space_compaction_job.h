// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "lid_space_compaction_job_base.h"

namespace proton {

class IFrozenBucketHandler;

/**
 * Job that regularly checks whether lid space compaction should be performed
 * for the given handler.
 *
 * Compaction is handled by moving documents from high lids to low free lids.
 * A handler is typically working over a single document sub db.
 */
class LidSpaceCompactionJob : public LidSpaceCompactionJobBase
{
private:
    IFrozenBucketHandler         &_frozenHandler;
    bool                          _retryFrozenDocument;

    bool scanDocuments(const search::LidUsageStats &stats) override;

public:
    LidSpaceCompactionJob(const DocumentDBLidSpaceCompactionConfig &config,
                          ILidSpaceCompactionHandler &handler,
                          IOperationStorer &opStorer,
                          IFrozenBucketHandler &frozenHandler,
                          IDiskMemUsageNotifier &diskMemUsageNotifier,
                          const BlockableMaintenanceJobConfig &blockableConfig,
                          IClusterStateChangedNotifier &clusterStateChangedNotifier,
                          bool nodeRetired);
    ~LidSpaceCompactionJob() override;
};

} // namespace proton

