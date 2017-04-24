// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::BucketIntegrityChecker
 * @ingroup storageserver
 *
 * @brief This class schedules buckets for integrity checks.
 *
 * @version $Id$
 */

#pragma once

#include <vespa/storage/common/servicelayercomponent.h>
#include <vespa/storage/common/storagelinkqueued.h>
#include <vespa/storage/config/config-stor-integritychecker.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageframework/storageframework.h>
#include <vespa/config/config.h>

namespace storage {

class RepairBucketReply;

/** Options describing when and how parallel we should run. */
struct SchedulingOptions : public document::Printable {
    /** Time of day to start/resume cycle. Minutes after 00:00. 0 - 24*60-1. */
    uint32_t _dailyCycleStart;
    /** Time of day to pause cycle if it's still going. Minutes after 00:00. */
    uint32_t _dailyCycleStop;

    enum RunState { DONT_RUN, RUN_FULL, RUN_CHEAP, CONTINUE };
    /** Which days to run cycle. */
    RunState _dailyStates[7];

    /** Max pending requests at the same time. */
    uint32_t _maxPendingCount;
    /** Minimum time between each cycle. */
    framework::SecondTime _minCycleTime;
    /** Seconds delay between requests if max pending == 1. */
    framework::SecondTime _requestDelay;

    SchedulingOptions()
        : _dailyCycleStart(0),
          _dailyCycleStop(0),
          _maxPendingCount(5),
          _minCycleTime(24 * 60 * 60), // One day
          _requestDelay(0)
    {
        for (uint32_t i=0; i<7; ++i) { _dailyStates[i] = RUN_FULL; }
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};


class BucketIntegrityChecker : public StorageLinkQueued,
                               private framework::Runnable,
                               public framework::HtmlStatusReporter,
                               private config::IFetcherCallback<vespa::config::content::core::StorIntegritycheckerConfig> {
public:
    struct DiskData {
        /**
         * State of bucket database iterating. If not started, we should
         * take first bucket in bucket db, if in progress, take next after
         * currentBucket, and if done, don't do anymore.
         */
        enum State { NOT_STARTED, IN_PROGRESS, DONE };

        document::BucketId currentBucket;
        uint32_t pendingCount;
        State state;
        uint8_t disk;
        std::list<document::BucketId> failedRepairs;
        uint32_t checkedBuckets;
        uint32_t retriedBuckets;

        DiskData() : currentBucket(0), pendingCount(0),
                     state(NOT_STARTED), disk(255),
                     checkedBuckets(0), retriedBuckets(0) {}

        bool done() const; // Whether we're still working on this disk
        bool working() const; // Whether we've stated and not finished
        /**
         * Get the next bucket to repair. If no more to iterate, random bucket
         * is returned. Check if done() afterwards.
         */
        document::BucketId iterate(StorBucketDatabase&);
    };

private:
    uint32_t _cycleCount;
    std::vector<DiskData> _status;
    framework::SecondTime _lastCycleStart;
    uint32_t _cycleStartBucketCount;
    framework::SecondTime _lastResponseTime;
    bool _lastCycleCompleted;
    bool _currentRunWithFullVerification;
    bool _verifyAllRepairs;
    SchedulingOptions _scheduleOptions;
    lib::ClusterState _systemState;
    vespalib::Monitor _wait;
    config::ConfigFetcher _configFetcher;
    framework::MilliSecTime _maxThreadWaitTime;
    ServiceLayerComponent _component;
    framework::Thread::UP _thread;

    BucketIntegrityChecker(const BucketIntegrityChecker &);
    BucketIntegrityChecker& operator=(const BucketIntegrityChecker &);

public:
    BucketIntegrityChecker(const config::ConfigUri & configUri,
                           ServiceLayerComponentRegister&);
    ~BucketIntegrityChecker();

    void onClose() override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    SchedulingOptions& getSchedulingOptions() { return _scheduleOptions; }
    bool isWorkingOnCycle() const;
    uint32_t getCycleCount() const;

    /** Give thread a bump by signalling it. */
    void bump() const;

    void setMaxThreadWaitTime(framework::MilliSecTime milliSecs) { _maxThreadWaitTime = milliSecs; }

    framework::Clock& getClock() { return _component.getClock(); }

private:
    void configure(std::unique_ptr<vespa::config::content::core::StorIntegritycheckerConfig>) override;
    void onDoneInit() override;
    bool onInternalReply(const std::shared_ptr<api::InternalReply>&) override;
    bool onSetSystemState(const std::shared_ptr<api::SetSystemStateCommand>&) override;
    bool onNotifyBucketChangeReply(const std::shared_ptr<api::NotifyBucketChangeReply>&) override { return true; }
    SchedulingOptions::RunState getCurrentRunState(framework::SecondTime time) const;
    void run(framework::ThreadHandle&) override;
    uint32_t getTotalPendingCount() const;
    void reportHtmlStatus(std::ostream&, const framework::HttpUrlPath&) const override;
};

}
