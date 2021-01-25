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

using search::DocumentMetaData;
using search::LidUsageStats;
using storage::spi::makeBucketTask;
using storage::spi::Bucket;
using vespalib::makeLambdaTask;

namespace proton::lidspace {

bool
CompactionJob::scanDocuments(const LidUsageStats &stats)
{
    if (_scanItr->valid()) {
        DocumentMetaData document = getNextDocument(stats, false);
        if (document.valid()) {
            Bucket metaBucket(document::Bucket(_bucketSpace, document.bucketId));
            IDestructorCallback::SP context = _moveOpsLimiter->beginOperation();
            auto failed = _bucketExecutor.execute(metaBucket, makeBucketTask([this, meta=document, opsTracker=std::move(context)] (const Bucket & bucket, std::shared_ptr<IDestructorCallback> onDone) {
                assert(bucket.getBucketId() == meta.bucketId);
                using DoneContext = vespalib::KeepAlive<std::pair<IDestructorCallback::SP, IDestructorCallback::SP>>;
                moveDocument(meta, std::make_shared<DoneContext>(std::make_pair(std::move(opsTracker), std::move(onDone))));
            }));
            if (failed) return false;
            if (isBlocked(BlockedReason::OUTSTANDING_OPS)) {
                return true;
            }
        }
    }
    return scanDocumentsPost();
}

void
CompactionJob::moveDocument(const search::DocumentMetaData & meta, std::shared_ptr<IDestructorCallback> context) {
    // The real lid must be sampled in the master thread.
    //TODO remove target lid from createMoveOperation interface
    auto op = _handler.createMoveOperation(meta, 0);
    if (!op || !op->getDocument()) return;
    // Early detection and force md5 calculation outside of master thread
    if (meta.gid != op->getDocument()->getId().getGlobalId()) return;

    _master.execute(makeLambdaTask([this, metaThen=meta, moveOp=std::move(op), onDone=std::move(context)]() {
        search::DocumentMetaData metaNow = _handler.getMetaData(metaThen.lid);
        if (metaNow.lid != metaThen.lid) return;
        if (metaNow.bucketId != metaThen.bucketId) return;
        if (metaNow.gid != moveOp->getDocument()->getId().getGlobalId()) return;

        uint32_t lowestLid = _handler.getLidStatus().getLowestFreeLid();
        if (lowestLid >= metaNow.lid) return;
        moveOp->setTargetLid(lowestLid);
        _opStorer.appendOperation(*moveOp, onDone);
        _handler.handleMove(*moveOp, std::move(onDone));
    }));
}

CompactionJob::CompactionJob(const DocumentDBLidSpaceCompactionConfig &config,
                             ILidSpaceCompactionHandler &handler,
                             IOperationStorer &opStorer,
                             IThreadService & master,
                             BucketExecutor & bucketExecutor,
                             IDiskMemUsageNotifier &diskMemUsageNotifier,
                             const BlockableMaintenanceJobConfig &blockableConfig,
                             IClusterStateChangedNotifier &clusterStateChangedNotifier,
                             bool nodeRetired,
                             document::BucketSpace bucketSpace)
    : LidSpaceCompactionJobBase(config, handler, opStorer, diskMemUsageNotifier,
                                blockableConfig, clusterStateChangedNotifier, nodeRetired),
      _master(master),
      _bucketExecutor(bucketExecutor),
      _bucketSpace(bucketSpace)
{
}

CompactionJob::~CompactionJob() {
    _bucketExecutor.sync();
}

} // namespace proton
