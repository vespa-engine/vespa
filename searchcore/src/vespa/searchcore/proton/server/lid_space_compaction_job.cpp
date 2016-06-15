// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.lid_space_compaction_job");

#include "lid_space_compaction_job.h"
#include "ifrozenbuckethandler.h"
#include <vespa/searchcore/proton/common/eventlogger.h>

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
                setBlocked(true);
                _retryFrozenDocument = true;
                return true;
            } else {
                MoveOperation::UP op = _handler.createMoveOperation(document, stats.getLowestFreeLid());
                _opStorer.storeOperation(*op);
                _handler.handleMove(*op);
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
    if (LOG_WOULD_LOG(event)) {
        EventLogger::lidSpaceCompactionComplete(_handler.getName(), wantedLidLimit);
    }
    _shouldCompactLidSpace = false;
}

LidSpaceCompactionJob::LidSpaceCompactionJob(const DocumentDBLidSpaceCompactionConfig &config,
                                             ILidSpaceCompactionHandler &handler,
                                             IOperationStorer &opStorer,
                                             IFrozenBucketHandler &frozenHandler)
    : IMaintenanceJob("lid_space_compaction." + handler.getName(),
            config.getInterval(), config.getInterval()),
      _cfg(config),
      _handler(handler),
      _opStorer(opStorer),
      _frozenHandler(frozenHandler),
      _scanItr(),
      _retryFrozenDocument(false),
      _shouldCompactLidSpace(false)
{
}

bool
LidSpaceCompactionJob::run()
{
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

} // namespace proton
