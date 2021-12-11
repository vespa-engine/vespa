// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blockable_maintenance_job.h"
#include "document_db_maintenance_config.h"
#include "i_disk_mem_usage_listener.h"
#include "iclusterstatechangedhandler.h"
#include <vespa/document/bucket/bucketspace.h>
#include <vespa/searchlib/common/idocumentmetastore.h>
#include <vespa/vespalib/util/retain_guard.h>
#include <atomic>

namespace storage::spi { struct BucketExecutor; }
namespace searchcorespi::index { struct IThreadService; }
namespace vespalib { class IDestructorCallback; }
namespace proton {
    class MoveOperation;
    class IDiskMemUsageNotifier;
    class IClusterStateChangedNotifier;
    struct IOperationStorer;
    struct ILidSpaceCompactionHandler;
    struct IDocumentScanIterator;
    class RemoveOperationsRateTracker;
}

namespace proton::lidspace {

/**
 * Moves documents from higher lids to lower lids. It uses a BucketExecutor that ensures that the bucket
 * is locked for changes while the document is moved.
 */
class CompactionJob : public BlockableMaintenanceJob,
                      public IDiskMemUsageListener,
                      public IClusterStateChangedHandler,
                      public std::enable_shared_from_this<CompactionJob>
{
private:
    using BucketExecutor = storage::spi::BucketExecutor;
    using IDestructorCallback = vespalib::IDestructorCallback;
    using IThreadService = searchcorespi::index::IThreadService;
    const DocumentDBLidSpaceCompactionConfig      _cfg;
    std::shared_ptr<ILidSpaceCompactionHandler>   _handler;
    IOperationStorer                             &_opStorer;
    std::unique_ptr<IDocumentScanIterator>        _scanItr;
    IDiskMemUsageNotifier                        &_diskMemUsageNotifier;
    IClusterStateChangedNotifier                 &_clusterStateChangedNotifier;
    std::shared_ptr<RemoveOperationsRateTracker>  _ops_rate_tracker;
    bool                                          _is_disabled;
    bool                                          _shouldCompactLidSpace;
    IThreadService                               &_master;
    BucketExecutor                               &_bucketExecutor;
    vespalib::RetainGuard                         _dbRetainer;
    document::BucketSpace                         _bucketSpace;

    bool hasTooMuchLidBloat(const search::LidUsageStats &stats) const;
    bool shouldRestartScanDocuments(const search::LidUsageStats &stats) const;
    void compactLidSpace(const search::LidUsageStats &stats);
    bool remove_batch_is_ongoing() const;
    bool remove_is_ongoing() const;
    search::DocumentMetaData getNextDocument(const search::LidUsageStats &stats);

    bool scanDocuments(const search::LidUsageStats &stats);
    static void moveDocument(std::shared_ptr<CompactionJob> job, const search::DocumentMetaData & metaThen,
                             std::shared_ptr<IDestructorCallback> onDone);
    void completeMove(const search::DocumentMetaData & metaThen, std::unique_ptr<MoveOperation> moveOp,
                      std::shared_ptr<IDestructorCallback> onDone);
    class MoveTask;

    CompactionJob(const DocumentDBLidSpaceCompactionConfig &config,
                  vespalib::RetainGuard dbRetainer,
                  std::shared_ptr<ILidSpaceCompactionHandler> handler,
                  IOperationStorer &opStorer,
                  IThreadService & master,
                  BucketExecutor & bucketExecutor,
                  IDiskMemUsageNotifier &diskMemUsageNotifier,
                  const BlockableMaintenanceJobConfig &blockableConfig,
                  IClusterStateChangedNotifier &clusterStateChangedNotifier,
                  bool nodeRetired,
                  document::BucketSpace bucketSpace);
public:
    static std::shared_ptr<CompactionJob>
    create(const DocumentDBLidSpaceCompactionConfig &config,
           vespalib::RetainGuard dbRetainer,
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
    void notifyDiskMemUsage(DiskMemUsageState state) override;
    void notifyClusterStateChanged(const std::shared_ptr<IBucketStateCalculator> &newCalc) override;
    bool run() override;
};

} // namespace proton
