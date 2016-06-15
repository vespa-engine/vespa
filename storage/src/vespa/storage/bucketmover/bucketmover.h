// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::BucketMover
 * @ingroup storageserver
 *
 * @brief This class moves buckets between disks for reducing node skew. Highly
 * inspired from BucketIntegrityChecker.
 *
 * It uses DiskMonitor class to monitor disk info (space available, space used,
 * etc), but also to monitor the number of pending moves for each disk.
 * It also uses BucketMoverHeuristic class to decide on which buckets should be
 * moved and to what disk.
 *
 * @version $Id:
 */

#pragma once

#include <vespa/storage/common/storagelink.h>
#include <vespa/storage/config/config-stor-bucketmover.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storage/bucketmover/run.h>
#include <vespa/storageframework/storageframework.h>
#include <vespa/storageframework/storageframework.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/storage/common/servicelayercomponent.h>

namespace storage {

class BucketDiskMoveCommand;
class Clock;

namespace bucketmover {

class BucketMover : public StorageLink,
                    private framework::Runnable,
                    public framework::HtmlStatusReporter,
                    private config::IFetcherCallback<vespa::config::content::core::StorBucketmoverConfig>
{
    ServiceLayerComponent _component;
    std::unique_ptr<vespa::config::content::core::StorBucketmoverConfig> _config;
    uint32_t _cycleCount;
    framework::SecondTime _nextRun;
    std::unique_ptr<bucketmover::Run> _currentRun;
    std::list<Move> _pendingMoves;
    std::list<std::shared_ptr<BucketDiskMoveCommand> > _newMoves;
    std::list<RunStatistics> _history;
    vespalib::Monitor _wait;
    config::ConfigFetcher _configFetcher;
    vespa::config::content::StorDistributionConfig::DiskDistribution _diskDistribution;
    uint32_t _maxSleepTime;
    framework::Thread::UP _thread;

public:
    BucketMover(const config::ConfigUri & configUri, ServiceLayerComponentRegister&);
    ~BucketMover();

    virtual void onDoneInit();
    virtual void onClose();

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    bool isWorkingOnCycle() const;
    uint32_t getCycleCount() const;
    void signal();
    framework::SecondTime getNextRunTime() const { return _nextRun; }

        // Useful for unit testing
    vespa::config::content::core::StorBucketmoverConfig& getConfig() { return *_config; }
    RunStatistics& getLastRunStats() { return *_history.begin(); }

private:
    friend class BucketMoverTest;

    void startNewRun();
    void queueNewMoves();
    void sendNewMoves();
    void finishCurrentRun();
    bool tick();

    virtual void configure(std::unique_ptr<vespa::config::content::core::StorBucketmoverConfig>);
    virtual void run(framework::ThreadHandle&);
    bool onInternalReply(const std::shared_ptr<api::InternalReply>&);
    virtual void storageDistributionChanged();

    framework::SecondTime calculateWaitTimeOfNextRun() const;

    virtual void reportHtmlStatus(std::ostream&,
                                  const framework::HttpUrlPath&) const;
    void printCurrentStatus(std::ostream&, const RunStatistics&) const;
    void printRunHtml(std::ostream&, const bucketmover::Run&) const;
    void printRunStatisticsHtml(std::ostream&, const RunStatistics&) const;

};

} // bucketmover
} // storage

