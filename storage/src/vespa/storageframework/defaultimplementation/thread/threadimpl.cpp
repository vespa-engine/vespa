// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadimpl.h"
#include "threadpoolimpl.h"
#include <vespa/storageframework/generic/clock/clock.h>
#include <vespa/vespalib/util/atomic.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <cinttypes>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".framework.thread.impl");

using namespace vespalib::atomic;

namespace storage::framework::defaultimplementation {

ThreadImpl::ThreadImpl(ThreadPoolImpl& pool,
                       Runnable& runnable,
                       vespalib::stringref id,
                       vespalib::duration waitTime,
                       vespalib::duration maxProcessTime,
                       int ticksBeforeWait,
                       std::optional<vespalib::CpuUsage::Category> cpu_category)
    : Thread(id),
      _pool(pool),
      _runnable(runnable),
      _properties(waitTime, maxProcessTime, ticksBeforeWait),
      _tickData(),
      _tickDataPtr(0),
      _interrupted(false),
      _joined(false),
      _thread(),
      _cpu_category(cpu_category)
{
    _tickData[load_relaxed(_tickDataPtr)]._lastTick = pool.getClock().getMonotonicTime();
    _thread = std::thread([this](){run();});
}

ThreadImpl::~ThreadImpl()
{
    interrupt();
    join();
}

void
ThreadImpl::run()
{
    if (_cpu_category.has_value()) {
        auto usage = vespalib::CpuUsage::use(_cpu_category.value());
        _runnable.run(*this);
    } else {
        _runnable.run(*this);
    }
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
}

void
ThreadImpl::join()
{
    if (_thread.joinable()) {
        _thread.join();
    }
}

vespalib::string
ThreadImpl::get_live_thread_stack_trace() const
{
    auto native_handle = const_cast<std::thread&>(_thread).native_handle();
    return vespalib::SignalHandler::get_cross_thread_stack_trace(native_handle);
}

void
ThreadImpl::registerTick(CycleType cycleType) {
    registerTick(cycleType, _pool.getClock().getMonotonicTime());
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
    return _tickData[load_acquire(_tickDataPtr)].loadRelaxed();
}

void
ThreadImpl::setTickData(const ThreadTickData& tickData)
{
    uint32_t nextData = (load_relaxed(_tickDataPtr) + 1) % _tickData.size();
    _tickData[nextData].storeRelaxed(tickData);
    store_release(_tickDataPtr, nextData);
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
