// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketintegritychecker.h"
#include <vespa/storage/common/bucketmessages.h>
#include <vespa/storage/storageutil/log.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/storage/bucketdb/lockablemap.hpp>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".bucketintegritychecker");

using std::shared_ptr;

namespace storage {

namespace {
    /*
    std::string printDate(time_t time) {
        char date[26];
        struct tm datestruct;
        struct tm* datestructptr = gmtime_r(&time, &datestruct);
        assert(datestructptr);
        char* result = asctime_r(&datestruct, date);
        size_t size = strlen(result);
        while (size > 0) {
            bool stop = false;
            switch (result[size - 1]) {
                case '\n':
                case '\r':
                case '\f':
                case '\t':
                    --size;
                default:
                    stop = true;
                    break;
            }
            if (stop) break;
        }
        return std::string(result, size);
    }
    */

    std::string printMinutesOfDay(uint32_t minutesOfDay) {
        std::ostringstream ost;
        uint32_t hours = minutesOfDay / 60;
        uint32_t minutes = minutesOfDay % 60;
        ost << (hours >= 10 ? hours / 10 : 0) << hours % 10 << ':'
            << (minutes >= 10 ? minutes / 10 : 0) << minutes % 10;
        return ost.str();
    }

    std::string printRunState(SchedulingOptions::RunState state) {
        switch (state) {
            case SchedulingOptions::DONT_RUN:
                return "Not running";
            case SchedulingOptions::RUN_FULL:
                return "Running with full verification";
            case SchedulingOptions::RUN_CHEAP:
                return "Running with cheap verification";
            case SchedulingOptions::CONTINUE:
                return "Continuing any existing run";
            default:
                assert(false);
                abort();
        }
    }
}

void
SchedulingOptions::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    (void) verbose;
    std::string ind = indent + "                 ";
    out << "SchedulingOptions("
        << "Daily cycle " << printMinutesOfDay(_dailyCycleStart)
        << " - " << printMinutesOfDay(_dailyCycleStop)
        << ",\n" << ind << "Weekly cycle"
        << "\n" << ind << "  Monday - " << printRunState(_dailyStates[1])
        << "\n" << ind << "  Tuesday - " << printRunState(_dailyStates[2])
        << "\n" << ind << "  Wednesday - " << printRunState(_dailyStates[3])
        << "\n" << ind << "  Thursday - " << printRunState(_dailyStates[4])
        << "\n" << ind << "  Friday - " << printRunState(_dailyStates[5])
        << "\n" << ind << "  Saturday - " << printRunState(_dailyStates[6])
        << "\n" << ind << "  Sunday - " << printRunState(_dailyStates[0])
        << ",\n" << ind << "Max pending count " << _maxPendingCount
        << ",\n" << ind << "Min cycle time "
                << printMinutesOfDay(_minCycleTime.getTime() / 60)
        << ",\n" << ind << "Request delay" << _requestDelay << " seconds."
        << "\n" << indent << ")";
}

bool
BucketIntegrityChecker::DiskData::done() const
{
    return (state == DONE && failedRepairs.empty() && pendingCount == 0);
}

bool
BucketIntegrityChecker::DiskData::working() const
{
    return (state == IN_PROGRESS || !failedRepairs.empty()
            || pendingCount != 0);
}

// Utilities to find next bucket in bucket list from a possibly non-existing one
namespace {

struct NextEntryFinder {
    bool _first;
    uint8_t _disk;
    document::BucketId _last;
    std::unique_ptr<document::BucketId> _next;

    NextEntryFinder(const document::BucketId& id, uint8_t disk)
        : _first(true), _disk(disk), _last(id), _next() {}

    StorBucketDatabase::Decision operator()(document::BucketId::Type bucketId,
                                            StorBucketDatabase::Entry& entry)
    {
        document::BucketId bucket(document::BucketId::keyToBucketId(bucketId));

        if (entry.disk != _disk) {
            return StorBucketDatabase::CONTINUE;
        } else if (_first && bucket == _last) {
            _first = false;
            return StorBucketDatabase::CONTINUE;
        } else {
            _next.reset(new document::BucketId(bucket));
            return StorBucketDatabase::ABORT;
        }
    }
};

std::unique_ptr<document::BucketId> getNextId(StorBucketDatabase& database,
                                            const document::BucketId& last,
                                            uint8_t disk)
{
    NextEntryFinder proc(last, disk);
    database.each(proc, "BucketIntegrityChecker::getNextId", last.toKey());
    return std::move(proc._next);
}
} // End of anonymous namespace

