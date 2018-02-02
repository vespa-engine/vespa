// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmover.h"
#include "htmltable.h"
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/storage/common/bucketmessages.h>
#include <vespa/storage/common/content_bucket_space_repo.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/storageutil/log.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <thread>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".bucketmover");

namespace storage::bucketmover {

BucketMover::BucketMover(const config::ConfigUri & configUri,
                         ServiceLayerComponentRegister& reg)
    : StorageLink("Bucket mover"),
      Runnable(),
      framework::HtmlStatusReporter("diskbalancer", "Disk balancer"),
      _component(reg, "diskbalancer"),
      _config(new vespa::config::content::core::StorBucketmoverConfig()),
      _cycleCount(0),
      _nextRun(0),
      _configFetcher(configUri.getContext()),
      _diskDistribution(currentDiskDistribution()),
      _maxSleepTime(60 * 60)
{
    if (!configUri.empty()) {
        using vespa::config::content::core::StorBucketmoverConfig;
        _configFetcher.subscribe<StorBucketmoverConfig>(configUri.getConfigId(), this);
        _configFetcher.start();
    }
    _component.registerStatusPage(*this);
}

BucketMover::~BucketMover()
{
    if (_thread) {
        LOG(error, "BucketMover deleted without calling close() first");
       onClose();
    }
    closeNextLink();
}


void BucketMover::onDoneInit()
{
    framework::MilliSecTime maxProcessingTime(60 * 1000);
    framework::MilliSecTime waitTime(_maxSleepTime * 1000);
    _thread = _component.startThread(*this, maxProcessingTime, waitTime);
}

void
BucketMover::onClose()
{
    // Avoid getting config during shutdown
    _configFetcher.close();
    // Close thread to ensure we don't send anything more down after
    if (_thread) {
        _thread->interruptAndJoin(&_wait);
        LOG(debug, "Bucket mover worker thread closed.");
        _thread.reset();
    }
}

void
BucketMover::signal()
{
    vespalib::MonitorGuard monitor(_wait);
    monitor.signal();
}

framework::SecondTime
BucketMover::calculateWaitTimeOfNextRun() const
{
    // _wait lock should have been taken by caller

    // If we haven't tried running at all, run fast to get statistics
    if (_history.empty()) {
        return framework::SecondTime(_config->minimumRecheckIntervalInSeconds);
    }

    // If we have a previous run, assuming our situation haven't changed
    // much from that one. Use it to calculate time.
    const RunStatistics& lastRun(_history.front());

    // If there are few buckets in wrong place, don't bother rechecking
    // often.
    if (lastRun.getWronglyPlacedRatio() < 0.01) {
        return framework::SecondTime(_config->maximumRecheckIntervalInSeconds);
    }

    // If a disk was disabled, wait for a good while.
    for (uint32_t i = 0; i < lastRun._diskData.size(); ++i) {
        if (lastRun._diskData[i]._diskDisabled) {
            return framework::SecondTime(_config->maximumRecheckIntervalInSeconds / 2);
        }
    }

    return framework::SecondTime(_config->minimumRecheckIntervalInSeconds);
}

void
BucketMover::startNewRun()
{
    // If not in a run but time to start another one, do so
    LOG(debug, "Starting new move cycle at time %s.",
        _component.getClock().getTimeInSeconds().toString().c_str());
    // TODO consider if we should invoke bucket moving across all bucket spaces. Not likely to ever be needed.
    // If so, we have to spawn off an individual Run per space, as it encompasses
    // both a (disk) distribution and a bucket database.
    _currentRun = std::make_unique<bucketmover::Run>(
            _component.getBucketSpaceRepo().get(document::FixedBucketSpaces::default_space()),
            *_component.getStateUpdater().getReportedNodeState(),
            _component.getIndex(),
            _component.getClock());
}

void
BucketMover::queueNewMoves()
{
    // If we have too few pending, send some new moves, if there are more
    // moves to perform.
    while (_pendingMoves.size() < uint32_t(_config->maxPending)) {
        Move nextMove = _currentRun->getNextMove();

        // If no more moves to do, stop attempting to send more.
        if (!nextMove.isDefined()) {
            break;
        }
        _pendingMoves.push_back(nextMove);
        auto cmd = std::make_shared<BucketDiskMoveCommand>(
                nextMove.getBucket(), nextMove.getSourceDisk(), nextMove.getTargetDisk());
        cmd->setPriority(nextMove.getPriority());
        _newMoves.push_back(cmd);
    }
}

void
BucketMover::finishCurrentRun()
{
    RunStatistics stats = _currentRun->getStatistics();
    if (_currentRun->aborted()) {
        LOG(debug, "Completed aborted bucket move run: %s",
            stats.toString().c_str());
    } else {
        // If current run is completed, note so in log, and move
        // run to history track.
        LOG(debug, "Completed bucket move run: %s",
            stats.toString().c_str());

        _history.push_front(stats);
        if (_history.size() > uint32_t(_config->maxHistorySize)) {
            _history.pop_back();
        }
        _nextRun = _component.getClock().getTimeInSeconds() +
                   calculateWaitTimeOfNextRun();
    }

    _currentRun.reset();
    ++_cycleCount;
}

void
BucketMover::sendNewMoves()
{
    for (auto& move : _newMoves) {
        LOG(debug, "Moving bucket: %s", move->toString().c_str());
        sendDown(move);

        // Be able to sleep a bit between moves for debugging to see
        // what is happening. (Cannot use wait() here as reply of
        // message sent will signal the monitor)
        if (_config->operationDelay != 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(_config->operationDelay));
        }
    }

