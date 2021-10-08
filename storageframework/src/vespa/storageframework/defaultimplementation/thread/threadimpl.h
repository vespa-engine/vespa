// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storageframework/generic/thread/threadpool.h>
#include <vespa/vespalib/util/document_runnable.h>
#include <array>
#include <atomic>

namespace storage::framework::defaultimplementation {

struct ThreadPoolImpl;

class ThreadImpl : public Thread
{
    struct BackendThread : public document::Runnable {
        ThreadImpl& _impl;
        BackendThread(ThreadImpl& impl) : _impl(impl) {}
        void run() override { _impl.run(); }
    };

    /**
     * Internal data race free implementation of tick data that maps to and
     * from ThreadTickData. We hide the atomicity of this since atomic vars
     * are not CopyConstructible and thus would impose unnecessary limitations
     * on code using it.
     */
    struct AtomicThreadTickData {
        AtomicThreadTickData() noexcept
            : _lastTickType(),
              _lastTick(vespalib::steady_time(vespalib::duration::zero())),
              _maxProcessingTimeSeen(),
              _maxWaitTimeSeen()
        {}
        std::atomic<CycleType> _lastTickType;
        std::atomic<vespalib::steady_time> _lastTick;
        std::atomic<vespalib::duration> _maxProcessingTimeSeen;
        std::atomic<vespalib::duration> _maxWaitTimeSeen;
        // struct stores and loads are both data race free with relaxed
        // memory semantics. This means it's possible to observe stale/partial
        // state in a case with concurrent readers/writers.
        ThreadTickData loadRelaxed() const noexcept;
        void storeRelaxed(const ThreadTickData& newState) noexcept;
    };

    ThreadPoolImpl& _pool;
    Runnable& _runnable;
    ThreadProperties _properties;
    std::array<AtomicThreadTickData, 3> _tickData;
    uint32_t _tickDataPtr;
    std::atomic<bool> _interrupted;
    bool _joined;
    BackendThread _thread;

    void run();

public:
    ThreadImpl(ThreadPoolImpl&, Runnable&, vespalib::stringref id, vespalib::duration waitTime,
               vespalib::duration maxProcessTime, int ticksBeforeWait);
    ~ThreadImpl();

    bool interrupted() const override;
    bool joined() const override;
    void interrupt() override;
    void join() override;
    void registerTick(CycleType, vespalib::steady_time) override;
    vespalib::duration getWaitTime() const override {
        return _properties.getWaitTime();
    }
    int getTicksBeforeWait() const override {
        return _properties.getTicksBeforeWait();
    }

    void updateParameters(vespalib::duration waitTime, vespalib::duration maxProcessTime, int ticksBeforeWait) override;

    void setTickData(const ThreadTickData&);
    ThreadTickData getTickData() const;
    const ThreadProperties& getProperties() const { return _properties; }
};

}