document::BucketId
BucketIntegrityChecker::DiskData::iterate(StorBucketDatabase& bucketDatabase)
{
    static uint32_t i=0;
        // Resend failed buckets once in a while
    if (failedRepairs.size() > 0 && ++i % 10 == 9)
    {
        document::BucketId bid(failedRepairs.front());
        LOG(spam, "Scheduling next bucket %s from failed repairs list",
                  bid.toString().c_str());
        failedRepairs.pop_front();
        ++retriedBuckets;
        return bid;
    }
    if (state == NOT_STARTED) {
            // Guarantueed to be before all buckets.
        currentBucket = document::BucketId(0, 0);
    }
    if (state != DONE) {
        std::unique_ptr<document::BucketId> bid(
                getNextId(bucketDatabase, currentBucket, disk));
        if (bid.get()) {
            state = IN_PROGRESS;
            currentBucket = *bid;
            return currentBucket;
        } else {
            state = DONE;
        }
    }
        // If we didn't schedule repaired, but we ended up not having any other,
        // take repaired once anyways
    if (failedRepairs.size() > 0) {
        document::BucketId bid(failedRepairs.front());
        LOG(spam, "Done iterating, scheduling next bucket %s from failed "
                  "repairs list", bid.toString().c_str());
        failedRepairs.pop_front();
        ++retriedBuckets;
        return bid;
    }
    return document::BucketId(0, 0);
}

BucketIntegrityChecker::BucketIntegrityChecker(
        const config::ConfigUri & configUri,
        ServiceLayerComponentRegister& compReg)
    : StorageLinkQueued("Bucket integrity checker", compReg),
      Runnable(),
      framework::HtmlStatusReporter("bucketintegritychecker",
                                    "Bucket integrity checker"),
      _cycleCount(0),
      _status(),
      _lastCycleStart(0),
      _cycleStartBucketCount(0),
      _lastResponseTime(0),
      _lastCycleCompleted(true),
      _currentRunWithFullVerification(false),
      _verifyAllRepairs(false),
      _scheduleOptions(),
      _systemState(),
      _wait(),
      _configFetcher(configUri.getContext()),
      _maxThreadWaitTime(60 * 1000),
      _component(compReg, "bucketintegritychecker")
{
    LOG(debug, "Configuring bucket integrity checker to work with %u disks.",
               _component.getDiskCount());
    _status.resize(_component.getDiskCount());
    for (uint16_t i=0; i<_component.getDiskCount(); ++i) {
        _status[i].disk = i;
    }
    if (_status.size() == 0) {
        throw vespalib::IllegalStateException(
                "Cannot have storage with no disks.", VESPA_STRLOC);
    }
    // Register for config. Normally not critical, so catching config
    // exception allowing program to continue if missing/faulty config.
    try{
        if (!configUri.empty()) {
            _configFetcher.subscribe<vespa::config::content::core::StorIntegritycheckerConfig>(configUri.getConfigId(), this);
            _configFetcher.start();
        } else {
            LOG(info, "No config id specified. Using defaults rather than "
                      "config");
        }
    } catch (config::InvalidConfigException& e) {
        LOG(info, "Bucket Integrity Checker failed to load config '%s'. This "
                  "is not critical since it has sensible defaults: %s",
            configUri.getConfigId().c_str(), e.what());
    }
    _component.registerStatusPage(*this);
}

BucketIntegrityChecker::~BucketIntegrityChecker()
{
        // This can happen during unit testing
    if (StorageLink::getState() == StorageLink::OPENED) {
        LOG(error, "BucketIntegrityChecker deleted without calling close() "
                   "first");
        close();
        flush();
    }
    closeNextLink();
}

void
BucketIntegrityChecker::onClose()
{
        // Avoid getting config during shutdown
    _configFetcher.close();
        // Close thread to ensure we don't send anything more down after
    if (_thread.get() != 0) {
        LOG(debug, "Waiting for bucket integrity worker thread to close.");
        _thread->interruptAndJoin(&_wait);
        LOG(debug, "Bucket integrity worker thread closed.");
    }
    StorageLinkQueued::onClose();
}

