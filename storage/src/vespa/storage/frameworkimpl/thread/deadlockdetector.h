// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include <vespa/storageframework/generic/thread/runnable.h>
#include <vespa/storageframework/generic/thread/thread.h>
#include <atomic>
#include <map>

namespace storage {

namespace framework { class Thread; }

struct DeadLockDetector : private framework::Runnable,
                          private framework::HtmlStatusReporter
{
    enum State { OK, WARNED, HALTED };

    DeadLockDetector(StorageComponentRegister&,
                     AppKiller::UP killer = std::make_unique<RealAppKiller>());
    ~DeadLockDetector() override;

    void enableWarning(bool enable); // Thread-safe
    void enableShutdown(bool enable); // Thread-safe
    // There are no data read/write dependencies on neither _processSlack
    // nor _waitSlack so relaxed ops suffice.
    void setProcessSlack(vespalib::duration slack) {
        _processSlack.store(slack, std::memory_order_relaxed);
    }
    vespalib::duration getProcessSlack() const {
        return _processSlack.load(std::memory_order_relaxed);
    }
    void setWaitSlack(vespalib::duration slack) {
        _waitSlack.store(slack, std::memory_order_relaxed);
    }
    vespalib::duration getWaitSlack() const {
        return _waitSlack.load(std::memory_order_relaxed);
    }

    // These utility functions are public as internal anonymous classes are
    // using them. Can also be useful for whitebox testing.
    struct ThreadVisitor {
        virtual ~ThreadVisitor() = default;
        virtual void visitThread(const framework::Thread& thread, State& state) = 0;
    };
    void visitThreads(ThreadVisitor&) const;

    bool isAboveFailThreshold(vespalib::steady_time time,
                              const framework::ThreadProperties& tp,
                              const framework::ThreadTickData& tick) const;
    bool isAboveWarnThreshold(vespalib::steady_time ,
                              const framework::ThreadProperties& tp,
                              const framework::ThreadTickData& tick) const;
    void handleDeadlock(vespalib::steady_time currentTime,
                        const framework::Thread& deadlocked_thread,
                        const vespalib::string& id,
                        const framework::ThreadProperties& tp,
                        const framework::ThreadTickData& tick,
                        bool warnOnly);

    // Note: returned value may change between calls due to reconfiguration by other threads
    [[nodiscard]] bool warning_enabled_relaxed() const noexcept {
        return _enableWarning.load(std::memory_order_relaxed);
    }
    [[nodiscard]] bool shutdown_enabled_relaxed() const noexcept {
        return _enableShutdown.load(std::memory_order_relaxed);
    }

private:
    AppKiller::UP _killer;
    mutable std::map<vespalib::string, State> _states;
    mutable std::mutex      _lock;
    std::condition_variable _cond;
    std::atomic<bool> _enableWarning;
    std::atomic<bool> _enableShutdown;
    std::atomic<vespalib::duration> _processSlack;
    std::atomic<vespalib::duration> _waitSlack;
    DistributorComponent::UP _dComponent;
    ServiceLayerComponent::UP _slComponent;
    StorageComponent* _component;
    framework::Thread::UP _thread;

    void run(framework::ThreadHandle&) override;
    void reportHtmlStatus(std::ostream& out, const framework::HttpUrlPath&) const override;
    vespalib::string getBucketLockInfo() const;
};

}
