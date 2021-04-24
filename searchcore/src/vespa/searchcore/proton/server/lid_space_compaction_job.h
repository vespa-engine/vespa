// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "lid_space_compaction_job_base.h"

namespace proton {

class IFrozenBucketHandler;

/**
 * Moves documents from higher lids to lower lids. It uses a 'frozen' bucket mechanism to ensure that it has exclusive access to the document.
 */
class LidSpaceCompactionJob : public LidSpaceCompactionJobBase
{
private:
    IFrozenBucketHandler         &_frozenHandler;
    bool                          _retryFrozenDocument;

    bool scanDocuments(const search::LidUsageStats &stats) override;

public:
    LidSpaceCompactionJob(const DocumentDBLidSpaceCompactionConfig &config,
                          std::shared_ptr<ILidSpaceCompactionHandler> handler,
                          IOperationStorer &opStorer,
                          IFrozenBucketHandler &frozenHandler,
                          IDiskMemUsageNotifier &diskMemUsageNotifier,
                          const BlockableMaintenanceJobConfig &blockableConfig,
                          IClusterStateChangedNotifier &clusterStateChangedNotifier,
                          bool nodeRetired);
    ~LidSpaceCompactionJob() override;
};

} // namespace proton

