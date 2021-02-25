// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_space_compaction_job_take2.h"
#include "i_document_scan_iterator.h"
#include "i_lid_space_compaction_handler.h"
#include "i_operation_storer.h"
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vespa/persistence/spi/bucket_tasks.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <cassert>
#include <thread>

using search::DocumentMetaData;
using search::LidUsageStats;
using storage::spi::makeBucketTask;
using storage::spi::Bucket;
using vespalib::makeLambdaTask;

namespace proton::lidspace {

namespace {

class IncOnDestruct {
public:
    IncOnDestruct(std::atomic<size_t> & count) : _count(count) {}
    ~IncOnDestruct() {
        _count.fetch_add(1, std::memory_order_relaxed);
    }
private:
    std::atomic<size_t> & _count;
};

bool
isSameDocument(const search::DocumentMetaData & a, const search::DocumentMetaData & b) {
    return (a.lid == b.lid) &&
           (a.bucketId == b.bucketId) &&
           (a.gid == b.gid) &&
           (a.timestamp == b.timestamp); // Timestamp check can be removed once logic has proved itself in large scale.
}

}

void
CompactionJob::failOperation() {
    _executedCount.fetch_add(1, std::memory_order_relaxed);
    _scanItr.reset();
}

bool
CompactionJob::scanDocuments(const LidUsageStats &stats)
{
    if (_scanItr->valid()) {
        DocumentMetaData document = getNextDocument(stats, false);
        if (document.valid()) {
            Bucket metaBucket(document::Bucket(_bucketSpace, document.bucketId));
            IDestructorCallback::SP context = getLimiter().beginOperation();
            auto bucketTask = makeBucketTask([this, meta=document, opsTracker=std::move(context)]
            (const Bucket & bucket, std::shared_ptr<IDestructorCallback> onDone) {
                assert(bucket.getBucketId() == meta.bucketId);
                using DoneContext = vespalib::KeepAlive<std::pair<IDestructorCallback::SP, IDestructorCallback::SP>>;
                moveDocument(meta, std::make_shared<DoneContext>(std::make_pair(std::move(opsTracker), std::move(onDone))));
            }, [this](const Bucket &) { _master.execute(makeLambdaTask([this] { failOperation(); } )); });

            _startedCount.fetch_add(1, std::memory_order_relaxed);
            _bucketExecutor.execute(metaBucket, std::move(bucketTask));
            if (isBlocked(BlockedReason::OUTSTANDING_OPS)) {
                return true;
            }
        }
    }
    return false;
}

void
CompactionJob::moveDocument(const search::DocumentMetaData & metaThen, std::shared_ptr<IDestructorCallback> context) {
    IncOnDestruct countGuard(_executedCount);
    if (_stopped.load(std::memory_order_relaxed)) return;
    // The real lid must be sampled in the master thread.
    //TODO remove target lid from createMoveOperation interface
    // Reread meta data as document might have been altered after move was initiated
    // If so it will fail the timestamp sanity check later on.
    search::DocumentMetaData metaNow = _handler->getMetaData(metaThen.lid);
    if ( ! isSameDocument(metaThen, metaNow)) return;
    auto op = _handler->createMoveOperation(metaNow, 0);
    if (!op || !op->getDocument()) return;
    // Early detection and force md5 calculation outside of master thread
    if (metaThen.gid != op->getDocument()->getId().getGlobalId()) return;

    _master.execute(makeLambdaTask([this, metaNow, moveOp=std::move(op), onDone=std::move(context)]() mutable {
        if (_stopped.load(std::memory_order_relaxed)) return;
        completeMove(metaNow, std::move(moveOp), std::move(onDone));
    }));
}

void
CompactionJob::completeMove(const search::DocumentMetaData & metaThen, std::unique_ptr<MoveOperation> moveOp,
                            std::shared_ptr<IDestructorCallback> onDone)
{
    search::DocumentMetaData metaNow = _handler->getMetaData(metaThen.lid);
    // This should be impossible and should probably be an assert
    if ( ! isSameDocument(metaThen, metaNow)) return;
    if (metaNow.gid != moveOp->getDocument()->getId().getGlobalId()) return;

    uint32_t lowestLid = _handler->getLidStatus().getLowestFreeLid();
    if (lowestLid >= metaNow.lid) return;
    moveOp->setTargetLid(lowestLid);
    _opStorer.appendOperation(*moveOp, onDone);
    _handler->handleMove(*moveOp, std::move(onDone));
}

CompactionJob::CompactionJob(const DocumentDBLidSpaceCompactionConfig &config,
                             std::shared_ptr<ILidSpaceCompactionHandler> handler,
                             IOperationStorer &opStorer,
                             IThreadService & master,
                             BucketExecutor & bucketExecutor,
                             IDiskMemUsageNotifier &diskMemUsageNotifier,
                             const BlockableMaintenanceJobConfig &blockableConfig,
                             IClusterStateChangedNotifier &clusterStateChangedNotifier,
                             bool nodeRetired,
                             document::BucketSpace bucketSpace)
    : LidSpaceCompactionJobBase(config, std::move(handler), opStorer, diskMemUsageNotifier,
                                blockableConfig, clusterStateChangedNotifier, nodeRetired),
      _master(master),
      _bucketExecutor(bucketExecutor),
      _bucketSpace(bucketSpace),
      _stopped(false),
      _startedCount(0),
      _executedCount(0)
{ }

CompactionJob::~CompactionJob() = default;

bool
CompactionJob::inSync() const {
    return _executedCount == _startedCount;
}

void
CompactionJob::onStop() {
    _stopped = true;
    while ( ! inSync() ) {
        std::this_thread::sleep_for(1ms);
    }
}

} // namespace proton