void BucketIntegrityChecker::bump() const {
    vespalib::MonitorGuard monitor(_wait);
    monitor.signal();
}

bool BucketIntegrityChecker::isWorkingOnCycle() const {
    vespalib::MonitorGuard monitor(_wait);
    for (uint32_t i=0; i<_status.size(); ++i) {
        if (_status[i].working()) return true;
    }
    return (!_lastCycleCompleted);
}

uint32_t BucketIntegrityChecker::getCycleCount() const {
    vespalib::MonitorGuard monitor(_wait);
    return _cycleCount;
}

void
BucketIntegrityChecker::print(std::ostream& out, bool verbose,
              const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "BucketIntegrityChecker";
}

void
BucketIntegrityChecker::configure(
        std::unique_ptr<vespa::config::content::core::StorIntegritycheckerConfig> config)
{
    SchedulingOptions options;
    options._dailyCycleStart = config->dailycyclestart;
    options._dailyCycleStop = config->dailycyclestop;
    options._maxPendingCount = config->maxpending;
    options._minCycleTime = framework::SecondTime(60 * config->mincycletime);
    options._requestDelay = framework::SecondTime(config->requestdelay);
    std::string states = config->weeklycycle;
    if (states.size() != 7) {
        LOG(warning, "Not using integritychecker config: weeklycycle must "
                     "contain 7 characters, one for each week. Retrieved value:"
                     " '%s'.", states.c_str());
        return;
    }
    for (uint32_t i=0; i<7; ++i) {
        switch (states[i]) {
            case 'R':
                options._dailyStates[i] = SchedulingOptions::RUN_FULL; break;
            case 'r':
                options._dailyStates[i] = SchedulingOptions::RUN_CHEAP; break;
            case 'c':
                options._dailyStates[i] = SchedulingOptions::CONTINUE; break;
            case '-':
                options._dailyStates[i] = SchedulingOptions::DONT_RUN; break; default:
                LOG(warning, "Not using integritychecker config: weeklycycle "
                             "contained illegal character %c.", states[i]);
                return;
        }
    }
    if (options._dailyCycleStart >= 24*60) {
        LOG(warning, "Not using integritychecker config: dailycyclestart "
                     "is minutes since midnight and must be less than %u. "
                     "%u is out of range.", 24*60, options._dailyCycleStart);
        return;
    }
    if (options._dailyCycleStop >= 24*60) {
        LOG(warning, "Not using integritychecker config: dailycyclestart "
                     "is minutes since midnight and must be less than %u. "
                     "%u is out of range.", 24*60, options._dailyCycleStart);
        return;
    }
    if (options._maxPendingCount > 1024) {
        LOG(warning, "integritychecker config: Values above 1024 not "
                     "accepted. Got %u.", options._maxPendingCount);
        return;
    }
    if (options._requestDelay > framework::SecondTime(60*60)) {
        LOG(warning, "With a %" PRIu64 " second delay between each bucket "
                     "verification actually finishing a cycle will take a very "
                     "long time.",
            options._requestDelay.getTime());
    }
    vespalib::MonitorGuard monitor(_wait);
    if (options._minCycleTime.getMillis() < _maxThreadWaitTime) {
        _maxThreadWaitTime = framework::MilliSecTime(1000);
        monitor.signal();
    } else {
        _maxThreadWaitTime = framework::MilliSecTime(60 * 1000);
    }
    _scheduleOptions = options;
}

void BucketIntegrityChecker::onDoneInit()
{
    framework::MilliSecTime maxProcessingTime(60 * 1000);
    _thread = _component.startThread(
            *this, maxProcessingTime, _maxThreadWaitTime);
}

