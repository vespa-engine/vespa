// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flushengine.h"
#include "active_flush_stats.h"
#include "cachedflushtarget.h"
#include "flush_all_strategy.h"
#include "flushtask.h"
#include "tls_stats_factory.h"
#include "tls_stats_map.h"
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchlib/common/flush_token.h>
#include <vespa/vespalib/util/cpu_usage.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.flushengine.flushengine");

using Task = vespalib::Executor::Task;
using searchcorespi::FlushStats;
using searchcorespi::IFlushTarget;
using vespalib::CpuUsage;
using namespace std::chrono_literals;

namespace proton {

namespace {

std::pair<search::SerialNum, vespalib::string>
findOldestFlushedTarget(const IFlushTarget::List &lst, const IFlushHandler &handler)
{
    search::SerialNum oldestFlushedSerial = handler.getCurrentSerialNumber();
    vespalib::string oldestFlushedName = "null";
    for (const IFlushTarget::SP &target : lst) {
        if (target->getType() != IFlushTarget::Type::GC) {
            search::SerialNum targetFlushedSerial = target->getFlushedSerialNum();
            if (targetFlushedSerial <= oldestFlushedSerial) {
                oldestFlushedSerial = targetFlushedSerial;
                oldestFlushedName = target->getName();
            }
        }
    }
    LOG(debug, "Oldest flushed serial for handler='%s', target='%s': %" PRIu64 ".",
        handler.getName().c_str(), oldestFlushedName.c_str(), oldestFlushedSerial);
    return std::make_pair(oldestFlushedSerial, oldestFlushedName);
}

void
logTarget(const char * text, const FlushContext & ctx) {
    LOG(debug, "Target '%s' %s flush of transactions %" PRIu64 " through %" PRIu64 ".",
        ctx.getName().c_str(), text,
        ctx.getTarget()->getFlushedSerialNum() + 1,
        ctx.getHandler()->getCurrentSerialNumber());
}

VESPA_THREAD_STACK_TAG(flush_engine_executor)

}

FlushEngine::FlushMeta::FlushMeta(const vespalib::string& handler_name,
                                  const vespalib::string& target_name, uint32_t id)
    : _name((handler_name.empty() && target_name.empty()) ? "" : FlushContext::create_name(handler_name, target_name)),
      _handler_name(handler_name),
      _timer(),
      _id(id)
{ }
FlushEngine::FlushMeta::~FlushMeta() = default;

FlushEngine::FlushInfo::FlushInfo()
    : FlushMeta("", "", 0),
      _target()
{
}

FlushEngine::FlushInfo::~FlushInfo() = default;


FlushEngine::FlushInfo::FlushInfo(uint32_t taskId, const vespalib::string& handler_name, const IFlushTarget::SP& target)
    : FlushMeta(handler_name, target->getName(), taskId),
      _target(target)
{
}

FlushEngine::FlushEngine(std::shared_ptr<flushengine::ITlsStatsFactory> tlsStatsFactory,
                         IFlushStrategy::SP strategy, uint32_t numThreads, vespalib::duration idleInterval)
    : _closed(false),
      _maxConcurrentNormal(numThreads),
      _idleInterval(idleInterval),
      _taskId(0),
      _thread(),
      _has_thread(false),
      _strategy(std::move(strategy)),
      _priorityStrategy(),
      _executor(maxConcurrentTotal(), CpuUsage::wrap(flush_engine_executor, CpuUsage::Category::COMPACT)),
      _lock(),
      _cond(),
      _handlers(),
      _flushing(),
      _setStrategyLock(),
      _strategyLock(),
      _strategyCond(),
      _tlsStatsFactory(std::move(tlsStatsFactory)),
      _pendingPrune(),
      _normal_flush_token(std::make_shared<search::FlushToken>()),
      _gc_flush_token(std::make_shared<search::FlushToken>())
{ }

FlushEngine::~FlushEngine()
{
    close();
}

FlushEngine &
FlushEngine::start()
{
    _thread = std::thread([this](){run();});
    return *this;
}

FlushEngine &
FlushEngine::close()
{
    {
        std::lock_guard<std::mutex> strategyGuard(_strategyLock);
        std::lock_guard<std::mutex> guard(_lock);
        _gc_flush_token->request_stop();
        _closed = true;
        _cond.notify_all();
    }
    if (_thread.joinable()) {
        _thread.join();
    }
    _executor.shutdown().sync();
    return *this;
}

void
FlushEngine::triggerFlush()
{
    setStrategy(std::make_shared<FlushAllStrategy>());
}

void
FlushEngine::kick()
{
    std::lock_guard<std::mutex> guard(_lock);
    LOG(debug, "Kicking flush engine");
    _cond.notify_all();
}

bool
FlushEngine::canFlushMore(const std::unique_lock<std::mutex> &, IFlushTarget::Priority priority) const
{
    if (priority > IFlushTarget::Priority::NORMAL) {
        return maxConcurrentTotal() > _flushing.size();
    } else {
        return maxConcurrentNormal() > _flushing.size();
    }
}

void
FlushEngine::idle_wait(vespalib::duration minimumWaitTimeIfReady) {
    std::unique_lock<std::mutex> guard(_lock);
    _cond.wait_for(guard, minimumWaitTimeIfReady);
}

bool
FlushEngine::wait_for_slot(IFlushTarget::Priority priority)
{
    std::unique_lock<std::mutex> guard(_lock);
    while ( ! canFlushMore(guard, priority)) {
        _cond.wait_for(guard, 1s); // broadcast when flush done
    }
    return !_closed.load(std::memory_order_relaxed);
}

void
FlushEngine::wait_for_slot_or_pending_prune(IFlushTarget::Priority priority)
{
    std::unique_lock<std::mutex> guard(_lock);
    while ( ! canFlushMore(guard, priority) && _pendingPrune.empty()) {
        _cond.wait_for(guard, 1s); // broadcast when flush done
    }
}

bool
FlushEngine::has_slot(IFlushTarget::Priority priority)
{
    std::unique_lock<std::mutex> guard(_lock);
    return canFlushMore(guard, priority);
}

vespalib::string
FlushEngine::checkAndFlush(vespalib::string prev) {
    std::pair<FlushContext::List, bool> lst = getSortedTargetList();
    if (lst.second) {
        // Everything returned from a priority strategy should be flushed
        flushAll(lst.first);
    } else if ( ! lst.first.empty()) {
        if (has_slot(IFlushTarget::Priority::NORMAL)) {
            prev = flushNextTarget(prev, lst.first);
        } else {
            FlushContext::List highPri;
            if (lst.first.front()->getTarget()->getPriority() > IFlushTarget::Priority::NORMAL) {
                highPri.push_back(lst.first.front());
            }
            prev = flushNextTarget(prev, highPri);
        }
        if (!prev.empty()) {
            // Sleep 1 ms after a successful flush in order to avoid busy loop in case
            // of strategy or target error.
            std::this_thread::sleep_for(1ms);
            return prev;
        }
    }
    return "";
}

void
FlushEngine::run()
{
    _has_thread = true;
    vespalib::string prevFlushName;
    for (vespalib::duration idleInterval=vespalib::duration::zero(); !_closed.load(std::memory_order_relaxed); idleInterval = _idleInterval) {
        LOG(debug, "Making another check for something to flush, last was '%s'", prevFlushName.c_str());
        wait_for_slot_or_pending_prune(IFlushTarget::Priority::HIGH);
        if (prune()) {
            // Prune attempted on one or more handlers
        } else {
            prevFlushName = checkAndFlush(prevFlushName);
            if (prevFlushName.empty()) {
                idle_wait(idleInterval);
            }
        }
    }
    _executor.sync();
    prune();
    _has_thread = false;
}

namespace {

vespalib::string
createName(const IFlushHandler &handler, const vespalib::string &targetName)
{
    return (handler.getName() + "." + targetName);
}

}

bool
FlushEngine::prune()
{
    std::set<IFlushHandler::SP> toPrune;
    {
        std::lock_guard<std::mutex> guard(_lock);
        if (_pendingPrune.empty()) {
            return false;
        }
        _pendingPrune.swap(toPrune);
    }
    for (const auto &handler : toPrune) {
        IFlushTarget::List lst = handler->getFlushTargets();
        auto oldestFlushed = findOldestFlushedTarget(lst, *handler);
        if (LOG_WOULD_LOG(event)) {
            EventLogger::flushPrune(createName(*handler, oldestFlushed.second), oldestFlushed.first);
        }
        handler->flushDone(oldestFlushed.first);
    }
    return true;
}

bool
FlushEngine::isFlushing(const std::lock_guard<std::mutex> & guard, const vespalib::string & name) const
{
    (void) guard;
    for(const auto & it : _flushing) {
        if (name == it.second.getName()) {
            return true;
        }
    }
    return false;
}

FlushContext::List
FlushEngine::getTargetList(bool includeFlushingTargets) const
{
    FlushContext::List ret;
    {
        std::lock_guard<std::mutex> guard(_lock);
        for (const auto & it : _handlers) {
            IFlushHandler & handler(*it.second);
            search::SerialNum serial(handler.getCurrentSerialNumber());
            LOG(spam, "Checking FlushHandler '%s' current serial = %" PRIu64, handler.getName().c_str(), serial);
            IFlushTarget::List lst = handler.getFlushTargets();
            for (const IFlushTarget::SP & target : lst) {
                LOG(spam, "Checking target '%s' with flushedSerialNum = %" PRIu64,
                    target->getName().c_str(), target->getFlushedSerialNum());
                if (!isFlushing(guard, FlushContext::createName(handler, *target)) || includeFlushingTargets) {
                    ret.push_back(std::make_shared<FlushContext>(it.second, std::make_shared<CachedFlushTarget>(target), serial));
                } else {
                    LOG(debug, "Target '%s' with flushedSerialNum = %" PRIu64 " already has a flush going. Local last serial = %" PRIu64 ".",
                        target->getName().c_str(), target->getFlushedSerialNum(), serial);
                }
            }
        }
    }
    return ret;
}

namespace {

flushengine::ActiveFlushStats
make_active_flushes(const FlushEngine::FlushMetaSet& flush_set)
{
    flushengine::ActiveFlushStats result;
    for (const auto& elem : flush_set) {
        result.set_start_time(elem.handler_name(), elem.getStart());
    }
    return result;
}

}

std::pair<FlushContext::List,bool>
FlushEngine::getSortedTargetList()
{
    auto unsortedTargets = getTargetList(false);
    auto tlsStatsMap = _tlsStatsFactory->create();
    auto active_flushes = make_active_flushes(getCurrentlyFlushingSet());
    std::lock_guard<std::mutex> strategyGuard(_strategyLock);
    std::pair<FlushContext::List, bool> ret;
    if (_priorityStrategy) {
        ret = std::make_pair(_priorityStrategy->getFlushTargets(unsortedTargets, tlsStatsMap, active_flushes), true);
    } else {
        ret = std::make_pair(_strategy->getFlushTargets(unsortedTargets, tlsStatsMap, active_flushes), false);
    }
    return ret;
}

std::shared_ptr<search::IFlushToken>
FlushEngine::get_flush_token(const FlushContext& ctx)
{
    if (ctx.getTarget()->getType() == IFlushTarget::Type::GC) {
        return _gc_flush_token;
    } else {
        return _normal_flush_token;
    }
}

FlushContext::SP
FlushEngine::initNextFlush(const FlushContext::List &lst)
{
    FlushContext::SP ctx;
    for (const FlushContext::SP & it : lst) {
        if (LOG_WOULD_LOG(event)) {
            EventLogger::flushInit(it->getName());
        }
        if (it->initFlush(get_flush_token(*it))) {
            ctx = it;
            break;
        }
    }
    if (ctx) {
        logTarget("initiated", *ctx);
    }
    return ctx;
}

void
FlushEngine::flushAll(const FlushContext::List &lst)
{
    LOG(debug, "%ld targets to flush.", lst.size());
    for (const FlushContext::SP & ctx : lst) {
        if (wait_for_slot(IFlushTarget::Priority::NORMAL)) {
            if (ctx->initFlush(get_flush_token(*ctx))) {
                logTarget("initiated", *ctx);
                _executor.execute(std::make_unique<FlushTask>(initFlush(*ctx), *this, ctx));
            } else {
                logTarget("failed to initiate", *ctx);
            }
        }
    }
    _executor.sync();
    prune();
    std::lock_guard<std::mutex> strategyGuard(_strategyLock);
    _priorityStrategy.reset();
    _strategyCond.notify_all();
}

vespalib::string
FlushEngine::flushNextTarget(const vespalib::string & name, const FlushContext::List & contexts)
{
    if (contexts.empty()) {
        LOG(debug, "No target to flush.");
        return "";
    }
    FlushContext::SP ctx = initNextFlush(contexts);
    if ( ! ctx) {
        LOG(debug, "All targets refused to flush.");
        return "";
    }
    if ( name == ctx->getName()) {
        LOG(info, "The same target %s out of %ld has been asked to flush again. "
                  "This might indicate flush logic flaw so I will wait 100 ms before doing it.",
                  name.c_str(), contexts.size());
        std::this_thread::sleep_for(100ms);
    }
    _executor.execute(std::make_unique<FlushTask>(initFlush(*ctx), *this, ctx));
    return ctx->getName();
}

uint32_t
FlushEngine::initFlush(const FlushContext &ctx)
{
    if (LOG_WOULD_LOG(event)) {
        IFlushTarget::MemoryGain mgain(ctx.getTarget()->getApproxMemoryGain());
        EventLogger::flushStart(ctx.getName(), mgain.getBefore(), mgain.getAfter(), mgain.gain(),
                                ctx.getTarget()->getFlushedSerialNum() + 1, ctx.getHandler()->getCurrentSerialNumber());
    }
    return initFlush(ctx.getHandler(), ctx.getTarget());
}

void
FlushEngine::flushDone(const FlushContext &ctx, uint32_t taskId)
{
    vespalib::duration duration;
    {
        std::lock_guard<std::mutex> guard(_lock);
        duration = _flushing[taskId].elapsed();
    }
    if (LOG_WOULD_LOG(event)) {
        FlushStats stats = ctx.getTarget()->getLastFlushStats();
        EventLogger::flushComplete(ctx.getName(), duration, ctx.getTarget()->getFlushedSerialNum(),
                                   stats.getPath(), stats.getPathElementsToLog());
    }
    LOG(debug, "FlushEngine::flushDone(taskId='%d') took '%f' secs", taskId, vespalib::to_s(duration));
    std::lock_guard<std::mutex> guard(_lock);
    _flushing.erase(taskId);
    assert(ctx.getHandler());
    if (_handlers.hasHandler(ctx.getHandler())) {
        _pendingPrune.insert(ctx.getHandler());
    }
    _cond.notify_all();
}

IFlushHandler::SP
FlushEngine::putFlushHandler(const DocTypeName &docTypeName, const IFlushHandler::SP &flushHandler)
{
    std::lock_guard<std::mutex> guard(_lock);
    IFlushHandler::SP result(_handlers.putHandler(docTypeName, flushHandler));
    if (result) {
        _pendingPrune.erase(result);
    }
    _pendingPrune.insert(flushHandler);
    return result;
}

IFlushHandler::SP
FlushEngine::removeFlushHandler(const DocTypeName &docTypeName)
{
    std::lock_guard<std::mutex> guard(_lock);
    IFlushHandler::SP result(_handlers.removeHandler(docTypeName));
    _pendingPrune.erase(result);
    return result;
}

FlushEngine::FlushMetaSet
FlushEngine::getCurrentlyFlushingSet() const
{
    FlushMetaSet s;
    std::lock_guard<std::mutex> guard(_lock);
    for (const auto & it : _flushing) {
        s.insert(it.second);
    }
    return s;
}

uint32_t
FlushEngine::initFlush(const IFlushHandler::SP &handler, const IFlushTarget::SP &target)
{
    uint32_t taskId;
    {
        std::lock_guard<std::mutex> guard(_lock);
        taskId = _taskId++;
        FlushInfo flush(taskId, handler->getName(), target);
        _flushing[taskId] = flush;
    }
    LOG(debug, "FlushEngine::initFlush(handler='%s', target='%s') => taskId='%d'",
        handler->getName().c_str(), target->getName().c_str(), taskId);
    return taskId;
}

void
FlushEngine::setStrategy(IFlushStrategy::SP strategy)
{
    std::lock_guard<std::mutex> setStrategyGuard(_setStrategyLock);
    std::unique_lock<std::mutex> strategyGuard(_strategyLock);
    if (_closed.load(std::memory_order_relaxed)) {
        return;
    }
    assert(!_priorityStrategy);
    _priorityStrategy = std::move(strategy);
    {
        std::lock_guard<std::mutex> guard(_lock);
        _cond.notify_all();
    }
    while (_priorityStrategy) {
        _strategyCond.wait(strategyGuard);
    }
}

} // namespace proton
