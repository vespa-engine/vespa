// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "lid_space_compaction_job_base.h"
#include <vespa/document/bucket/bucketspace.h>
#include <atomic>

namespace storage::spi { struct BucketExecutor; }
namespace searchcorespi::index { struct IThreadService; }
namespace vespalib { class IDestructorCallback; }
namespace proton {
    class IDiskMemUsageNotifier;
    class IClusterStateChangedNotifier;
    class MoveOperation;
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
    using IThreadService = searchcorespi::index::IThreadService;
    IThreadService          & _master;
    BucketExecutor          &_bucketExecutor;
    document::BucketSpace    _bucketSpace;
    std::atomic<bool>        _stopped;
    std::atomic<size_t>      _startedCount;
    std::atomic<size_t>      _executedCount;

    bool scanDocuments(const search::LidUsageStats &stats) override;
    void moveDocument(const search::DocumentMetaData & metaThen, std::shared_ptr<IDestructorCallback> onDone);
    void completeMove(const search::DocumentMetaData & metaThen, std::unique_ptr<MoveOperation> moveOp,
                      std::shared_ptr<IDestructorCallback> onDone);
    void onStop() override;
    bool inSync() const override;
    void failOperation();
public:
    CompactionJob(const DocumentDBLidSpaceCompactionConfig &config,
                  std::shared_ptr<ILidSpaceCompactionHandler> handler,
                  IOperationStorer &opStorer,
                  IThreadService & master,
                  BucketExecutor & bucketExecutor,
                  IDiskMemUsageNotifier &diskMemUsageNotifier,
                  const BlockableMaintenanceJobConfig &blockableConfig,
                  IClusterStateChangedNotifier &clusterStateChangedNotifier,
                  bool nodeRetired,
                  document::BucketSpace bucketSpace);
    ~CompactionJob() override;
};

} // namespace proton

