// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "tickingthread.h"
#include "threadpool.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>

namespace storage::framework {

ThreadWaitInfo ThreadWaitInfo::MORE_WORK_ENQUEUED(false);
ThreadWaitInfo ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN(true);

void
ThreadWaitInfo::merge(const ThreadWaitInfo& other) {
    if (!other._waitWanted) {
        _waitWanted = false;
    }
}

/**
 * \brief Implementation actually doing lock handling, waiting, and allowing a
 *        global synchronization point where no thread is currently running.
 */
class TickingThreadRunner final : public Runnable {
    vespalib::Monitor& _monitor;
    TickingThread& _tickingThread;
    uint32_t _threadIndex;
    bool _wantToFreeze;
    bool _frozen;
    char _state;

public:
    typedef std::shared_ptr<TickingThreadRunner> SP;

    TickingThreadRunner(vespalib::Monitor& m,
                        TickingThread& ticker,
                        uint32_t threadIndex) noexcept
        : _monitor(m), _tickingThread(ticker),
          _threadIndex(threadIndex), _wantToFreeze(false), _frozen(false) {}

    /**
     * Call to freeze this thread. Returns then the thread has done executing
     * tick and has frozen.
     */
    void freeze() {
        vespalib::MonitorGuard guard(_monitor);
        _wantToFreeze = true;
        while (!_frozen) {
            guard.wait();
        }
    }

    /**
     * Call to thaw up a frozen thread so it can continue.
     */
    void thaw() {
        vespalib::MonitorGuard guard(_monitor);
        _wantToFreeze = false;
        guard.broadcast();
    }

    char getState() const { return _state; }

private:
    void run(ThreadHandle& handle) override {
        ThreadWaitInfo info = ThreadWaitInfo::MORE_WORK_ENQUEUED;
        CycleType cycle = PROCESS_CYCLE;
        int ticksExecutedAfterWait = 0;
        while (!handle.interrupted()) {
            {
                vespalib::MonitorGuard guard(_monitor);
                if (info.waitWanted()) {
                    _state = 'w';
                    cycle = WAIT_CYCLE;
                    if (ticksExecutedAfterWait >= handle.getTicksBeforeWait()) {
                        guard.wait(handle.getWaitTime());
                        ticksExecutedAfterWait = 0;
                    }
                }
                if (_wantToFreeze) {
                    _state = 'f';
                    doFreeze(guard);
                    ticksExecutedAfterWait = 0;
                }
                _state = 'c';
                info.merge(_tickingThread.doCriticalTick(_threadIndex));
                _state = 'n';
            }
            handle.registerTick(cycle);
            ticksExecutedAfterWait++;
            cycle = PROCESS_CYCLE;
            info = _tickingThread.doNonCriticalTick(_threadIndex);
        }
        _state = 's';
    }
    void doFreeze(vespalib::MonitorGuard& guard) {
        _frozen = true;
        guard.broadcast();
        while (_wantToFreeze) {
            guard.wait();
        }
        _frozen = false;
    }
};

class TickingThreadPoolImpl final : public TickingThreadPool {
    vespalib::string _name;
    vespalib::Monitor _monitor;
    std::atomic_uint_least64_t _waitTime;
    std::atomic_uint _ticksBeforeWait;
    std::atomic_uint_least64_t _maxProcessTime;
    std::vector<TickingThreadRunner::SP> _tickers;
    std::vector<std::shared_ptr<Thread>> _threads;

    struct FreezeGuard final : public TickingLockGuard::Impl {
        TickingThreadPoolImpl& _pool;

        explicit FreezeGuard(TickingThreadPoolImpl& pool) : _pool(pool) { _pool.freeze(); }
        ~FreezeGuard() override { _pool.thaw(); }
        void broadcast() override {}
    };
    struct CriticalGuard final : public TickingLockGuard::Impl {
        vespalib::MonitorGuard _guard;

        explicit CriticalGuard(vespalib::Monitor& m) : _guard(m) {}

        void broadcast() override { _guard.broadcast(); }
    };

public:
    TickingThreadPoolImpl(vespalib::stringref name, MilliSecTime waitTime,
                          int ticksBeforeWait, MilliSecTime maxProcessTime)
        : _name(name),
          _waitTime(waitTime.getTime()),
          _ticksBeforeWait(ticksBeforeWait),
          _maxProcessTime(maxProcessTime.getTime()) {}

    ~TickingThreadPoolImpl() override {
        stop();
    }

    void updateParametersAllThreads(MilliSecTime waitTime, MilliSecTime maxProcessTime,
                                    int ticksBeforeWait) override {
        _waitTime.store(waitTime.getTime());
        _maxProcessTime.store(maxProcessTime.getTime());
        _ticksBeforeWait.store(ticksBeforeWait);
        // TODO: Add locking so threads not deleted while updating
        for (uint32_t i=0; i<_threads.size(); ++i) {
            _threads[i]->updateParameters(waitTime.getTime(), maxProcessTime.getTime(), ticksBeforeWait);
        }
    }

    void addThread(TickingThread& ticker) override {
        ThreadIndex index = _tickers.size();
        ticker.newThreadCreated(index);
        _tickers.emplace_back(std::make_shared<TickingThreadRunner>(_monitor, ticker, index));
    }

    void start(ThreadPool& pool) override {
        assert(!_tickers.empty());
        for (uint32_t i=0; i<_tickers.size(); ++i) {
            vespalib::asciistream ost;
            ost << _name.c_str() << " thread " << i;
            _threads.push_back(std::shared_ptr<Thread>(pool.startThread(
                    *_tickers[i],
                    ost.str(),
                    _waitTime.load(std::memory_order_relaxed),
                    _maxProcessTime.load(std::memory_order_relaxed),
                    _ticksBeforeWait.load(std::memory_order_relaxed))));
        }
    }

    TickingLockGuard freezeAllTicks() override {
        return TickingLockGuard(std::make_unique<FreezeGuard>(*this));
    }

    TickingLockGuard freezeCriticalTicks() override {
        return TickingLockGuard(std::make_unique<CriticalGuard>(_monitor));
    }

    void stop() override {
        for (auto& t : _threads) {
            t->interrupt();
        }
        {
            vespalib::MonitorGuard guard(_monitor);
            guard.broadcast();
        }
        for (auto& t : _threads) {
            t->join();
        }
    }

    vespalib::string getStatus() override {
        vespalib::string result(_tickers.size(), ' ');
        for (uint32_t i=0, n=_tickers.size(); i<n; ++i) {
            result[i] = _tickers[i]->getState();
        }
        return result;
    }

private:
    void freeze() {
        for (auto& t : _tickers) {
            t->freeze();
        }
    }

    void thaw() {
        for (auto& t : _tickers) {
            t->thaw();
        }
    }
};

TickingThreadPool::UP
TickingThreadPool::createDefault(
        vespalib::stringref name,
        MilliSecTime waitTime,
        int ticksBeforeWait,
        MilliSecTime maxProcessTime)
{
    return std::make_unique<TickingThreadPoolImpl>(name, waitTime, ticksBeforeWait, maxProcessTime);
}

} // storage::framework
