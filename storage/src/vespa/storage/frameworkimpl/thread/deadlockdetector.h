// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::DeadLockDetector
 * @ingroup common
 *
 * Threads register in the deadlock detector and calls registerTick
 * periodically. If they do not tick often enough, the deadlock detector
 * will shut down the node.
 *
 * @brief A class for detecting whether storage has entered a deadlock.
 */

#pragma once

#include "appkiller.h"
#include <vespa/storage/common/distributorcomponent.h>
#include <vespa/storage/common/servicelayercomponent.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/storageframework/generic/thread/threadpool.h>
#include <map>
#include <atomic>


namespace storage {

struct DeadLockDetector : private framework::Runnable,
                          private framework::HtmlStatusReporter
{
    enum State { OK, WARNED, HALTED };

    DeadLockDetector(StorageComponentRegister&,
                     AppKiller::UP killer = AppKiller::UP(new RealAppKiller));
    ~DeadLockDetector();

    void enableWarning(bool enable);
    void enableShutdown(bool enable);
    // There are no data read/write dependencies on neither _processSlackMs
    // nor _waitSlackMs so relaxed ops suffice.
    void setProcessSlack(framework::MilliSecTime slack) {
        _processSlackMs.store(slack.getTime(), std::memory_order_relaxed);
    }
    framework::MilliSecTime getProcessSlack() const {
        return framework::MilliSecTime(
                _processSlackMs.load(std::memory_order_relaxed));
    }
    void setWaitSlack(framework::MilliSecTime slack) {
        _waitSlackMs.store(slack.getTime(), std::memory_order_relaxed);
    }
    framework::MilliSecTime getWaitSlack() const {
        return framework::MilliSecTime(
                _waitSlackMs.load(std::memory_order_relaxed));
    }

        // These utility functions are public as internal anonymous classes are
        // using them. Can also be useful for whitebox testing.
    struct ThreadVisitor {
        virtual ~ThreadVisitor() {}
        virtual void visitThread(const vespalib::string& id,
                                 const framework::ThreadProperties&,
                                 const framework::ThreadTickData&,
                                 State& state) = 0;
    };
    void visitThreads(ThreadVisitor&) const;

    bool isAboveFailThreshold(const framework::MilliSecTime& time,
                              const framework::ThreadProperties& tp,
                              const framework::ThreadTickData& tick) const;
    bool isAboveWarnThreshold(const framework::MilliSecTime& time,
                              const framework::ThreadProperties& tp,
                              const framework::ThreadTickData& tick) const;
    void handleDeadlock(const framework::MilliSecTime& currentTime,
                        const vespalib::string& id,
                        const framework::ThreadProperties& tp,
                        const framework::ThreadTickData& tick,
                        bool warnOnly);

private:
    AppKiller::UP _killer;
    mutable std::map<vespalib::string, State> _states;
    mutable std::mutex      _lock;
    std::condition_variable _cond;
    bool _enableWarning;
    bool _enableShutdown;
    std::atomic<uint64_t> _processSlackMs;
    std::atomic<uint64_t> _waitSlackMs;
    State _reportedBucketDBLocksAtState;
    DistributorComponent::UP _dComponent;
    ServiceLayerComponent::UP _slComponent;
    StorageComponent* _component;
    framework::Thread::UP _thread;

    void run(framework::ThreadHandle&) override;
    void reportHtmlStatus(std::ostream& out, const framework::HttpUrlPath&) const override;
    vespalib::string getBucketLockInfo() const;
};

}
