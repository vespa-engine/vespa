// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    std::mutex              & _monitor;
    std::condition_variable & _cond;
    TickingThread           & _tickingThread;
    uint32_t                  _threadIndex;
    bool                      _wantToFreeze;
    bool                      _frozen;
    char                      _state;

public:
    typedef std::shared_ptr<TickingThreadRunner> SP;

    TickingThreadRunner(std::mutex& m,
                        std::condition_variable & cond,
                        TickingThread& ticker,
                        uint32_t threadIndex) noexcept
        : _monitor(m), _cond(cond), _tickingThread(ticker),
          _threadIndex(threadIndex), _wantToFreeze(false), _frozen(false) {}

    /**
     * Call to freeze this thread. Returns then the thread has done executing
     * tick and has frozen.
     */
    void freeze() {
        std::unique_lock guard(_monitor);
        _wantToFreeze = true;
        while (!_frozen) {
            _cond.wait(guard);
        }
    }

    /**
     * Call to thaw up a frozen thread so it can continue.
     */
    void thaw() {
        {
            std::lock_guard guard(_monitor);
            _wantToFreeze = false;
        }
        _cond.notify_all();
    }

    char getState() const { return _state; }

private:
    void run(ThreadHandle& handle) override {
        ThreadWaitInfo info = ThreadWaitInfo::MORE_WORK_ENQUEUED;
        CycleType cycle = PROCESS_CYCLE;
        int ticksExecutedAfterWait = 0;
        while (!handle.interrupted()) {
            {
                std::unique_lock guard(_monitor);
                if (info.waitWanted()) {
                    _state = 'w';
                    cycle = WAIT_CYCLE;
                    if (ticksExecutedAfterWait >= handle.getTicksBeforeWait()) {
                        _cond.wait_for(guard, handle.getWaitTime());
                        ticksExecutedAfterWait = 0;
                    }
                }
                if (_wantToFreeze) {
                    _state = 'f';
                    doFreeze(guard, _cond);
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
    void doFreeze(std::unique_lock<std::mutex> & guard, std::condition_variable & cond) {
        _frozen = true;
        cond.notify_all();
        while (_wantToFreeze) {
            _cond.wait(guard);
        }
        _frozen = false;
    }
};

class TickingThreadPoolImpl final : public TickingThreadPool {
    const vespalib::string               _name;
    const vespalib::duration             _waitTime;
    const vespalib::duration             _maxProcessTime;
    const uint32_t                       _ticksBeforeWait;
    std::mutex                           _lock;
    std::condition_variable              _cond;
    std::vector<TickingThreadRunner::SP> _tickers;
    std::vector<std::shared_ptr<Thread>> _threads;

    struct FreezeGuard final : public TickingLockGuard::Impl {
        TickingThreadPoolImpl& _pool;

        explicit FreezeGuard(TickingThreadPoolImpl& pool) : _pool(pool) { _pool.freeze(); }
        ~FreezeGuard() override { _pool.thaw(); }
        void broadcast() override {}
    };
    struct CriticalGuard final : public TickingLockGuard::Impl {
        std::unique_lock<std::mutex> _guard;
        std::condition_variable &_cond;

        explicit CriticalGuard(std::mutex & lock, std::condition_variable & cond) : _guard(lock), _cond(cond) {}

        void broadcast() override { _cond.notify_all(); }
    };

public:
    TickingThreadPoolImpl(vespalib::stringref name, vespalib::duration waitTime,
                          int ticksBeforeWait, vespalib::duration maxProcessTime)
        : _name(name),
          _waitTime(waitTime),
          _maxProcessTime(maxProcessTime),
          _ticksBeforeWait(ticksBeforeWait)
    { }

    ~TickingThreadPoolImpl() override {
        stop();
    }

    void addThread(TickingThread& ticker) override {
        ThreadIndex index = _tickers.size();
        ticker.newThreadCreated(index);
        _tickers.emplace_back(std::make_shared<TickingThreadRunner>(_lock, _cond, ticker, index));
    }

    void start(ThreadPool& pool) override {
        assert(!_tickers.empty());
        for (uint32_t i=0; i<_tickers.size(); ++i) {
            vespalib::asciistream ost;
            ost << _name.c_str() << " thread " << i;
            _threads.push_back(std::shared_ptr<Thread>(pool.startThread(
                    *_tickers[i],
                    ost.str(),
                    _waitTime,
                    _maxProcessTime,
                    _ticksBeforeWait, std::nullopt)));
        }
    }

    TickingLockGuard freezeAllTicks() override {
        return TickingLockGuard(std::make_unique<FreezeGuard>(*this));
    }

    TickingLockGuard freezeCriticalTicks() override {
        return TickingLockGuard(std::make_unique<CriticalGuard>(_lock, _cond));
    }

    void stop() override {
        for (auto& t : _threads) {
            t->interrupt();
        }
        {
            _cond.notify_all();
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
        vespalib::duration waitTime,
        int ticksBeforeWait,
        vespalib::duration maxProcessTime)
{
    return std::make_unique<TickingThreadPoolImpl>(name, waitTime, ticksBeforeWait, maxProcessTime);
}

TickingThreadPool::UP
TickingThreadPool::createDefault(vespalib::stringref name, vespalib::duration waitTime)
{
    return createDefault(name, waitTime, 1, 5s);
}

} // storage::framework