bool
BucketIntegrityChecker::onInternalReply(
        const std::shared_ptr<api::InternalReply>& internalReply)
{
        // We only care about repair bucket replies
    shared_ptr<RepairBucketReply> reply(
            std::dynamic_pointer_cast<RepairBucketReply>(internalReply));
    if (!reply.get()) return false;

    vespalib::MonitorGuard monitor(_wait);
    _lastResponseTime = _component.getClock().getTimeInSeconds();
    uint8_t disk = reply->getDisk();
    --_status[disk].pendingCount;
    LOG(spam, "Got repair reply for bucket %s: %s. %u messages still pending "
              "for disk %u. Bucket altered ? %s",
              reply->getBucketId().toString().c_str(),
              reply->getResult().toString().c_str(),
              _status[disk].pendingCount, disk,
              (reply->bucketAltered() ? "true" : "false"));
    if (reply->getResult().success()) {
        LOG(spam, "Repaired handled ok");
        ++_status[disk].checkedBuckets;
        if (_status[disk].done()) {
            bool completed = true;
            for (uint32_t i=0; i<_status.size(); ++i) {
                if (!_status[i].done()) {
                    completed = false;
                    break;
                }
            }
            _lastCycleCompleted = completed;
        }
    } else if (reply->getResult().isNonCriticalForIntegrityChecker()) {
        ++_status[disk].checkedBuckets;
        LOGBP(debug, "Failed to repair bucket %s due to aborting request. "
                     "Likely bucket split/join or storage shutting down: %s",
              reply->getBucketId().toString().c_str(),
              reply->getResult().toString().c_str());
    } else {
        _status[disk].failedRepairs.push_back(reply->getBucketId());
        LOGBP(warning, "Failed to perform maintenance on bucket %s, "
                       "scheduled to be retried: %s",
              reply->getBucketId().toString().c_str(),
              reply->getResult().toString().c_str());
    }
    if (_lastCycleCompleted) {
        LOG(info, "Completed bucket integrity check cycle");
    }
    monitor.signal();
    return true;
}

bool
BucketIntegrityChecker::onSetSystemState(
                const std::shared_ptr<api::SetSystemStateCommand>& cmd)
{
    vespalib::MonitorGuard monitor(_wait);
    _systemState = cmd->getSystemState();
    return false;
}


SchedulingOptions::RunState
BucketIntegrityChecker::getCurrentRunState(
        framework::SecondTime currentTime) const
{
    time_t currTime = currentTime.getTime();
    struct tm date;
    struct tm* dateptr = ::gmtime_r(&currTime, &date);
    assert(dateptr);
    (void) dateptr;
        // Get initial state based on weekday
    SchedulingOptions::RunState state(
            _scheduleOptions._dailyStates[date.tm_wday]);
    uint32_t minutesOfDay = 60 * date.tm_hour + date.tm_min;
    if ((
          _scheduleOptions._dailyCycleStart < _scheduleOptions._dailyCycleStop
          && _scheduleOptions._dailyCycleStart <= minutesOfDay
          && _scheduleOptions._dailyCycleStop > minutesOfDay
        ) || (
          _scheduleOptions._dailyCycleStart >= _scheduleOptions._dailyCycleStop
          && (_scheduleOptions._dailyCycleStart <= minutesOfDay
              || _scheduleOptions._dailyCycleStop > minutesOfDay)
        )
       )
    {   // If we're within region in day that we can run.
//std::cerr << "We're inside time boundary. Current time: " << minutesOfDay << " (" << printMinutesOfDay(minutesOfDay) << "). Running between " << _scheduleOptions._dailyCycleStart << " (" << printMinutesOfDay(_scheduleOptions._dailyCycleStart) << ") - " << _scheduleOptions._dailyCycleStop << " (" << printMinutesOfDay(_scheduleOptions._dailyCycleStop) << ")\n";
        if (state == SchedulingOptions::CONTINUE) {
//std::cerr << "We're in continue state.\n";
            // If we're in a continue state, set runstate if there's a current
            // run active that isn't completed yet, don't run otherwise.
            state = (_lastCycleCompleted
                        ? SchedulingOptions::DONT_RUN
                        : (_currentRunWithFullVerification
                            ? SchedulingOptions::RUN_FULL
                            : SchedulingOptions::RUN_CHEAP));
        } else if (state == SchedulingOptions::RUN_FULL ||
                   state == SchedulingOptions::RUN_CHEAP) {
            // If we're not currently in a run, and it's less than min cycle
            // time since last run started, we might not want to run yet.
            if (_lastCycleCompleted &&
                currentTime - _lastCycleStart < _scheduleOptions._minCycleTime)
            {
                // Unless we didn't do full verification last and want to
                // do full verification now, delay run.
                if (_currentRunWithFullVerification ||
                    state == SchedulingOptions::RUN_CHEAP)
                {
//std::cerr << "Tagging dont run since too little time passed since last run\n" << "current time: " << currentTime << ", last start " << _lastCycleStart << ", min cycle time " << _scheduleOptions._minCycleTime << "\n";
                    state = SchedulingOptions::DONT_RUN;
                } else {
//std::cerr << "We can start new run. Last cycle started at " << _lastCycleStart.toString() << " current time is " << currentTime.toString() << " and min cycle time is " << _scheduleOptions._minCycleTime << "\n";
                }
            } else {
//std::cerr << "Enough time passed? " << currentTime.toString() << " - " << _lastCycleStart.toString() << " >= " << _scheduleOptions._minCycleTime << "\n";
            }
        }
    } else {
        // If we're outside of time of day boundaries, don't run
//std::cerr << "We're outside time boundary. Current time: " << minutesOfDay << " (" << printMinutesOfDay(minutesOfDay) << "). Only running between " << _scheduleOptions._dailyCycleStart << " (" << printMinutesOfDay(_scheduleOptions._dailyCycleStart) << ") - " << _scheduleOptions._dailyCycleStop << " (" << printMinutesOfDay(_scheduleOptions._dailyCycleStop) << ")\n";
        state = SchedulingOptions::DONT_RUN;
    }
    return state;
}