    _newMoves.clear();
}

bool
BucketMover::tick()
{
    {
        vespalib::MonitorGuard monitor(_wait);

        framework::SecondTime currentTime(_component.getClock().getTimeInSeconds());

        if (!_currentRun) {
            if (currentTime >= _nextRun) {
                startNewRun();
            } else {
                return false;
            }
        }

        queueNewMoves();

        if (_newMoves.empty()) {
            if (_pendingMoves.empty()) {
                finishCurrentRun();
                return true;
            } else {
                return false;
            }
        }
    }

    // Send delayed after monitor has been unlocked, such that
    // incoming responses can grab lock. (Response might come back
    // in this thread on errors)
    sendNewMoves();
    return true;
}

void
BucketMover::run(framework::ThreadHandle& thread)
{
    while (!thread.interrupted()) {
        thread.registerTick(framework::PROCESS_CYCLE);
        if (!tick()) {
            vespalib::MonitorGuard monitor(_wait);
            monitor.wait(1000);
        }
    }
}

void
BucketMover::configure(std::unique_ptr<vespa::config::content::core::StorBucketmoverConfig> config)
{
    vespalib::MonitorGuard monitor(_wait);
    if (config->minimumRecheckIntervalInSeconds < 0) {
        throw config::InvalidConfigException(
                "Minimum recheck interval must be a positive value",
                VESPA_STRLOC);
    }
    if (config->maximumRecheckIntervalInSeconds
            < config->minimumRecheckIntervalInSeconds) {
        throw config::InvalidConfigException(
                "Maximum recheck interval must be equal or greater "
                "to minimum recheck interval",
                VESPA_STRLOC);
    }
    if (config->bucketIterationChunk < 1) {
        throw config::InvalidConfigException(
                "Bucket iteration chunk must be a positive number",
                VESPA_STRLOC);
    }
    if (config->maxTargetFillRateAboveAverage < 0
        || config->maxTargetFillRateAboveAverage > 1.0)
    {
        throw config::InvalidConfigException(
                "Max target fill rate above average must be in the range 0-1",
                VESPA_STRLOC);
    }
    if (config->maxPending < 1) {
        throw config::InvalidConfigException(
                "Cannot have less than 1 max pending", VESPA_STRLOC);
    }
    if (config->maxHistorySize < 1) {
        throw config::InvalidConfigException(
                "Cannot have less than 1 max history size", VESPA_STRLOC);
    }
    if (config->operationDelay > 0) {
        LOG(warning, "Operation delay debug option enabled. Slows down bucket "
                     "moving. Should only be used in testing where we want to "
                     "slow down the operation to manually inspect it during "
                     "the run.");
    }
    _config = std::move(config);
    while (_history.size() > uint32_t(_config->maxHistorySize)) {
        _history.pop_back();
    }
}

