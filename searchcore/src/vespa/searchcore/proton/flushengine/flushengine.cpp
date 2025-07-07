// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flushengine.h"
#include "active_flush_stats.h"
#include "cachedflushtarget.h"
#include "flush_all_strategy.h"
#include "flush_history.h"
#include "flush_strategy_id_notifier.h"
#include "flushtask.h"
#include "tls_stats_factory.h"
#include "tls_stats_map.h"
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchlib/common/flush_token.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <chrono>

#include <vespa/log/log.h>
LOG_SETUP(".proton.flushengine.flushengine");

using Task = vespalib::Executor::Task;
using searchcorespi::FlushStats;
using searchcorespi::IFlushTarget;
using proton::flushengine::FlushHistory;
using proton::flushengine::FlushHistoryEntry;
using proton::flushengine::FlushStrategyIdNotifier;
using vespalib::CpuUsage;
using namespace std::chrono_literals;

namespace proton {

namespace {

std::pair<search::SerialNum, std::string>
findOldestFlushedTarget(const IFlushTarget::List &lst, const IFlushHandler &handler)
{
    search::SerialNum oldestFlushedSerial = handler.getCurrentSerialNumber();
    std::string oldestFlushedName = "null";
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

bool
reuse_strategy(const IFlushStrategy& old_strategy, const IFlushStrategy& strategy) {
    /*
     * If same strategy is already active or queued then reuse it instead of enqueueing a new one.
     * FlushAllStrategy (with name "flush_all") flushes all targets and is thus a superset of
     * PrepareRestartFlushStrategy (with name "prepare_restart"). If the former is active or queued then
     * don't enqueue the latter.
     */
    return (old_strategy.name() == strategy.name() ||
            (old_strategy.name() == "flush_all" && strategy.name() == "prepare_restart"));
}

VESPA_THREAD_STACK_TAG(flush_engine_executor)

}

FlushEngine::FlushMeta::FlushMeta(const std::string& handler_name,
                                  const std::string& target_name, uint32_t id)
    : _name((handler_name.empty() && target_name.empty()) ? "" : FlushContext::create_name(handler_name, target_name)),
      _handler_name(handler_name),
      _timer(),
      _id(id)
{ }
FlushEngine::FlushMeta::~FlushMeta() = default;

FlushEngine::FlushInfo::FlushInfo()
    : FlushMeta("", "", 0),
      _target(),
      _strategy_id(0)
{
}

FlushEngine::FlushInfo::~FlushInfo() = default;


FlushEngine::FlushInfo::FlushInfo(uint32_t taskId, const std::string& handler_name, const IFlushTarget::SP& target,
                                  uint32_t strategy_id)
    : FlushMeta(handler_name, target->getName(), taskId),
      _target(target),
      _strategy_id(strategy_id)
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
      _priority_strategy_queue(),
      _strategy_id(duration_cast<std::chrono::seconds>(std::chrono::system_clock::now().time_since_epoch()).count()),
      _executor(maxConcurrentTotal(), CpuUsage::wrap(flush_engine_executor, CpuUsage::Category::COMPACT)),
      _lock(),
      _cond(),
      _handlers(),
      _flushing(),
      _flushing_strategies(),
      _setStrategyLock(),
      _strategyLock(),
      _strategy_changed(false),
      _lowest_strategy_id_notifier(std::make_shared<FlushStrategyIdNotifier>(_strategy_id)),
      _tlsStatsFactory(std::move(tlsStatsFactory)),
      _pendingPrune(),
      _normal_flush_token(std::make_shared<search::FlushToken>()),
      _gc_flush_token(std::make_shared<search::FlushToken>()),
      _flush_history(std::make_shared<FlushHistory>(_strategy->name(), _strategy_id, _maxConcurrentNormal))
{
    _flushing_strategies[_strategy_id] = 1u; // Account for initial flush strategy
}

FlushEngine::~FlushEngine()
{
    close();
    // All flushes should be completely accounted for
    assert(_flushing_strategies.size() == 1u);
    assert(_flushing_strategies.begin()->first == _strategy_id);
    assert(_flushing_strategies.begin()->second == 1u);
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
        std::unique_lock guard(_lock);
        _gc_flush_token->request_stop(); // Signal active fusion flushes to abort.
        _closed = true;
        _cond.notify_all();
    }
    _lowest_strategy_id_notifier->close();
    if (_thread.joinable()) {
        _thread.join(); // Wait for active flushes to complete or abort and flush engine scheduler thread to exit.
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
    while ( ! canFlushMore(guard, priority) && !is_closed()) {
        _cond.wait_for(guard, 1s); // broadcast when flush done
    }
    return !is_closed();
}

void
FlushEngine::wait_for_slot_or_pending_prune(IFlushTarget::Priority priority)
{
    std::unique_lock<std::mutex> guard(_lock);
    while (! canFlushMore(guard, priority) && !is_closed() && _pendingPrune.empty()) {
        _cond.wait_for(guard, 1s); // broadcast when flush done
    }
}

bool
FlushEngine::has_slot(IFlushTarget::Priority priority)
{
    std::unique_lock<std::mutex> guard(_lock);
    return canFlushMore(guard, priority);
}

std::string
FlushEngine::checkAndFlush(std::string prev) {
    auto lst = getSortedTargetList();
    if (lst._priority_flush) {
        // Everything returned from a priority strategy should be flushed
        flushAll(lst._ctx_list, lst._strategy_id);
        return "[priority_targets]"; // prevent idle_wait in FlushEngine::run()
    } else if ( ! lst._ctx_list.empty()) {
        if (has_slot(IFlushTarget::Priority::NORMAL)) {
            prev = flushNextTarget(prev, lst._ctx_list, lst._strategy_id);
        } else {
            FlushContext::List highPri;
            if (lst._ctx_list.front()->getTarget()->getPriority() > IFlushTarget::Priority::NORMAL) {
                highPri.push_back(lst._ctx_list.front());
            }
            prev = flushNextTarget(prev, highPri, lst._strategy_id);
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
    std::string prevFlushName;
    while (!is_closed()) {
        LOG(debug, "Making another check for something to flush, last was '%s'", prevFlushName.c_str());
        wait_for_slot_or_pending_prune(IFlushTarget::Priority::HIGH);
        if (prune()) {
            // Prune attempted on one or more handlers
        } else if (!is_closed()) {
            prevFlushName = checkAndFlush(prevFlushName);
            if (prevFlushName.empty()) {
                idle_wait(_idleInterval);
            }
        }
    }
    _executor.sync(); // Wait for active flushes to complete or abort
    prune();
    _has_thread = false;
}

namespace {

std::string
createName(const IFlushHandler &handler, const std::string &targetName)
{
    return (handler.getName() + "." + targetName);
}

}

bool
FlushEngine::prune()
{
    PendingPrunes toPrune;
    {
        std::lock_guard<std::mutex> guard(_lock);
        if (_pendingPrune.empty()) {
            return false;
        }
        _pendingPrune.swap(toPrune);
    }
    std::vector<uint32_t> strategy_ids_for_finished_flushes;
    for (const auto& kv : toPrune) {
        const auto& handler = kv.first;
        IFlushTarget::List lst = handler->getFlushTargets();
        auto oldestFlushed = findOldestFlushedTarget(lst, *handler);
        if (LOG_WOULD_LOG(event)) {
            EventLogger::flushPrune(createName(*handler, oldestFlushed.second), oldestFlushed.first);
        }
        handler->flushDone(oldestFlushed.first);
        prune_done(strategy_ids_for_finished_flushes, kv.second);
    }
    prune_flushing_strategies(std::move(strategy_ids_for_finished_flushes));
    return true;
}

void
FlushEngine::prune_done(std::vector<uint32_t>& strategy_ids_for_finished_flushes, const std::vector<PruneMeta>& prune_metas)
{
    for (auto& prune_meta : prune_metas) {
        _flush_history->prune_done(prune_meta._flush_id);
        strategy_ids_for_finished_flushes.emplace_back(prune_meta._strategy_id);
    }
}

void
FlushEngine::prune_flushing_strategies(std::vector<uint32_t> strategy_ids_for_finished_flushes)
{
    if (strategy_ids_for_finished_flushes.empty()) {
        return;
    }
    std::unique_lock guard(_lock);
    for (auto id : strategy_ids_for_finished_flushes) {
        auto it = _flushing_strategies.find(id);
        assert(it != _flushing_strategies.end());
        assert(it->second > 0u);
        --(it->second);
    }
    bool erased = false;
    assert(!_flushing_strategies.empty());
    for (;;) {
        auto it = _flushing_strategies.begin();
        if (it->second != 0) {
            break;
        }
        _flushing_strategies.erase(it);
        erased = true;
        assert(!_flushing_strategies.empty());
    }
    auto lowest_strategy_id = _flushing_strategies.begin()->first;
    if (erased) {
        LOG(debug, "oldest flushing strategy is now %u", _flushing_strategies.begin()->first);
        guard.unlock();
        _lowest_strategy_id_notifier->set_strategy_id(lowest_strategy_id);
    }
}

void
FlushEngine::maybe_apply_changed_strategy(std::vector<uint32_t>& strategy_ids_for_finished_flushes, std::unique_lock<std::mutex>& strategy_guard)
{
    (void) strategy_guard;
    if (!_strategy_changed) {
        return;
    }
    _strategy_changed = false;
    strategy_ids_for_finished_flushes.emplace_back(_strategy_id);
    auto strategy_name = _priorityStrategy ? _priorityStrategy->name() : _strategy->name();
    bool priority_strategy = static_cast<bool>(_priorityStrategy);
    _flush_history->clear_pending_flushes();
    _flush_history->set_strategy(std::move(strategy_name), ++_strategy_id, priority_strategy);
    std::lock_guard guard(_lock);
    auto it = _flushing_strategies.lower_bound(_strategy_id);
    assert(it == _flushing_strategies.end());
    _flushing_strategies.emplace_hint(it, _strategy_id, 1u);
}

void
FlushEngine::mark_active_strategy(uint32_t strategy_id, std::lock_guard<std::mutex>&)
{
    auto it = _flushing_strategies.lower_bound(strategy_id);
    assert(it != _flushing_strategies.end());
    assert(it->second > 0u);
    ++(it->second);
}

bool
FlushEngine::isFlushing(const std::lock_guard<std::mutex> & guard, const std::string & name) const
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

FlushEngine::BoundFlushContextList
FlushEngine::getSortedTargetList()
{
    auto unsortedTargets = getTargetList(false);
    auto tlsStatsMap = _tlsStatsFactory->create();
    auto active_flushes = make_active_flushes(getCurrentlyFlushingSet());
    std::vector<uint32_t> strategy_ids_for_finished_flushes;
    std::unique_lock strategy_guard(_strategyLock);
    maybe_apply_changed_strategy(strategy_ids_for_finished_flushes, strategy_guard);
    BoundFlushContextList ret;
    if (_priorityStrategy) {
        ret = BoundFlushContextList(_priorityStrategy->getFlushTargets(unsortedTargets, tlsStatsMap, active_flushes), _strategy_id, true);
    } else {
        ret = BoundFlushContextList(_strategy->getFlushTargets(unsortedTargets, tlsStatsMap, active_flushes), _strategy_id, false);
    }
    strategy_guard.unlock();
    prune_flushing_strategies(std::move(strategy_ids_for_finished_flushes));
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
FlushEngine::flushAll(const FlushContext::List &lst, uint32_t strategy_id)
{
    LOG(debug, "%ld targets to flush.", lst.size());
    for (const FlushContext::SP & ctx : lst) {
        _flush_history->add_pending_flush(ctx->getHandler()->getName(), ctx->getTarget()->getName(),
                                          ctx->getTarget()->last_flush_duration());
    }
    for (const FlushContext::SP & ctx : lst) {
        if (wait_for_slot(IFlushTarget::Priority::NORMAL)) {
            if (ctx->initFlush(get_flush_token(*ctx))) {
                logTarget("initiated", *ctx);
                _executor.execute(std::make_unique<FlushTask>(initFlush(*ctx, strategy_id), *this, ctx));
            } else {
                logTarget("failed to initiate", *ctx);
                _flush_history->drop_pending_flush(ctx->getHandler()->getName(), ctx->getTarget()->getName());
            }
        }
    }
    // All flushes from priority flush strategy have been started (some might still be ongoing).
    std::lock_guard<std::mutex> strategyGuard(_strategyLock);
    _strategy_changed = true;
    _priorityStrategy.reset();
    if (!_priority_strategy_queue.empty()) {
        _priorityStrategy = _priority_strategy_queue.front();
        _priority_strategy_queue.pop_front();
    }
}

std::string
FlushEngine::flushNextTarget(const std::string & name, const FlushContext::List & contexts, uint32_t strategy_id)
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
    _executor.execute(std::make_unique<FlushTask>(initFlush(*ctx, strategy_id), *this, ctx));
    return ctx->getName();
}

uint32_t
FlushEngine::initFlush(const FlushContext &ctx, uint32_t strategy_id)
{
    if (LOG_WOULD_LOG(event)) {
        IFlushTarget::MemoryGain mgain(ctx.getTarget()->getApproxMemoryGain());
        EventLogger::flushStart(ctx.getName(), mgain.getBefore(), mgain.getAfter(), mgain.gain(),
                                ctx.getTarget()->getFlushedSerialNum() + 1, ctx.getHandler()->getCurrentSerialNumber());
    }
    return initFlush(ctx.getHandler(), ctx.getTarget(), strategy_id);
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
    std::unique_lock guard(_lock);
    /*
     * Hand over any priority flush token, task id and strategy id for completed flush to
     * _pendingPrune, to ensure that flush is considered active and setStrategy will wait until
     * flush engine has called prune().
     */
    std::vector<uint32_t> strategy_ids_for_finished_flushes;
    uint32_t strategy_id = 0;
    {
        auto itr = _flushing.find(taskId);
        assert(itr != _flushing.end());
        strategy_id = itr->second._strategy_id;
        _flush_history->flush_done(taskId);
        _flushing.erase(itr);
    }
    assert(ctx.getHandler());
    assert(strategy_id != 0);
    if (_handlers.hasHandler(ctx.getHandler())) {
        // Handover, prune will call prune_done()
        auto ins_res = _pendingPrune.emplace(ctx.getHandler(), PendingPrunes::mapped_type());
        ins_res.first->second.emplace_back(taskId, strategy_id);
    } else {
        // No handover, handler disappeared, i.e. document type removed
        _flush_history->prune_done(taskId);
        strategy_ids_for_finished_flushes.emplace_back(strategy_id);
    }
    _cond.notify_all();
    guard.unlock();
    prune_flushing_strategies(std::move(strategy_ids_for_finished_flushes));
}

IFlushHandler::SP
FlushEngine::putFlushHandler(const DocTypeName &docTypeName, const IFlushHandler::SP &flushHandler)
{
    std::vector<uint32_t> strategy_ids_for_finished_flushes;
    std::unique_lock guard(_lock);
    IFlushHandler::SP result(_handlers.putHandler(docTypeName, flushHandler));
    if (result) {
        auto it = _pendingPrune.find(result);
        if (it != _pendingPrune.end()) {
            prune_done(strategy_ids_for_finished_flushes, it->second);
            _pendingPrune.erase(it);
        }
    }
    _pendingPrune.emplace(flushHandler, PendingPrunes::mapped_type());
    guard.unlock();
    prune_flushing_strategies(std::move(strategy_ids_for_finished_flushes));
    return result;
}

IFlushHandler::SP
FlushEngine::removeFlushHandler(const DocTypeName &docTypeName)
{
    std::vector<uint32_t> strategy_ids_for_finished_flushes;
    std::unique_lock guard(_lock);
    IFlushHandler::SP result(_handlers.removeHandler(docTypeName));
    if (result) {
        auto it = _pendingPrune.find(result);
        if (it != _pendingPrune.end()) {
            prune_done(strategy_ids_for_finished_flushes, it->second);
            _pendingPrune.erase(it);
        }
    }
    guard.unlock();
    prune_flushing_strategies(std::move(strategy_ids_for_finished_flushes));
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
FlushEngine::initFlush(const IFlushHandler::SP &handler, const IFlushTarget::SP &target, uint32_t strategy_id)
{
    uint32_t taskId;
    {
        std::lock_guard<std::mutex> guard(_lock);
        taskId = _taskId++;
        FlushInfo flush(taskId, handler->getName(), target, strategy_id);
        _flushing[taskId] = flush;
        _flush_history->start_flush(handler->getName(), target->getName(), target->last_flush_duration(),  taskId);
        mark_active_strategy(strategy_id, guard);
    }
    LOG(debug, "FlushEngine::initFlush(handler='%s', target='%s') => taskId='%d'",
        handler->getName().c_str(), target->getName().c_str(), taskId);
    return taskId;
}

uint32_t
FlushEngine::set_strategy_helper(std::shared_ptr<IFlushStrategy> strategy, std::unique_lock<std::mutex>& strategy_guard)
{
    (void) strategy_guard;
    bool need_wakeup = false;
    uint32_t wait_strategy_id = _strategy_id;
    if (!_priorityStrategy) {
        _priorityStrategy = std::move(strategy);
        wait_strategy_id += 2u; // switch to strategy then to next one
        _strategy_changed = true;
        need_wakeup = true;
    } else {
        if (_strategy_changed) {
            wait_strategy_id += 1u; // Account for maybe_apply_changed_strategy detecting switch to _priorityStrategy
        }
        // wait_strategy_id is now the strategy id for _priorityStrategy
        if (reuse_strategy(*_priorityStrategy, *strategy)) {
            // Reuse _priorityStrategy
            wait_strategy_id += 1u;
        } else {
            uint32_t idx = 0;
            for (auto& old_strategy : _priority_strategy_queue) {
                if (reuse_strategy(*old_strategy, *strategy)) {
                    break;
                }
                ++idx;
            }
            if (idx >= _priority_strategy_queue.size()) {
                _priority_strategy_queue.push_back(std::move(strategy));
            }
            // switch to idx non-reused strategies then (possibly reused) strategy, then next one
            wait_strategy_id += idx + 2u;
        }
    }
    if (need_wakeup) {
        std::lock_guard<std::mutex> guard(_lock);
        _cond.notify_all();
    }
    return wait_strategy_id;
}

void
FlushEngine::setStrategy(IFlushStrategy::SP strategy)
{
    auto notifier = _lowest_strategy_id_notifier;
    std::lock_guard<std::mutex> setStrategyGuard(_setStrategyLock);
    std::unique_lock<std::mutex> strategyGuard(_strategyLock);
    if (is_closed()) {
        std::unique_lock guard(_lock);
        return;
    }
    uint32_t wait_strategy_id = set_strategy_helper(std::move(strategy), strategyGuard);
    strategyGuard.unlock();
    /*
     * Wait for flushes started before the strategy change, for
     * flushes initiated by the strategy, and for flush engine to call
     * prune() afterwards.
     */
    notifier->wait_ge_strategy_id(wait_strategy_id);
}

} // namespace proton