void
BucketIntegrityChecker::run(framework::ThreadHandle& thread)
{
    while (!thread.interrupted()) {
        thread.registerTick(framework::PROCESS_CYCLE);
            // Get the state based on the current time.
        framework::SecondTime currentTime(
                _component.getClock().getTimeInSeconds());

        vespalib::MonitorGuard monitor(_wait);
        SchedulingOptions::RunState state = getCurrentRunState(currentTime);
        if (state != SchedulingOptions::RUN_FULL &&
            state != SchedulingOptions::RUN_CHEAP)
        {
            // If we dont want to run at this hour, wait.
            LOG(spam, "Not in a run state. Waiting.");
            monitor.wait(_maxThreadWaitTime.getTime());
            thread.registerTick(framework::WAIT_CYCLE);
        } else if (state == SchedulingOptions::RUN_FULL && !_lastCycleCompleted
                   && !_currentRunWithFullVerification)
        {
            if (getTotalPendingCount() > 0) {
                LOG(spam, "Waiting for last run to get pending to 0, before "
                          "restarting run to get full verification.");
                monitor.wait(_maxThreadWaitTime.getTime());
                thread.registerTick(framework::WAIT_CYCLE);
            } else {
                LOG(info, "Aborting current verification/repair cycle and "
                          "starting new one as we at this time want full "
                          "verification.");
                for (uint32_t i=0; i<_status.size(); ++i) {
                    _status[i].state = DiskData::DONE;
                }
                _lastCycleCompleted = true;
            }
        } else if (_scheduleOptions._requestDelay.isSet()
                   && getTotalPendingCount() > 0)
        {
            LOG(spam, "Request delay. Waiting for 0 pending before possibly "
                      "sending new.");
            // If request delay is used, we don't send anything new before
            // all requests have been received.
            monitor.wait(_maxThreadWaitTime.getTime());
            thread.registerTick(framework::WAIT_CYCLE);
        } else if (_scheduleOptions._requestDelay.isSet() &&
                   currentTime - _lastResponseTime
                        < _scheduleOptions._requestDelay)
        {
            LOG(spam, "Request delay. Waiting given seconds before sending "
                      "next.");
            // If request delay is used and we haven't waited enough, wait more
            framework::MilliSecTime delay(
                    (_scheduleOptions._requestDelay
                        - (currentTime - _lastResponseTime)).getMillis());
            if (delay > _maxThreadWaitTime) delay = _maxThreadWaitTime;
            monitor.wait(std::min(_maxThreadWaitTime.getTime(),
                                  delay.getTime()));
            thread.registerTick(framework::WAIT_CYCLE);
        } else if (_lastCycleCompleted && getTotalPendingCount() > 0) {
            LOG(spam, "Completed last cycle. Waiting until we have 0 pending "
                      "before possibly starting new cycle");
            monitor.wait(_maxThreadWaitTime.getTime());
            thread.registerTick(framework::WAIT_CYCLE);
        } else {
            LOG(spam, "Sending messages if we have less than max pending. "
                      "(Currently %u pending total, max is %u per disk)",
                      getTotalPendingCount(),
                      _scheduleOptions._maxPendingCount);
            // Else we send up to max pending and wait for responses.
            if (_lastCycleCompleted) {
                for (uint32_t i=0; i<_status.size(); ++i) {
                    _status[i].state = DiskData::NOT_STARTED;
                    _status[i].failedRepairs.clear();
                    _status[i].checkedBuckets = 0;
                    _status[i].retriedBuckets = 0;
                }
                LOG(info, "Starting new verification/repair cycle at time %s.",
                           currentTime.toString().c_str());
                _lastCycleStart = currentTime;
                _cycleStartBucketCount = _component.getBucketDatabase().size();
                _lastCycleCompleted = false;
                _currentRunWithFullVerification
                        = (state == SchedulingOptions::RUN_FULL);
                ++_cycleCount;
            }
            for (uint32_t i=0; i<_status.size(); ++i) {
                while (_status[i].pendingCount
                            < _scheduleOptions._maxPendingCount)
                {
                    document::BucketId bid(_status[i].iterate(
                            _component.getBucketDatabase()));
                    if (bid == document::BucketId(0, 0)) {
                        LOG(debug, "Completed repair cycle for disk %u.", i);
                        // If there is no next bucket, we might have completed
                        // run
                        bool completed = true;
                        for (uint32_t j=0; j<_status.size(); ++j) {
                            if (!_status[j].done()) {
                                completed = false;
                                break;
                            }
                        }
                        _lastCycleCompleted = completed;
                        if (_lastCycleCompleted) {
                            LOG(debug, "Repair cycle completed for all disks.");
                        }
                        break;
                    }

                    std::shared_ptr<RepairBucketCommand> cmd(
                            new RepairBucketCommand(bid, _status[i].disk));
                    cmd->verifyBody(_currentRunWithFullVerification);
                    cmd->moveToIdealDisk(true);
                    cmd->setPriority(230);
                    LOG(spam, "Sending new repair command for bucket %s. "
                              "After this, there will be %u pending on disk %u",
                              bid.toString().c_str(),
                              _status[i].pendingCount + 1, _status[i].disk);
                    ++_status[i].pendingCount;
                    dispatchDown(cmd);
                }
            }
            monitor.wait(_maxThreadWaitTime.getTime());
            thread.registerTick(framework::WAIT_CYCLE);
        }
    }
}

