// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_space_compaction_job_base.h"
#include "imaintenancejobrunner.h"
#include "i_document_scan_iterator.h"
#include "i_lid_space_compaction_handler.h"
#include "i_operation_storer.h"
#include "remove_operations_rate_tracker.h"
#include "i_disk_mem_usage_notifier.h"
#include "iclusterstatechangednotifier.h"
#include <vespa/searchcore/proton/feedoperation/compact_lid_space_operation.h>
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/gate.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.lid_space_compaction_job");

using search::DocumentMetaData;
using search::LidUsageStats;

namespace proton {

bool
LidSpaceCompactionJobBase::hasTooMuchLidBloat(const LidUsageStats &stats) const
{
    return ((stats.getLidBloat() >= _cfg.getAllowedLidBloat()) &&
            (stats.getLidBloatFactor() >= _cfg.getAllowedLidBloatFactor()) &&
            (stats.getLidLimit() > stats.getLowestFreeLid()));
}

bool
LidSpaceCompactionJobBase::shouldRestartScanDocuments(const LidUsageStats &stats) const
{
    return ((stats.getUsedLids() + _cfg.getAllowedLidBloat()) < stats.getHighestUsedLid()) &&
            (stats.getLowestFreeLid() < stats.getHighestUsedLid());
}

DocumentMetaData
LidSpaceCompactionJobBase::getNextDocument(const LidUsageStats &stats, bool retryLastDocument)
{
    return _scanItr->next(std::max(stats.getLowestFreeLid(), stats.getUsedLids()), retryLastDocument);
}

void
LidSpaceCompactionJobBase::compactLidSpace(const LidUsageStats &stats)
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

bool
LidSpaceCompactionJobBase::remove_batch_is_ongoing() const
{
    return _ops_rate_tracker->remove_batch_above_threshold();
}

bool
LidSpaceCompactionJobBase::remove_is_ongoing() const
{
    return _ops_rate_tracker->remove_above_threshold();
}

LidSpaceCompactionJobBase::LidSpaceCompactionJobBase(const DocumentDBLidSpaceCompactionConfig &config,
                                                     std::shared_ptr<ILidSpaceCompactionHandler>  handler,
                                                     IOperationStorer &opStorer,
                                                     IDiskMemUsageNotifier &diskMemUsageNotifier,
                                                     const BlockableMaintenanceJobConfig &blockableConfig,
                                                     IClusterStateChangedNotifier &clusterStateChangedNotifier,
                                                     bool nodeRetired)
    : BlockableMaintenanceJob("lid_space_compaction." + handler->getName(),
                              config.getDelay(), config.getInterval(), blockableConfig),
      _cfg(config),
      _handler(std::move(handler)),
      _opStorer(opStorer),
      _scanItr(),
      _diskMemUsageNotifier(diskMemUsageNotifier),
      _clusterStateChangedNotifier(clusterStateChangedNotifier),
      _ops_rate_tracker(std::make_shared<RemoveOperationsRateTracker>(config.get_remove_batch_block_rate(),
                                                                      config.get_remove_block_rate())),
      _is_disabled(false),
      _shouldCompactLidSpace(false)
{
    _diskMemUsageNotifier.addDiskMemUsageListener(this);
    _clusterStateChangedNotifier.addClusterStateChangedHandler(this);
    if (nodeRetired) {
        setBlocked(BlockedReason::CLUSTER_STATE);
    }
    _handler->set_operation_listener(_ops_rate_tracker);
}

LidSpaceCompactionJobBase::~LidSpaceCompactionJobBase()
{
    _clusterStateChangedNotifier.removeClusterStateChangedHandler(this);
    _diskMemUsageNotifier.removeDiskMemUsageListener(this);
}

bool
LidSpaceCompactionJobBase::run()
{
    if (isBlocked()) {
        return true; // indicate work is done since no work can be done
    }
    LidUsageStats stats = _handler->getLidStatus();
    if (remove_batch_is_ongoing()) {
        // Note that we don't set the job as blocked as the decision to un-block it is not driven externally.
        LOG(info, "%s: Lid space compaction is disabled while remove batch (delete buckets) is ongoing",
            _handler->getName().c_str());
        _is_disabled = true;
        return true;
    }
    if (remove_is_ongoing()) {
        // Note that we don't set the job as blocked as the decision to un-block it is not driven externally.
        LOG(info, "%s: Lid space compaction is disabled while remove operations are ongoing",
            _handler->getName().c_str());
        _is_disabled = true;
        return true;
    }
    if (_is_disabled) {
        LOG(info, "%s: Lid space compaction is re-enabled as remove operations are no longer ongoing",
            _handler->getName().c_str());
        _is_disabled = false;
    }

    if (_scanItr && !_scanItr->valid()) {
        if (!inSync()) {
            return false;
        }
        if (shouldRestartScanDocuments(_handler->getLidStatus())) {
            _scanItr = _handler->getIterator();
        } else {
            _scanItr = IDocumentScanIterator::UP();
            _shouldCompactLidSpace = true;
            return false;
        }
    }

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

void
LidSpaceCompactionJobBase::notifyDiskMemUsage(DiskMemUsageState state)
{
    // Called by master write thread
    internalNotifyDiskMemUsage(state);
}

void
LidSpaceCompactionJobBase::notifyClusterStateChanged(const std::shared_ptr<IBucketStateCalculator> &newCalc)
{
    // Called by master write thread
    bool nodeRetired = newCalc->nodeRetired();
    if (!nodeRetired) {
        if (isBlocked(BlockedReason::CLUSTER_STATE)) {
            LOG(info, "%s: Lid space compaction is un-blocked as node is no longer retired", _handler->getName().c_str());
            unBlock(BlockedReason::CLUSTER_STATE);
        }
    } else {
        LOG(info, "%s: Lid space compaction is blocked as node is retired", _handler->getName().c_str());
        setBlocked(BlockedReason::CLUSTER_STATE);
    }
}

} // namespace proton
