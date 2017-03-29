// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cachedflushtarget.h"
#include "flush_all_strategy.h"
#include "flushengine.h"
#include "flushtask.h"
#include "tls_stats_map.h"
#include "tls_stats_factory.h"
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/vespalib/util/jsonwriter.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.flushengine.flushengine");

using vespalib::MonitorGuard;
typedef vespalib::Executor::Task Task;

namespace proton {

namespace {

search::SerialNum
findOldestFlushedSerial(const IFlushTarget::List &lst,
                        const IFlushHandler &handler)
{
    search::SerialNum ret(handler.getCurrentSerialNumber());
    for (const IFlushTarget::SP & target : lst) {
        ret = std::min(ret, target->getFlushedSerialNum());
    }
    LOG(debug, "Oldest flushed serial for '%s' is %" PRIu64 ".", handler.getName().c_str(), ret);
    return ret;
}

}

FlushEngine::FlushMeta::FlushMeta(const vespalib::string & name, fastos::TimeStamp start, uint32_t id) :
    _name(name),
    _start(start),
    _id(id)
{ }
FlushEngine::FlushMeta::~FlushMeta() { }

FlushEngine::FlushInfo::FlushInfo() :
    FlushMeta("", fastos::ClockSystem::now(), 0),
    _target()
{
}

FlushEngine::FlushInfo::~FlushInfo() { }


FlushEngine::FlushInfo::FlushInfo(uint32_t taskId,
                                  const IFlushTarget::SP &target,
                                  const vespalib::string & destination) :
    FlushMeta(destination, fastos::ClockSystem::now(), taskId),
    _target(target)
{
}

FlushEngine::FlushEngine(std::shared_ptr<flushengine::ITlsStatsFactory>
                         tlsStatsFactory,
                         IFlushStrategy::SP strategy, uint32_t numThreads,
                         uint32_t idleIntervalMS)
    : _closed(false),
      _maxConcurrent(numThreads),
      _idleIntervalMS(idleIntervalMS),
      _taskId(0),
      _threadPool(128 * 1024),
      _strategy(strategy),
      _priorityStrategy(),
      _executor(numThreads, 128 * 1024),
      _monitor(),
      _handlers(),
      _flushing(),
      _strategyLock(),
      _strategyMonitor(),
      _tlsStatsFactory(tlsStatsFactory),
      _pendingPrune()
{
    // empty
}

FlushEngine::~FlushEngine()
{
    close();
}

FlushEngine &
FlushEngine::start()
{
    if (_threadPool.NewThread(this) == NULL) {
        throw vespalib::IllegalStateException("Failed to start engine thread.");
    }
    return *this;
}

FlushEngine &
FlushEngine::close()
{
    {
        MonitorGuard strategyGuard(_strategyMonitor);
        MonitorGuard guard(_monitor);
        _closed = true;
        guard.broadcast();
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
FlushEngine::kick(void)
{
    MonitorGuard guard(_monitor);
    LOG(debug, "Kicking flush engine");
    guard.broadcast();
}

bool
FlushEngine::canFlushMore(const MonitorGuard & guard) const
{
    (void) guard;
    return _maxConcurrent > _flushing.size();
}

bool
FlushEngine::wait(size_t minimumWaitTimeIfReady)
{
    MonitorGuard guard(_monitor);
    if ( (minimumWaitTimeIfReady > 0) && canFlushMore(guard) && _pendingPrune.empty()) {
        guard.wait(minimumWaitTimeIfReady);
    }
    while ( ! canFlushMore(guard) && _pendingPrune.empty()) {
        guard.wait(1000); // broadcast when flush done
    }
    return !_closed;
}

void
FlushEngine::Run(FastOS_ThreadInterface *thread, void *arg)
{
    (void)thread;
    (void)arg;
    bool shouldIdle = false;
    vespalib::string prevFlushName;
    while (wait(shouldIdle ? _idleIntervalMS : 0)) {
        shouldIdle = false;
        if (prune()) {
            continue; // Prune attempted on one or more handlers
        }
        prevFlushName = flushNextTarget(prevFlushName);
        if ( ! prevFlushName.empty()) {
            // Sleep at least 10 ms after a successful flush in order to avoid busy loop in case
            // of strategy error or target error.
            FastOS_Thread::Sleep(10);
        } else {
            shouldIdle = true;
        }
        LOG(debug, "Making another wait(idle=%s, timeMS=%d) last was '%s'", shouldIdle ? "true" : "false", shouldIdle ? _idleIntervalMS : 0, prevFlushName.c_str());
    }
}

bool
FlushEngine::prune()
{
    std::set<IFlushHandler::SP> toPrune;
    {
        MonitorGuard guard(_monitor);
        if (_pendingPrune.empty()) {
            return false;
        }
        _pendingPrune.swap(toPrune);
    }
    for (const auto &handler : toPrune) {
        IFlushTarget::List lst = handler->getFlushTargets();
        handler->flushDone(findOldestFlushedSerial(lst, *handler));
    }
    return true;
}

bool FlushEngine::isFlushing(const MonitorGuard & guard, const vespalib::string & name) const
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
        MonitorGuard guard(_monitor);
        for (const auto & it : _handlers) {
            IFlushHandler & handler(*it.second);
            search::SerialNum serial(handler.getCurrentSerialNumber());
            LOG(spam, "Checking FlushHandler '%s' current serial = %ld",
                handler.getName().c_str(), serial);
            IFlushTarget::List lst = handler.getFlushTargets();
            for (const IFlushTarget::SP & target : lst) {
                LOG(spam, "Checking target '%s' with flushedSerialNum = %ld", target->getName().c_str(), target->getFlushedSerialNum());
                if (!isFlushing(guard, FlushContext::createName(handler, *target)) || includeFlushingTargets) {
                    ret.push_back(FlushContext::SP(new FlushContext(it.second,
                                                                    IFlushTarget::SP(new CachedFlushTarget(target)),
                                                                    serial)));
                } else {
                    LOG(debug, "Target '%s' with flushedSerialNum = %ld already has a flush going. Local last serial = %ld.",
                               target->getName().c_str(), target->getFlushedSerialNum(), serial);
                }
            }
        }
    }
    return ret;
}

std::pair<FlushContext::List,bool>
FlushEngine::getSortedTargetList(MonitorGuard &strategyGuard) const
{
    (void) strategyGuard;
    FlushContext::List unsortedTargets = getTargetList(false);
    std::pair<FlushContext::List, bool> ret;
    flushengine::TlsStatsMap tlsStatsMap(_tlsStatsFactory->create());
    if (_priorityStrategy) {
        ret = std::make_pair(_priorityStrategy->getFlushTargets(unsortedTargets, tlsStatsMap), true);
    } else {
        ret = std::make_pair(_strategy->getFlushTargets(unsortedTargets, tlsStatsMap), false);
    }
    return ret;
}

FlushContext::SP
FlushEngine::initNextFlush(const FlushContext::List &lst)
{
    FlushContext::SP ctx;
    for (const FlushContext::SP & it : lst) {
        if (LOG_WOULD_LOG(event)) {
            EventLogger::flushInit(it->getName());
        }
        if (it->initFlush()) {
            ctx = it;
            break;
        }
    }
    if (ctx.get() != NULL) {
        LOG(debug, "Target '%s' initiated flush of transactions %" PRIu64 " through %" PRIu64 ".",
                   ctx->getName().c_str(),
                   ctx->getTarget()->getFlushedSerialNum() + 1,
                   ctx->getHandler()->getCurrentSerialNumber());
    }
    return ctx;
}



void
FlushEngine::flushAll(const FlushContext::List &lst)
{
    LOG(debug, "%ld targets to flush.", lst.size());
    for (const FlushContext::SP & ctx : lst) {
        if (wait(0)) {
            if (ctx->initFlush()) {
                LOG(debug, "Target '%s' initiated flush of transactions %" PRIu64 " through %" PRIu64 ".",
                           ctx->getName().c_str(),
                           ctx->getTarget()->getFlushedSerialNum() + 1,
                           ctx->getHandler()->getCurrentSerialNumber());
                _executor.execute(Task::UP(new FlushTask(initFlush(*ctx), *this, ctx)));
            } else {
                LOG(debug, "Target '%s' failed to initiate flush of transactions %" PRIu64 " through %" PRIu64 ".",
                           ctx->getName().c_str(),
                           ctx->getTarget()->getFlushedSerialNum() + 1,
                           ctx->getHandler()->getCurrentSerialNumber());
            }
        }

    }
}

vespalib::string
FlushEngine::flushNextTarget(const vespalib::string & name)
{
    MonitorGuard strategyGuard(_strategyMonitor);
    std::pair<FlushContext::List,bool> lst = getSortedTargetList(strategyGuard);
    if (lst.second) {
        // Everything returned from a priority strategy should be flushed
        flushAll(lst.first);
        _executor.sync();
        _priorityStrategy.reset();
        strategyGuard.broadcast();
        return "";
    }
    if (lst.first.empty()) {
        LOG(debug, "No target to flush.");
        return "";
    }
    FlushContext::SP ctx = initNextFlush(lst.first);
    if (ctx.get() == NULL) {
        LOG(debug, "All targets refused to flush.");
        return "";
    }
    if ( name == ctx->getName()) {
        LOG(info, "The same target %s out of %ld has been asked to flush again. "
                  "This might indicate flush logic flaw so I will wait 1s before doing it.",
                  name.c_str(), lst.first.size());
        FastOS_Thread::Sleep(1000);
    }
    _executor.execute(Task::UP(new FlushTask(initFlush(*ctx), *this, ctx)));
    return ctx->getName();
}

uint32_t
FlushEngine::initFlush(const FlushContext &ctx)
{
    if (LOG_WOULD_LOG(event)) {
        IFlushTarget::MemoryGain mgain(ctx.getTarget()->getApproxMemoryGain());
        EventLogger::flushStart(ctx.getName(),
                                mgain.getBefore(),
                                mgain.getAfter(),
                                mgain.gain(),
                                ctx.getTarget()->getFlushedSerialNum() + 1,
                                ctx.getHandler()->getCurrentSerialNumber());
    }
    return initFlush(ctx.getHandler(), ctx.getTarget());
}

void
FlushEngine::flushDone(const FlushContext &ctx, uint32_t taskId)
{
    fastos::TimeStamp duration;
    {
        MonitorGuard guard(_monitor);
        duration = fastos::TimeStamp(fastos::ClockSystem::now()) - _flushing[taskId].getStart();
    }
    if (LOG_WOULD_LOG(event)) {
        FlushStats stats = ctx.getTarget()->getLastFlushStats();
        EventLogger::flushComplete(ctx.getName(),
                                   duration.ms(),
                                   stats.getPath(),
                                   stats.getPathElementsToLog());
    }
    LOG(debug, "FlushEngine::flushDone(taskId='%d') took '%f' secs", taskId, duration.sec());
    MonitorGuard guard(_monitor);
    _flushing.erase(taskId);
    assert(ctx.getHandler());
    if (_handlers.hasHandler(ctx.getHandler())) {
        _pendingPrune.insert(ctx.getHandler());
    }
    guard.broadcast();
}

IFlushHandler::SP
FlushEngine::putFlushHandler(const DocTypeName &docTypeName,
                             const IFlushHandler::SP &flushHandler)
{
    MonitorGuard guard(_monitor);
    IFlushHandler::SP result(_handlers.putHandler(docTypeName, flushHandler));
    if (result) {
        _pendingPrune.erase(result);
    }
    _pendingPrune.insert(flushHandler);
    return std::move(result);
}

IFlushHandler::SP
FlushEngine::getFlushHandler(const DocTypeName &docTypeName) const
{
    MonitorGuard guard(_monitor);
    return _handlers.getHandler(docTypeName);
}

IFlushHandler::SP
FlushEngine::removeFlushHandler(const DocTypeName &docTypeName)
{
    MonitorGuard guard(_monitor);
    IFlushHandler::SP result(_handlers.removeHandler(docTypeName));
    _pendingPrune.erase(result);
    return std::move(result);
}

FlushEngine::FlushMetaSet
FlushEngine::getCurrentlyFlushingSet() const
{
    FlushMetaSet s;
    vespalib::LockGuard guard(_monitor);
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
        vespalib::LockGuard guard(_monitor);
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
    vespalib::LockGuard strategyLock(_strategyLock);
    MonitorGuard strategyGuard(_strategyMonitor);
    if (_closed) {
        return;
    }
    assert(!_priorityStrategy);
    _priorityStrategy = strategy;
    {
        MonitorGuard guard(_monitor);
        guard.broadcast();
    }
    while (_priorityStrategy) {
        strategyGuard.wait();
    }
}

} // namespace proton
