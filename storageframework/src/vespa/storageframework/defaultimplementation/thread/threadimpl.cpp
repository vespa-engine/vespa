// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadimpl.h"
#include "threadpoolimpl.h"
#include <vespa/storageframework/generic/clock/clock.h>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".framework.thread.impl");

namespace storage::framework::defaultimplementation {

ThreadImpl::ThreadImpl(ThreadPoolImpl& pool,
                       Runnable& runnable,
                       vespalib::stringref id,
                       uint64_t waitTimeMs,
                       uint64_t maxProcessTimeMs,
                       int ticksBeforeWait)
    : Thread(id),
      _pool(pool),
      _runnable(runnable),
      _properties(waitTimeMs, maxProcessTimeMs, ticksBeforeWait),
      _tickData(),
      _tickDataPtr(0),
      _interrupted(false),
      _joined(false),
      _thread(*this)
{
    _tickData[_tickDataPtr]._lastTickMs = pool.getClock().getTimeInMillis().getTime();
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
ThreadImpl::registerTick(CycleType cycleType, MilliSecTime time)
{
    if (!time.isSet()) time = _pool.getClock().getTimeInMillis();
    ThreadTickData data(getTickData());
    uint64_t previousTickMs = data._lastTickMs;
    uint64_t nowMs = time.getTime();
    data._lastTickMs = nowMs;
    data._lastTickType = cycleType;
    setTickData(data);

    if (data._lastTickMs == 0) { return; }

    if (previousTickMs > nowMs) {
        LOGBP(warning, "Thread is registering tick at time %lu, but "
                       "last time it registered a tick, the time was %lu"
                       ". Assuming clock has been adjusted backwards",
	      nowMs, previousTickMs);
        return;
    }
    uint64_t cycleTimeMs = nowMs - previousTickMs;
    if (cycleType == WAIT_CYCLE) {
        data._maxWaitTimeSeenMs = std::max(data._maxWaitTimeSeenMs, cycleTimeMs);
    } else {
        data._maxProcessingTimeSeenMs = std::max(data._maxProcessingTimeSeenMs, cycleTimeMs);
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
ThreadImpl::updateParameters(uint64_t waitTimeMs,
			     uint64_t maxProcessTimeMs,
			     int ticksBeforeWait) {
  _properties.setWaitTime(waitTimeMs);
  _properties.setMaxProcessTime(maxProcessTimeMs);
  _properties.setTicksBeforeWait(ticksBeforeWait);
}

ThreadTickData
ThreadImpl::AtomicThreadTickData::loadRelaxed() const noexcept
{
    ThreadTickData result;
    constexpr auto relaxed = std::memory_order_relaxed;
    result._lastTickType = _lastTickType.load(relaxed);
    result._lastTickMs = _lastTickMs.load(relaxed);
    result._maxProcessingTimeSeenMs = _maxProcessingTimeSeenMs.load(relaxed);
    result._maxWaitTimeSeenMs = _maxWaitTimeSeenMs.load(relaxed);
    return result;
}

void
ThreadImpl::AtomicThreadTickData::storeRelaxed(
        const ThreadTickData& newState) noexcept
{
    constexpr auto relaxed = std::memory_order_relaxed;
    _lastTickType.store(newState._lastTickType, relaxed);
    _lastTickMs.store(newState._lastTickMs, relaxed);
    _maxProcessingTimeSeenMs.store(newState._maxProcessingTimeSeenMs, relaxed);
    _maxWaitTimeSeenMs.store(newState._maxWaitTimeSeenMs, relaxed);
}

}
