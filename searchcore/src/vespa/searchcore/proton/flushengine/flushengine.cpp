// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cachedflushtarget.h"
#include "flush_all_strategy.h"
#include "flushengine.h"
#include "flushtask.h"
#include "tls_stats_map.h"
#include "tls_stats_factory.h"
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchlib/common/flush_token.h>
#include <vespa/vespalib/util/jsonwriter.h>
#include <vespa/vespalib/util/size_literals.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".proton.flushengine.flushengine");

typedef vespalib::Executor::Task Task;
using searchcorespi::IFlushTarget;
using searchcorespi::FlushStats;
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

FlushEngine::FlushMeta::FlushMeta(const vespalib::string & name, uint32_t id)
    : _name(name),
      _timer(),
      _id(id)
{ }
FlushEngine::FlushMeta::~FlushMeta() = default;

FlushEngine::FlushInfo::FlushInfo()
    : FlushMeta("", 0),
      _target()
{
}

FlushEngine::FlushInfo::~FlushInfo() = default;


FlushEngine::FlushInfo::FlushInfo(uint32_t taskId, const IFlushTarget::SP &target, const vespalib::string & destination)
    : FlushMeta(destination, taskId),
      _target(target)
{
}

FlushEngine::FlushEngine(std::shared_ptr<flushengine::ITlsStatsFactory> tlsStatsFactory,
                         IFlushStrategy::SP strategy, uint32_t numThreads, vespalib::duration idleInterval)
    : _closed(false),
      _maxConcurrent(numThreads),
      _idleInterval(idleInterval),
      _taskId(0),
      _threadPool(128_Ki),
      _strategy(std::move(strategy)),
      _priorityStrategy(),
      _executor(numThreads, 128_Ki, flush_engine_executor),
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
    if (_threadPool.NewThread(this) == nullptr) {
        throw vespalib::IllegalStateException("Failed to start engine thread.");
    }
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
    _threadPool.Close();
    _executor.shutdown();
    _executor.sync();
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
FlushEngine::canFlushMore(const std::unique_lock<std::mutex> &guard) const
{
    (void) guard;
    return _maxConcurrent > _flushing.size();
}

bool
FlushEngine::wait(vespalib::duration minimumWaitTimeIfReady, bool ignorePendingPrune)
{
    std::unique_lock<std::mutex> guard(_lock);
    if ( (minimumWaitTimeIfReady != vespalib::duration::zero()) && canFlushMore(guard) && _pendingPrune.empty()) {
        _cond.wait_for(guard, minimumWaitTimeIfReady);
    }
    while ( ! canFlushMore(guard) && ( ignorePendingPrune || _pendingPrune.empty())) {
        _cond.wait_for(guard, 1s); // broadcast when flush done
    }
    return !_closed;
}

void
FlushEngine::Run(FastOS_ThreadInterface *, void *)
{
    bool shouldIdle = false;
    vespalib::string prevFlushName;
    while (wait(shouldIdle ? _idleInterval : vespalib::duration::zero(), false)) {
        shouldIdle = false;
        if (prune()) {
            continue; // Prune attempted on one or more handlers
        }
        prevFlushName = flushNextTarget(prevFlushName);
        if ( ! prevFlushName.empty()) {
            // Sleep 1 ms after a successful flush in order to avoid busy loop in case
            // of strategy or target error.
            std::this_thread::sleep_for(1ms);
        } else {
            shouldIdle = true;
        }
        LOG(debug, "Making another wait(idle=%s, timeS=%1.3f) last was '%s'",
            shouldIdle ? "true" : "false", shouldIdle ? vespalib::to_s(_idleInterval) : 0, prevFlushName.c_str());
    }
    _executor.sync();
    prune();
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

std::pair<FlushContext::List,bool>
FlushEngine::getSortedTargetList()
{
    FlushContext::List unsortedTargets = getTargetList(false);
    flushengine::TlsStatsMap tlsStatsMap(_tlsStatsFactory->create());
    std::lock_guard<std::mutex> strategyGuard(_strategyLock);
    std::pair<FlushContext::List, bool> ret;
    if (_priorityStrategy) {
        ret = std::make_pair(_priorityStrategy->getFlushTargets(unsortedTargets, tlsStatsMap), true);
    } else {
        ret = std::make_pair(_strategy->getFlushTargets(unsortedTargets, tlsStatsMap), false);
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
        if (wait(vespalib::duration::zero(), true)) {
            if (ctx->initFlush(get_flush_token(*ctx))) {
                logTarget("initiated", *ctx);
                _executor.execute(std::make_unique<FlushTask>(initFlush(*ctx), *this, ctx));
            } else {
                logTarget("failed to initiate", *ctx);
            }
        }
    }
}

vespalib::string
FlushEngine::flushNextTarget(const vespalib::string & name)
{
    std::pair<FlushContext::List,bool> lst = getSortedTargetList();
    if (lst.second) {
        // Everything returned from a priority strategy should be flushed
        flushAll(lst.first);
        _executor.sync();
        prune();
        std::lock_guard<std::mutex> strategyGuard(_strategyLock);
        _priorityStrategy.reset();
        _strategyCond.notify_all();
        return "";
    }
    if (lst.first.empty()) {
        LOG(debug, "No target to flush.");
        return "";
    }
    FlushContext::SP ctx = initNextFlush(lst.first);
    if ( ! ctx) {
        LOG(debug, "All targets refused to flush.");
        return "";
    }
    if ( name == ctx->getName()) {
        LOG(info, "The same target %s out of %ld has been asked to flush again. "
                  "This might indicate flush logic flaw so I will wait 100 ms before doing it.",
                  name.c_str(), lst.first.size());
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
    vespalib::duration duration = vespalib::duration::zero();
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
    uint32_t taskId(0);
    {
        std::lock_guard<std::mutex> guard(_lock);
        taskId = _taskId++;
        vespalib::string name(FlushContext::createName(*handler, *target));
        FlushInfo flush(taskId, target, name);
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
    if (_closed) {
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
