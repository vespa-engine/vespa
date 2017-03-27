// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/storageframework/generic/thread/tickingthread.h>
#include <atomic>
#include <vespa/log/log.h>
#include <sstream>
#include <vespa/storageframework/generic/thread/threadpool.h>
#include <vespa/vespalib/util/exceptions.h>

LOG_SETUP(".framework.thread.ticker");

namespace storage {
namespace framework {

ThreadWaitInfo ThreadWaitInfo::MORE_WORK_ENQUEUED(false);
ThreadWaitInfo ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN(true);

void
ThreadWaitInfo::merge(const ThreadWaitInfo& other) {
    if (!other._waitWanted) _waitWanted = false;
}

/**
 * \brief Implementation actually doing lock handling, waiting, and allowing a
 *        global synchronization point where no thread is currently running.
 */
class TickingThreadRunner : public Runnable {
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
                        uint32_t threadIndex)
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
    virtual void run(ThreadHandle& handle) {
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

class TickingThreadPoolImpl : public TickingThreadPool {
    vespalib::string _name;
    vespalib::Monitor _monitor;
    std::atomic_uint_least64_t _waitTime  ;
    std::atomic_uint _ticksBeforeWait;
    std::atomic_uint_least64_t _maxProcessTime;
    std::vector<TickingThreadRunner::SP> _tickers;
    std::vector<std::shared_ptr<Thread> > _threads;

    struct FreezeGuard : public TickingLockGuard::Impl {
        TickingThreadPoolImpl& _pool;

        FreezeGuard(TickingThreadPoolImpl& pool)
            : _pool(pool) { _pool.freeze(); }

        virtual ~FreezeGuard() { _pool.thaw(); }

        virtual void broadcast() {}
    };
    struct CriticalGuard : public TickingLockGuard::Impl {
        vespalib::MonitorGuard _guard;

        CriticalGuard(vespalib::Monitor& m) : _guard(m) {}

        virtual void broadcast() { _guard.broadcast(); }
    };

public:
    TickingThreadPoolImpl(vespalib::stringref name,
                          MilliSecTime waitTime,
			  int ticksBeforeWait,
                          MilliSecTime maxProcessTime)
        : _name(name),
          _waitTime(waitTime.getTime()),
          _ticksBeforeWait(ticksBeforeWait),
          _maxProcessTime(maxProcessTime.getTime()) {}

    ~TickingThreadPoolImpl() {
        stop();
    }

    virtual void updateParametersAllThreads(
            MilliSecTime waitTime,
            MilliSecTime maxProcessTime,
            int ticksBeforeWait) {
        _waitTime.store(waitTime.getTime());
        _maxProcessTime.store(maxProcessTime.getTime());
        _ticksBeforeWait.store(ticksBeforeWait);
        // TODO: Add locking so threads not deleted while updating
	for (uint32_t i=0; i<_threads.size(); ++i) {
	  _threads[i]->updateParameters(waitTime.getTime(),
                                        maxProcessTime.getTime(),
                                        ticksBeforeWait);
        }
    }

    void addThread(TickingThread& ticker) {
        ThreadIndex index = _tickers.size();
        ticker.newThreadCreated(index);
        _tickers.push_back(TickingThreadRunner::SP(
                new TickingThreadRunner(_monitor, ticker, index))); 
    }

    void start(ThreadPool& pool) {
        if (_tickers.empty()) {
            throw vespalib::IllegalStateException(
                    "Makes no sense to start threadpool without threads",
                    VESPA_STRLOC);
        }
        for (uint32_t i=0; i<_tickers.size(); ++i) {
            std::ostringstream ost;
            ost << _name.c_str() << " thread " << i;
            _threads.push_back(std::shared_ptr<Thread>(pool.startThread(
                    *_tickers[i],
                    ost.str(),
                    _waitTime.load(std::memory_order_relaxed),
                    _maxProcessTime.load(std::memory_order_relaxed),
                    _ticksBeforeWait.load(std::memory_order_relaxed))));
        }
    }

    virtual TickingLockGuard freezeAllTicks() {
        return TickingLockGuard(std::unique_ptr<TickingLockGuard::Impl>(
                new FreezeGuard(*this)));
    }

    virtual TickingLockGuard freezeCriticalTicks() {
        return TickingLockGuard(std::unique_ptr<TickingLockGuard::Impl>(
                new CriticalGuard(_monitor)));
    }

    void stop() {
        for (uint32_t i=0; i<_threads.size(); ++i) {
            _threads[i]->interrupt();
        }
        {
            vespalib::MonitorGuard guard(_monitor);
            guard.broadcast();
        }
        for (uint32_t i=0; i<_threads.size(); ++i) {
            _threads[i]->join();
        }
    }

    vespalib::string getStatus() {
        vespalib::string result(_tickers.size(), ' ');
        for (uint32_t i=0, n=_tickers.size(); i<n; ++i) {
            result[i] = _tickers[i]->getState();
        }
        return result;
    }

private:
    void freeze() {
        for (uint32_t i=0; i<_tickers.size(); ++i) {
            _tickers[i]->freeze();
        }
    }

    void thaw() {
        for (uint32_t i=0; i<_tickers.size(); ++i) {
            _tickers[i]->thaw();
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
    return TickingThreadPool::UP(new TickingThreadPoolImpl(
            name,
            waitTime,
            ticksBeforeWait,
            maxProcessTime));
}

} // framework
} // storage
