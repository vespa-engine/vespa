// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "lid_space_compaction_job_base.h"
#include <vespa/document/bucket/bucketspace.h>

namespace storage::spi { struct BucketExecutor;}
namespace proton {
    class IDiskMemUsageNotifier;
    class IClusterStateChangedNotifier;
}

namespace proton::lidspace {

/**
 * Moves documents from higher lids to lower lids. It uses a BucketExecutor that ensures that the bucket
 * is locked for changes while the document is moved.
 */
class CompactionJob : public LidSpaceCompactionJobBase
{
private:
    using BucketExecutor = storage::spi::BucketExecutor;
    using IDestructorCallback = vespalib::IDestructorCallback;
    BucketExecutor               &_bucketExecutor;
    document::BucketSpace         _bucketSpace;

    bool scanDocuments(const search::LidUsageStats &stats) override;
    void moveDocument(const search::DocumentMetaData & meta, std::shared_ptr<IDestructorCallback> onDone);

public:
    CompactionJob(const DocumentDBLidSpaceCompactionConfig &config,
                  ILidSpaceCompactionHandler &handler,
                  IOperationStorer &opStorer,
                  BucketExecutor & bucketExecutor,
                  IDiskMemUsageNotifier &diskMemUsageNotifier,
                  const BlockableMaintenanceJobConfig &blockableConfig,
                  IClusterStateChangedNotifier &clusterStateChangedNotifier,
                  bool nodeRetired,
                  document::BucketSpace bucketSpace);
    ~CompactionJob() override;
};

} // namespace proton