uint32_t
BucketIntegrityChecker::getTotalPendingCount() const
{
    uint32_t total = 0;
    for (uint32_t i=0; i<_status.size(); ++i) {
        total += _status[i].pendingCount;
    }
    return total;
}

namespace {
    template<typename T>
    void printRow(std::ostream& out, const std::string& key, const T& val) {
        out << "<tr><td>" << key << "</td><td><pre>" << val
            << "</pre></td></tr>\n";
    }
}

void
BucketIntegrityChecker::reportHtmlStatus(std::ostream& out,
                                         const framework::HttpUrlPath&) const
{
    vespalib::MonitorGuard monitor(_wait);
    uint32_t totalChecked = 0, totalRetried = 0;
    for (uint32_t i=0; i<_status.size(); ++i) {
        totalChecked += _status[i].checkedBuckets;
        totalRetried += _status[i].retriedBuckets;
    }
    out << "<table>\n";
    printRow(out, "current status", _lastCycleCompleted
                ? "Not running a cycle" : "Running a cycle");
    printRow(out, "pending count", getTotalPendingCount());
    std::string name = (_lastCycleCompleted ? "last" : "current");
    if (_lastCycleStart.isSet()) {
        printRow(out, name + " cycle start", _lastCycleStart.toString());
        printRow(out, "buckets checked in " + name + " cycle",
                 totalChecked);
        printRow(out, "buckets retried check in " + name + " cycle",
                 totalRetried);
        printRow(out, "total buckets in database at start of " + name
                        + " cycle", _cycleStartBucketCount);
        if (!_lastCycleCompleted) {
            std::ostringstream ost;
            ost << (100.0 * totalChecked / _cycleStartBucketCount) << " %";
            printRow(out, "progress", ost.str());
        }
    }
    if (_lastResponseTime.isSet()) {
        printRow(out, "Last response time", _lastResponseTime.toString());
    }
    printRow(out, "Schedule options", _scheduleOptions);
    out << "</table>\n";
}

} // storage