bool
BucketMover::onInternalReply(
        const std::shared_ptr<api::InternalReply>& internalReply)
{
    // We only care about move disk bucket replies
    auto reply = std::dynamic_pointer_cast<BucketDiskMoveReply>(internalReply);
    if (!reply) {
        return false;
    }

    // Warn if we see move replies outside of a run. Should not be possible.
    vespalib::MonitorGuard monitor(_wait);
    if (!_currentRun) {
        LOG(warning, "Got a bucket disk move reply while no run is active. "
                     "This should not happen, as runs should stay active until "
                     "all requests are answered.");
        return true;
    }
    // Match move against pending ones
    Move move;
    for (auto it = _pendingMoves.begin(); it != _pendingMoves.end(); ++it) {
        if (it->getBucket() == reply->getBucket()
            && it->getSourceDisk() == reply->getSrcDisk()
            && it->getTargetDisk() == reply->getDstDisk())
        {
            move = *it;
            _pendingMoves.erase(it);
            break;
        }
    }
        // Warn if it wasn't supposed to be active
    if (!move.isDefined()) {
        LOG(warning, "Got a bucket disk move reply which wasn't registered "
                     "as pending. This should not happen.");
        return true;
    }
        // Tag move completed in run.
    if (reply->getResult().success()) {
        _currentRun->moveOk(move);
    } else if (reply->getResult().getResult()
                    == api::ReturnCode::BUCKET_NOT_FOUND
            || reply->getResult().getResult()
                    == api::ReturnCode::BUCKET_DELETED)
    {
        _currentRun->moveFailedBucketNotFound(move);
    } else {
        _currentRun->moveFailed(move);
        LOGBP(debug, "Failed %s: %s",
              move.toString().c_str(), reply->getResult().toString().c_str());
    }
    monitor.broadcast();
    return true;
}

// TODO if we start supporting disk moves for other spaces than the default space
// we also have to check all disk distributions here.
void
BucketMover::storageDistributionChanged()
{
    // Verify that the actual disk distribution changed, if not ignore
    lib::Distribution::DiskDistribution newDistr(currentDiskDistribution());

    if (_diskDistribution == newDistr) {
        return;
    }

    vespalib::MonitorGuard monitor(_wait);
    if (_currentRun) {
        LOG(info, "Aborting bucket mover run as disk distribution changed "
                  "from %s to %s.",
            lib::Distribution::getDiskDistributionName(_diskDistribution).c_str(),
            lib::Distribution::getDiskDistributionName(newDistr).c_str());
        _currentRun->abort();
    } else {
        LOG(info, "Regathering state as disk distribution changed "
                  "from %s to %s.",
            lib::Distribution::getDiskDistributionName(_diskDistribution).c_str(),
            lib::Distribution::getDiskDistributionName(newDistr).c_str());
    }
    _diskDistribution = newDistr;
    _nextRun = framework::SecondTime(0);
}

lib::Distribution::DiskDistribution BucketMover::currentDiskDistribution() const {
    auto distribution = _component.getBucketSpaceRepo().get(document::FixedBucketSpaces::default_space()).getDistribution();
    return distribution->getDiskDistribution();
}

bool BucketMover::isWorkingOnCycle() const {
    vespalib::MonitorGuard monitor(_wait);
    return (_currentRun.get() != nullptr);
}

uint32_t BucketMover::getCycleCount() const {
    vespalib::MonitorGuard monitor(_wait);
    return _cycleCount;
}

void
BucketMover::print(std::ostream& out, bool verbose,
                   const std::string& indent) const
{
    (void) verbose; (void) indent;
    vespalib::MonitorGuard monitor(_wait);
    out << "BucketMover() {";
    if (_currentRun) {
        out << "\n" << indent << "  ";
        _currentRun->print(out, verbose, indent + "  ");
    } else {
        out << "\n" << indent << "  No current run.";
    }
    if (verbose && !_history.empty()) {
        out << "\n" << indent << "  History:";
        for (auto& entry : _history) {
            out << "\n" << indent << "    ";
            entry.print(out, true, indent + "    ");
        }
    }
    out << "\n" << indent << "}";
}

