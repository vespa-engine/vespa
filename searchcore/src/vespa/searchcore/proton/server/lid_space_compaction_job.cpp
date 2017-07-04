// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "i_disk_mem_usage_notifier.h"
#include "iclusterstatechangednotifier.h"
#include "imaintenancejobrunner.h"
#include "lid_space_compaction_job.h"
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchlib/common/idestructorcallback.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.lid_space_compaction_job");

using search::DocumentMetaData;
using search::LidUsageStats;

namespace proton {

bool
LidSpaceCompactionJob::hasTooMuchLidBloat(const LidUsageStats &stats) const
{
    return (stats.getLidBloat() >= _cfg.getAllowedLidBloat() &&
            stats.getLidBloatFactor() >= _cfg.getAllowedLidBloatFactor() &&
            stats.getLidLimit() > stats.getLowestFreeLid());
}

bool
LidSpaceCompactionJob::shouldRestartScanDocuments(const LidUsageStats &stats) const
{
    return (stats.getUsedLids() + _cfg.getAllowedLidBloat()) < stats.getHighestUsedLid() &&
        stats.getLowestFreeLid() < stats.getHighestUsedLid();
}

DocumentMetaData
LidSpaceCompactionJob::getNextDocument(const LidUsageStats &stats)
{
    DocumentMetaData document =
        _scanItr->next(std::max(stats.getLowestFreeLid(), stats.getUsedLids()),
                       _cfg.getMaxDocsToScan(), _retryFrozenDocument);
    _retryFrozenDocument = false;
    return document;
}

bool
LidSpaceCompactionJob::scanDocuments(const LidUsageStats &stats)
{
    if (_scanItr->valid()) {
        DocumentMetaData document = getNextDocument(stats);
        if (document.valid()) {
            IFrozenBucketHandler::ExclusiveBucketGuard::UP bucketGuard = _frozenHandler.acquireExclusiveBucket(document.bucketId);
            if ( ! bucketGuard ) {
                // the job is blocked until the bucket for this document is thawed
                setBlocked(BlockedReason::FROZEN_BUCKET);
                _retryFrozenDocument = true;
                return true;
            } else {
                MoveOperation::UP op = _handler.createMoveOperation(document, stats.getLowestFreeLid());
                _opStorer.storeOperation(*op);
                _handler.handleMove(*op, _moveOpsLimiter->beginOperation());
                if (isBlocked(BlockedReason::OUTSTANDING_OPS)) {
                    return true;
                }
            }
        }
    }
    if (!_scanItr->valid()){
        if (shouldRestartScanDocuments(_handler.getLidStatus())) {
            _scanItr = _handler.getIterator();
        } else {
            _scanItr = IDocumentScanIterator::UP();
            _shouldCompactLidSpace = true;
        }
    }
    return false; // more work to do (scan documents or compact lid space)
}

void
LidSpaceCompactionJob::compactLidSpace(const LidUsageStats &stats)
{
    uint32_t wantedLidLimit = stats.getHighestUsedLid() + 1;
    CompactLidSpaceOperation op(_handler.getSubDbId(), wantedLidLimit);
    _opStorer.storeOperation(op);
    _handler.handleCompactLidSpace(op);
    EventLogger::lidSpaceCompactionComplete(_handler.getName(), wantedLidLimit);
    _shouldCompactLidSpace = false;
}

LidSpaceCompactionJob::LidSpaceCompactionJob(const DocumentDBLidSpaceCompactionConfig &config,
                                             ILidSpaceCompactionHandler &handler,
                                             IOperationStorer &opStorer,
                                             IFrozenBucketHandler &frozenHandler,
                                             IDiskMemUsageNotifier &diskMemUsageNotifier,
                                             const BlockableMaintenanceJobConfig &blockableConfig,
                                             IClusterStateChangedNotifier &clusterStateChangedNotifier,
                                             bool nodeRetired)
    : BlockableMaintenanceJob("lid_space_compaction." + handler.getName(),
            config.getDelay(), config.getInterval(), blockableConfig),
      _cfg(config),
      _handler(handler),
      _opStorer(opStorer),
      _frozenHandler(frozenHandler),
      _scanItr(),
      _retryFrozenDocument(false),
      _shouldCompactLidSpace(false),
      _diskMemUsageNotifier(diskMemUsageNotifier),
      _clusterStateChangedNotifier(clusterStateChangedNotifier)
{
    _diskMemUsageNotifier.addDiskMemUsageListener(this);
    _clusterStateChangedNotifier.addClusterStateChangedHandler(this);
    if (nodeRetired) {
        setBlocked(BlockedReason::CLUSTER_STATE);
    }
}

LidSpaceCompactionJob::~LidSpaceCompactionJob()
{
    _clusterStateChangedNotifier.removeClusterStateChangedHandler(this);
    _diskMemUsageNotifier.removeDiskMemUsageListener(this);
}

bool
LidSpaceCompactionJob::run()
{
    if (isBlocked()) {
        return true; // indicate work is done since no work can be done
    }
    LidUsageStats stats = _handler.getLidStatus();
    if (_scanItr) {
        return scanDocuments(stats);
    } else if (_shouldCompactLidSpace) {
        compactLidSpace(stats);
    } else if (hasTooMuchLidBloat(stats)) {
        assert(!_scanItr);
        _scanItr = _handler.getIterator();
        return scanDocuments(stats);
    }
    return true;
}

void
LidSpaceCompactionJob::notifyDiskMemUsage(DiskMemUsageState state)
{
    // Called by master write thread
    internalNotifyDiskMemUsage(state);
}

void
LidSpaceCompactionJob::notifyClusterStateChanged(const IBucketStateCalculator::SP &newCalc)
{
    // Called by master write thread
    bool nodeRetired = newCalc->nodeRetired();
    if (!nodeRetired) {
        if (isBlocked(BlockedReason::CLUSTER_STATE)) {
            LOG(info, "notifyClusterStateChanged(): Node is no longer retired -> lid space compaction job re-enabled");
            unBlock(BlockedReason::CLUSTER_STATE);
        }
    } else {
        LOG(info, "notifyClusterStateChanged(): Node is retired -> lid space compaction job disabled");
        setBlocked(BlockedReason::CLUSTER_STATE);
    }
}

} // namespace proton
