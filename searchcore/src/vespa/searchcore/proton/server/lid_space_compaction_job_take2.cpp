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

bool
isSameDocument(const search::DocumentMetaData &a, const search::DocumentMetaData &b) {
    return (a.lid == b.lid) &&
           (a.bucketId == b.bucketId) &&
           (a.gid == b.gid) &&
           (a.timestamp ==
            b.timestamp); // Timestamp check can be removed once logic has proved itself in large scale.
}

}

class CompactionJob::MoveTask : public storage::spi::BucketTask {
public:
    MoveTask(std::shared_ptr<CompactionJob> job, const search::DocumentMetaData & meta, IDestructorCallback::SP opsTracker)
        : _job(std::move(job)),
          _meta(meta),
          _opsTracker(std::move(opsTracker))
    { }
    void run(const Bucket & bucket, IDestructorCallback::SP onDone) override {
        assert(bucket.getBucketId() == _meta.bucketId);
        using DoneContext = vespalib::KeepAlive<std::pair<IDestructorCallback::SP, IDestructorCallback::SP>>;
        CompactionJob::moveDocument(std::move(_job), _meta,
                                    std::make_shared<DoneContext>(std::make_pair(std::move(_opsTracker), std::move(onDone))));
    }
    void fail(const Bucket & bucket) override {
        assert(bucket.getBucketId() == _meta.bucketId);
        auto & master = _job->_master;
        if (_job->_stopped) return;
        master.execute(makeLambdaTask([job=std::move(_job)] { job->_scanItr.reset(); }));
    }
private:
    std::shared_ptr<CompactionJob> _job;
    const search::DocumentMetaData _meta;
    IDestructorCallback::SP        _opsTracker;
};

bool
CompactionJob::scanDocuments(const LidUsageStats &stats)
{
    if (_scanItr->valid()) {
        DocumentMetaData document = getNextDocument(stats, false);
        if (document.valid()) {
            Bucket metaBucket(document::Bucket(_bucketSpace, document.bucketId));
            _bucketExecutor.execute(metaBucket, std::make_unique<MoveTask>(shared_from_this(), document, getLimiter().beginOperation()));
            if (isBlocked(BlockedReason::OUTSTANDING_OPS)) {
                return true;
            }
        }
    }
    return false;
}

void
CompactionJob::moveDocument(std::shared_ptr<CompactionJob> job, const search::DocumentMetaData & metaThen,
                            std::shared_ptr<IDestructorCallback> context)
{
    if (job->_stopped) return; //TODO Remove once lidtracker is no longer in use.
    // The real lid must be sampled in the master thread.
    //TODO remove target lid from createMoveOperation interface
    auto op = job->_handler->createMoveOperation(metaThen, 0);
    if (!op || !op->getDocument()) return;
    // Early detection and force md5 calculation outside of master thread
    if (metaThen.gid != op->getDocument()->getId().getGlobalId()) return;

    auto & master = job->_master;
    if (job->_stopped) return;
    master.execute(makeLambdaTask([self=std::move(job), meta=metaThen, moveOp=std::move(op), onDone=std::move(context)]() mutable {
        if (self->_stopped.load(std::memory_order_relaxed)) return;
        self->completeMove(meta, std::move(moveOp), std::move(onDone));
    }));
}

void
CompactionJob::completeMove(const search::DocumentMetaData & metaThen, std::unique_ptr<MoveOperation> moveOp,
                            std::shared_ptr<IDestructorCallback> onDone)
{
    // Reread meta data as document might have been altered after move was initiated
    // If so it will fail the timestamp sanity check later on.
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
                             RetainGuard dbRetainer,
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
      std::enable_shared_from_this<CompactionJob>(),
      _master(master),
      _bucketExecutor(bucketExecutor),
      _dbRetainer(std::move(dbRetainer)),
      _bucketSpace(bucketSpace),
      _stopped(false)
{ }

CompactionJob::~CompactionJob() = default;

void
CompactionJob::onStop() {
    _stopped = true;
}

} // namespace proton