void
BucketMover::reportHtmlStatus(std::ostream& out,
                              const framework::HttpUrlPath&) const
{
    vespalib::MonitorGuard monitor(_wait);
    if (_history.empty()) {
        out << "<h2>Status after last run</h2>\n";
        out << "<p>No run completed yet. Current status unknown.</p>\n";
    } else {
        printCurrentStatus(out, *_history.begin());
    }
    out << "<h2>Current move cycle</h2>\n";
    if (_currentRun) {
        printRunHtml(out, *_currentRun);
        if (_currentRun->getPendingMoves().empty()) {
            out << "<blockquote>No pending moves.</blockquote>\n";
        } else {
            out << "<blockquote>Pending bucket moves:<ul>\n";
            for (auto& entry : _currentRun->getPendingMoves()) {
                out << "<li>" << entry << "</li>\n";
            }
            out << "</ul></blockquote>\n";
        }
    } else {
        out << "<p>\n"
            << "No bucket move cycle currently running. ";
        framework::SecondTime currentTime(
                _component.getClock().getTimeInSeconds());
        if (_nextRun <= currentTime) {
            if (_thread) {
                out << "Next run to start immediately.";
                    // Wake up thread, so user sees it starts immediately :)
                monitor.signal();
            } else {
                out << "Waiting for node to finish initialization before "
                    << "starting run.";
            }
        } else {
            out << "Next run scheduled to run";
            framework::SecondTime diff(_nextRun - currentTime);
            if (diff < framework::SecondTime(24 * 60 * 60)) {
                out << " in " << diff.toString(framework::DIFFERENCE);
            } else {
                out << " at time " << _nextRun;
            }
            out << ".";
        }
        out << "\n</p>\n";
    }
    if (!_history.empty()) {
        out << "<h2>Statistics from previous bucket mover cycles</h2>\n";
        for (auto& entry : _history) {
            printRunStatisticsHtml(out, entry);
        }
    }
}

void
BucketMover::printCurrentStatus(std::ostream& out,
                                const RunStatistics& rs) const
{
    framework::SecondTime currentTime(_component.getClock().getTimeInSeconds());
    out << "<h2>Status after last run ("
        << (currentTime - rs._endTime).toString(framework::DIFFERENCE)
        << " ago)</h2>\n"
        << "<p>Disk distribution: "
        << lib::Distribution::getDiskDistributionName(_diskDistribution)
        << "</p>\n";
    out << "<p>This is the status from the last completed bucket database scan "
        << "done by the bucket mover. After starting storage, or after "
        << "configuration changes, a single scan is always done without "
        << "actually attempting to move anything, just to get status updated "
        << "quickly. During a move cycle, the data shown for the current cycle "
        << "will be more recently updated, but will only represent a part of "
        << "the bucket database.</p>\n";
    HtmlTable table("Disk");
    table.addColumnHeader("Real partition byte usage", 3);
    ByteSizeColumn diskSpaceUsed("Used", &table);
    ByteSizeColumn diskSpaceTotal("Total", &table);
    DoubleColumn diskSpaceFillRate("Fill rate", " %", &table);
    diskSpaceFillRate.addColorLimit(85, Column::LIGHT_GREEN);
    diskSpaceFillRate.addColorLimit(95, Column::LIGHT_YELLOW);
    diskSpaceFillRate.addColorLimit(100, Column::LIGHT_RED);
    diskSpaceFillRate.setTotalAsAverage();
    table.addColumnHeader("Buckets in directory", 2);
    LongColumn bucketCount("Count", "", &table);
    PercentageColumn bucketCountPart("Part", 0, &table);
    table.addColumnHeader("Total document size directory", 2);
    ByteSizeColumn documentSize("Size", &table);
    PercentageColumn documentSizePart("Part", 0, &table);
    table.addColumnHeader("Buckets on correct disk", 2);
    LongColumn bucketsCorrectDisk("Count", "", &table);
    DoubleColumn bucketsCorrectDiskPart("Part", " %", &table);
    bucketsCorrectDiskPart.setTotalAsAverage();
    bucketsCorrectDiskPart.addColorLimit(95, Column::LIGHT_YELLOW);
    bucketsCorrectDiskPart.addColorLimit(100, Column::LIGHT_GREEN);
    for (uint32_t i=0; i<rs._diskData.size(); ++i) {
        table.addRow(i);
        // Ignore disks down
        bucketCount[i] = rs.getBucketCount(i, true);
        bucketCountPart[i] = bucketCount[i];
        documentSize[i] = rs._diskData[i]._bucketSize;
        documentSizePart[i] = documentSize[i];
        bucketsCorrectDisk[i] = rs.getBucketCount(i, false);
        bucketsCorrectDiskPart[i] = 100.0 * rs.getBucketCount(i, false)
                                          / rs.getBucketCount(i, true);
    }
    table.addTotalRow("Total");
    table.print(out);

    MATRIX_PRINT("Buckets on wrong disk", _bucketsLeftOnWrongDisk, rs);
}

void
BucketMover::printRunHtml(std::ostream& out, const bucketmover::Run& runner) const
{
    printRunStatisticsHtml(out, runner.getStatistics());
}

void
BucketMover::printRunStatisticsHtml(std::ostream& out,
                                    const RunStatistics& rs) const
{
    rs.print(out, true, "");
}

}
