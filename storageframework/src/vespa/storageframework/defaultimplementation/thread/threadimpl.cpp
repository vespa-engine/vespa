// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadimpl.h"
#include "threadpoolimpl.h"
#include <vespa/storageframework/generic/clock/clock.h>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".framework.thread.impl");

namespace storage::framework::defaultimplementation {

ThreadImpl::ThreadImpl(ThreadPoolImpl& pool,
                       Runnable& runnable,
                       vespalib::stringref id,
                       vespalib::duration waitTime,
                       vespalib::duration maxProcessTime,
                       int ticksBeforeWait)
    : Thread(id),
      _pool(pool),
      _runnable(runnable),
      _properties(waitTime, maxProcessTime, ticksBeforeWait),
      _tickData(),
      _tickDataPtr(0),
      _interrupted(false),
      _joined(false),
      _thread(*this)
{
    _tickData[_tickDataPtr]._lastTick = pool.getClock().getMonotonicTime();
    _thread.start(_pool.getThreadPool());
}

ThreadImpl::~ThreadImpl()
{
    interrupt();
    join();
}

void
ThreadImpl::run()
{
    _runnable.run(*this);
    _pool.unregisterThread(*this);
    _joined = true;
}

bool
ThreadImpl::interrupted() const
{
    return _interrupted.load(std::memory_order_relaxed);
}

bool
ThreadImpl::joined() const
{
    return _joined;
}

void
ThreadImpl::interrupt()
{
    _interrupted.store(true, std::memory_order_relaxed);
    _thread.stop();
}

void
ThreadImpl::join()
{
    _thread.join();
}

void
ThreadImpl::registerTick(CycleType cycleType, vespalib::steady_time now)
{
    if (now.time_since_epoch() == vespalib::duration::zero()) now = _pool.getClock().getMonotonicTime();
    ThreadTickData data(getTickData());
    vespalib::steady_clock::time_point previousTick = data._lastTick;
    data._lastTick = now;
    data._lastTickType = cycleType;
    setTickData(data);

    if (data._lastTick.time_since_epoch() == vespalib::duration::zero()) { return; }

    if (previousTick > now) {
        LOGBP(warning, "Thread is registering tick at time %" PRIu64 ", but "
                       "last time it registered a tick, the time was %" PRIu64
                       ". Assuming clock has been adjusted backwards",
	      vespalib::count_ms(now.time_since_epoch()), vespalib::count_ms(previousTick.time_since_epoch()));
        return;
    }
    vespalib::duration cycleTime = now - previousTick;
    if (cycleType == WAIT_CYCLE) {
        data._maxWaitTimeSeen = std::max(data._maxWaitTimeSeen, cycleTime);
    } else {
        data._maxProcessingTimeSeen = std::max(data._maxProcessingTimeSeen, cycleTime);
    }
}

ThreadTickData
ThreadImpl::getTickData() const
{
    return _tickData[_tickDataPtr].loadRelaxed();
}

void
ThreadImpl::setTickData(const ThreadTickData& tickData)
{
    uint32_t nextData = (_tickDataPtr + 1) % _tickData.size();
    _tickData[nextData].storeRelaxed(tickData);
    _tickDataPtr = nextData;
}

void
ThreadImpl::updateParameters(vespalib::duration waitTime,
                             vespalib::duration maxProcessTime,
                             int ticksBeforeWait) {
  _properties.setWaitTime(waitTime);
  _properties.setMaxProcessTime(maxProcessTime);
  _properties.setTicksBeforeWait(ticksBeforeWait);
}

ThreadTickData
ThreadImpl::AtomicThreadTickData::loadRelaxed() const noexcept
{
    ThreadTickData result;
    constexpr auto relaxed = std::memory_order_relaxed;
    result._lastTickType = _lastTickType.load(relaxed);
    result._lastTick = _lastTick.load(relaxed);
    result._maxProcessingTimeSeen = _maxProcessingTimeSeen.load(relaxed);
    result._maxWaitTimeSeen = _maxWaitTimeSeen.load(relaxed);
    return result;
}

void
ThreadImpl::AtomicThreadTickData::storeRelaxed(const ThreadTickData& newState) noexcept
{
    constexpr auto relaxed = std::memory_order_relaxed;
    _lastTickType.store(newState._lastTickType, relaxed);
    _lastTick.store(newState._lastTick, relaxed);
    _maxProcessingTimeSeen.store(newState._maxProcessingTimeSeen, relaxed);
    _maxWaitTimeSeen.store(newState._maxWaitTimeSeen, relaxed);
}

}
