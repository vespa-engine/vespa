// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_space_compaction_job.h"
#include "i_document_scan_iterator.h"
#include "i_lid_space_compaction_handler.h"
#include "i_operation_storer.h"
#include "i_disk_mem_usage_notifier.h"
#include "iclusterstatechangednotifier.h"
#include "remove_operations_rate_tracker.h"
#include <vespa/searchcore/proton/feedoperation/compact_lid_space_operation.h>
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vespa/persistence/spi/bucket_tasks.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/gate.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.lidspace.compactionjob");

using search::DocumentMetaData;
using search::LidUsageStats;
using storage::spi::makeBucketTask;
using storage::spi::Bucket;
using vespalib::RetainGuard;
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
        if (_job->stopped()) return;
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
        DocumentMetaData document = getNextDocument(stats);
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
    if (job->stopped()) return; //TODO Remove once lidtracker is no longer in use.
    // The real lid must be sampled in the master thread.
    //TODO remove target lid from createMoveOperation interface
    auto op = job->_handler->createMoveOperation(metaThen, 0);
    if (!op || !op->getDocument()) return;
    // Early detection and force md5 calculation outside of master thread
    if (metaThen.gid != op->getDocument()->getId().getGlobalId()) return;

    auto & master = job->_master;
    if (job->stopped()) return;
    master.execute(makeLambdaTask([self=std::move(job), meta=metaThen, moveOp=std::move(op), onDone=std::move(context)]() mutable {
        if (self->stopped()) return;
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
    : BlockableMaintenanceJob("lid_space_compaction." + handler->getName(),
                              config.getDelay(), config.getInterval(), blockableConfig),
      IDiskMemUsageListener(),
      IClusterStateChangedHandler(),
      std::enable_shared_from_this<CompactionJob>(),
      _cfg(config),
      _handler(std::move(handler)),
      _opStorer(opStorer),
      _scanItr(),
      _diskMemUsageNotifier(diskMemUsageNotifier),
      _clusterStateChangedNotifier(clusterStateChangedNotifier),
      _ops_rate_tracker(std::make_shared<RemoveOperationsRateTracker>(config.get_remove_batch_block_rate(),
                                                                      config.get_remove_block_rate())),
      _is_disabled(false),
      _shouldCompactLidSpace(false),
      _master(master),
      _bucketExecutor(bucketExecutor),
      _dbRetainer(std::move(dbRetainer)),
      _bucketSpace(bucketSpace)
{
    _diskMemUsageNotifier.addDiskMemUsageListener(this);
    _clusterStateChangedNotifier.addClusterStateChangedHandler(this);
    if (nodeRetired) {
        setBlocked(BlockedReason::CLUSTER_STATE);
    }
    _handler->set_operation_listener(_ops_rate_tracker);
}

CompactionJob::~CompactionJob() {
    _clusterStateChangedNotifier.removeClusterStateChangedHandler(this);
    _diskMemUsageNotifier.removeDiskMemUsageListener(this);
}

std::shared_ptr<CompactionJob>
CompactionJob::create(const DocumentDBLidSpaceCompactionConfig &config,
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
{
    return std::shared_ptr<CompactionJob>(
            new CompactionJob(config, std::move(dbRetainer), std::move(handler), opStorer, master, bucketExecutor,
                              diskMemUsageNotifier, blockableConfig, clusterStateChangedNotifier, nodeRetired, bucketSpace),
            [&master](auto job) {
                auto failed = master.execute(makeLambdaTask([job]() { delete job; }));
                assert(!failed);
            });
}

DocumentMetaData
CompactionJob::getNextDocument(const LidUsageStats &stats)
{
    return _scanItr->next(std::max(stats.getLowestFreeLid(), stats.getUsedLids()));
}

bool
CompactionJob::run()
{
    if (isBlocked()) {
        return true; // indicate work is done since no work can be done
    }
    if (remove_batch_is_ongoing()) {
        // Note that we don't set the job as blocked as the decision to un-block it is not driven externally.
        if (!_is_disabled) {
            LOG(info, "%s: Lid space compaction is disabled while remove batch (delete buckets) is ongoing",
                _handler->getName().c_str());
            _is_disabled = true;
        }
        return true;
    }
    if (remove_is_ongoing()) {
        // Note that we don't set the job as blocked as the decision to un-block it is not driven externally.
        if (!_is_disabled) {
            LOG(info, "%s: Lid space compaction is disabled while remove operations are ongoing",
                _handler->getName().c_str());
            _is_disabled = true;
        }
        return true;
    }
    if (_is_disabled) {
        LOG(info, "%s: Lid space compaction is re-enabled as remove operations are no longer ongoing",
            _handler->getName().c_str());
        _is_disabled = false;
    }

    if (_scanItr && !_scanItr->valid()) {
        bool numPending = getLimiter().numPending();
        if (numPending > 0) {
            // We must wait to decide if a rescan is necessary until all operations are completed
            return false;
        }
        LidUsageStats stats = _handler->getLidStatus();
        if (shouldRestartScanDocuments(stats)) {
            _scanItr = _handler->getIterator();
        } else {
            _scanItr = IDocumentScanIterator::UP();
            _shouldCompactLidSpace = true;
            return false;
        }
    }

    LidUsageStats stats = _handler->getLidStatus();
    if (_scanItr) {
        return scanDocuments(stats);
    } else if (_shouldCompactLidSpace) {
        compactLidSpace(stats);
    } else if (hasTooMuchLidBloat(stats)) {
        assert(!_scanItr);
        _scanItr = _handler->getIterator();
        return scanDocuments(stats);
    }
    return true;
}

bool
CompactionJob::remove_batch_is_ongoing() const
{
    return _ops_rate_tracker->remove_batch_above_threshold();
}

bool
CompactionJob::remove_is_ongoing() const
{
    return _ops_rate_tracker->remove_above_threshold();
}

bool
CompactionJob::hasTooMuchLidBloat(const LidUsageStats &stats) const
{
    return ((stats.getLidBloat() >= _cfg.getAllowedLidBloat()) &&
            (stats.getLidBloatFactor() >= _cfg.getAllowedLidBloatFactor()) &&
            (stats.getLidLimit() > stats.getLowestFreeLid()));
}

bool
CompactionJob::shouldRestartScanDocuments(const LidUsageStats &stats) const
{
    return ((stats.getUsedLids() + _cfg.getAllowedLidBloat()) < stats.getHighestUsedLid()) &&
           (stats.getLowestFreeLid() < stats.getHighestUsedLid());
}

void
CompactionJob::compactLidSpace(const LidUsageStats &stats)
{
    uint32_t wantedLidLimit = stats.getHighestUsedLid() + 1;
    CompactLidSpaceOperation op(_handler->getSubDbId(), wantedLidLimit);
    vespalib::Gate gate;
    auto commit_result = _opStorer.appendAndCommitOperation(op, std::make_shared<vespalib::GateCallback>(gate));
    gate.await();
    _handler->handleCompactLidSpace(op, std::make_shared<vespalib::KeepAlive<decltype(commit_result)>>(std::move(commit_result)));
    EventLogger::lidSpaceCompactionComplete(_handler->getName(), wantedLidLimit);
    _shouldCompactLidSpace = false;
}

void
CompactionJob::notifyDiskMemUsage(DiskMemUsageState state)
{
    // Called by master write thread
    internalNotifyDiskMemUsage(state);
}

void
CompactionJob::notifyClusterStateChanged(const std::shared_ptr<IBucketStateCalculator> &newCalc)
{
    // Called by master write thread
    bool nodeRetired = newCalc->nodeRetired();
    if (!nodeRetired) {
        if (isBlocked(BlockedReason::CLUSTER_STATE)) {
            LOG(info, "%s: Lid space compaction is un-blocked as node is no longer retired", _handler->getName().c_str());
            unBlock(BlockedReason::CLUSTER_STATE);
        }
    } else if (!isBlocked(BlockedReason::CLUSTER_STATE)) {
        LOG(info, "%s: Lid space compaction is blocked as node is retired", _handler->getName().c_str());
        setBlocked(BlockedReason::CLUSTER_STATE);
    }
}

} // namespace proton
