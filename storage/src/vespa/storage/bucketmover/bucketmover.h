// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

#include "run.h"
#include <vespa/storage/common/storagelink.h>
#include <vespa/storage/config/config-stor-bucketmover.h>
#include <vespa/storage/common/servicelayercomponent.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/config/helper/ifetchercallback.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/config/config.h>

namespace storage {

class BucketDiskMoveCommand;

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
    lib::Distribution::DiskDistribution _diskDistribution;
    uint32_t _maxSleepTime;
    framework::Thread::UP _thread;

public:
    BucketMover(const config::ConfigUri & configUri, ServiceLayerComponentRegister&);
    ~BucketMover();

    void onDoneInit() override;
    void onClose() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    bool isWorkingOnCycle() const;
    uint32_t getCycleCount() const;
    void signal();
    framework::SecondTime getNextRunTime() const { return _nextRun; }

        // Useful for unit testing
    vespa::config::content::core::StorBucketmoverConfig& getConfig() { return *_config; }
    RunStatistics& getLastRunStats() { return *_history.begin(); }
    bool tick();
    void finishCurrentRun();

private:
    friend struct BucketMoverTest;

    void startNewRun();
    void queueNewMoves();
    void sendNewMoves();

    void configure(std::unique_ptr<vespa::config::content::core::StorBucketmoverConfig>) override;
    void run(framework::ThreadHandle&) override;
    bool onInternalReply(const std::shared_ptr<api::InternalReply>&) override;
    void storageDistributionChanged() override;
    lib::Distribution::DiskDistribution currentDiskDistribution() const;

    framework::SecondTime calculateWaitTimeOfNextRun() const;

    void reportHtmlStatus(std::ostream&, const framework::HttpUrlPath&) const override;
    void printCurrentStatus(std::ostream&, const RunStatistics&) const;
    void printRunHtml(std::ostream&, const bucketmover::Run&) const;
    void printRunStatisticsHtml(std::ostream&, const RunStatistics&) const;
};

} // bucketmover
} // storage
