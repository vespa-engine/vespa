// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_space_compaction_job.h"
#include "i_document_scan_iterator.h"
#include "ifrozenbuckethandler.h"
#include "i_lid_space_compaction_handler.h"
#include "i_operation_storer.h"
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>

using search::DocumentMetaData;
using search::LidUsageStats;

namespace proton {

bool
LidSpaceCompactionJob::scanDocuments(const LidUsageStats &stats)
{
    if (_scanItr->valid()) {
        DocumentMetaData document = getNextDocument(stats, _retryFrozenDocument);
        _retryFrozenDocument = false;
        if (document.valid()) {
            IFrozenBucketHandler::ExclusiveBucketGuard::UP bucketGuard = _frozenHandler.acquireExclusiveBucket(document.bucketId);
            if ( ! bucketGuard ) {
                // the job is blocked until the bucket for this document is thawed
                setBlocked(BlockedReason::FROZEN_BUCKET);
                _retryFrozenDocument = true;
                return true;
            } else {
                std::unique_ptr<MoveOperation> op = _handler.createMoveOperation(document, stats.getLowestFreeLid());
                if ( ! op ) {
                    return false;
                }
                vespalib::IDestructorCallback::SP context = _moveOpsLimiter->beginOperation();
                _opStorer.appendOperation(*op, context);
                _handler.handleMove(*op, std::move(context));
                if (isBlocked(BlockedReason::OUTSTANDING_OPS)) {
                    return true;
                }
            }
        }
    }
    return scanDocumentsPost();
}

LidSpaceCompactionJob::LidSpaceCompactionJob(const DocumentDBLidSpaceCompactionConfig &config,
                                             ILidSpaceCompactionHandler &handler,
                                             IOperationStorer &opStorer,
                                             IFrozenBucketHandler &frozenHandler,
                                             IDiskMemUsageNotifier &diskMemUsageNotifier,
                                             const BlockableMaintenanceJobConfig &blockableConfig,
                                             IClusterStateChangedNotifier &clusterStateChangedNotifier,
                                             bool nodeRetired)
    : LidSpaceCompactionJobBase(config, handler, opStorer, diskMemUsageNotifier,
                                blockableConfig, clusterStateChangedNotifier, nodeRetired),
      _frozenHandler(frozenHandler),
      _retryFrozenDocument(false)
{
}

LidSpaceCompactionJob::~LidSpaceCompactionJob() = default;

}
