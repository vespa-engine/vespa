// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "flushcontext.h"
#include "iflushstrategy.h"
#include <vespa/searchcore/proton/common/handlermap.hpp>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/time.h>
#include <thread>
#include <set>
#include <mutex>
#include <condition_variable>

namespace search { class FlushToken; }

namespace proton {

namespace flushengine { class ITlsStatsFactory; }

class FlushEngine
{
public:
    class FlushMeta {
    public:
        FlushMeta(const vespalib::string& handler_name, const vespalib::string& target_name, uint32_t id);
        ~FlushMeta();
        const vespalib::string & getName() const { return _name; }
        const vespalib::string& handler_name() const { return _handler_name; }
        vespalib::system_time getStart() const { return vespalib::to_utc(_timer.get_start()); }
        vespalib::duration elapsed() const { return _timer.elapsed(); }
        uint32_t getId() const { return _id; }
        bool operator < (const FlushMeta & rhs) const { return _id < rhs._id; }
    private:
        vespalib::string  _name;
        vespalib::string  _handler_name;
        vespalib::Timer   _timer;
        uint32_t          _id;
    };
    using FlushMetaSet = std::set<FlushMeta>;
private:
    using IFlushTarget = searchcorespi::IFlushTarget;
    struct FlushInfo : public FlushMeta
    {
        FlushInfo();
        FlushInfo(uint32_t taskId, const vespalib::string& handler_name, const IFlushTarget::SP &target);
        ~FlushInfo();

        IFlushTarget::SP  _target;
    };
    using FlushMap = std::map<uint32_t, FlushInfo>;
    using FlushHandlerMap = HandlerMap<IFlushHandler>;
    std::atomic<bool>              _closed;
    const uint32_t                 _maxConcurrentNormal;
    const vespalib::duration       _idleInterval;
    uint32_t                       _taskId;    
    std::thread                    _thread;
    std::atomic<bool>              _has_thread;
    IFlushStrategy::SP             _strategy;
    mutable IFlushStrategy::SP     _priorityStrategy;
    vespalib::ThreadStackExecutor  _executor;
    mutable std::mutex             _lock;
    std::condition_variable        _cond;
    FlushHandlerMap                _handlers;
    FlushMap                       _flushing;
    std::mutex                     _setStrategyLock; // serialize setStrategy calls
    std::mutex                     _strategyLock;
    std::condition_variable        _strategyCond;
    std::shared_ptr<flushengine::ITlsStatsFactory> _tlsStatsFactory;
    std::set<IFlushHandler::SP>    _pendingPrune;
    std::shared_ptr<search::FlushToken> _normal_flush_token;
    std::shared_ptr<search::FlushToken> _gc_flush_token;

    FlushContext::List getTargetList(bool includeFlushingTargets) const;
    std::pair<FlushContext::List,bool> getSortedTargetList();
    std::shared_ptr<search::IFlushToken> get_flush_token(const FlushContext& ctx);
    FlushContext::SP initNextFlush(const FlushContext::List &lst);
    vespalib::string flushNextTarget(const vespalib::string & name, const FlushContext::List & contexts);
    void flushAll(const FlushContext::List &lst);
    bool prune();
    uint32_t initFlush(const FlushContext &ctx);
    uint32_t initFlush(const IFlushHandler::SP &handler, const IFlushTarget::SP &target);
    void flushDone(const FlushContext &ctx, uint32_t taskId);
    bool canFlushMore(const std::unique_lock<std::mutex> &guard, IFlushTarget::Priority priority) const;
    void wait_for_slot_or_pending_prune(IFlushTarget::Priority priority);
    void idle_wait(vespalib::duration minimumWaitTimeIfReady);
    bool wait_for_slot(IFlushTarget::Priority priority);
    bool has_slot(IFlushTarget::Priority priority);
    bool isFlushing(const std::lock_guard<std::mutex> &guard, const vespalib::string & name) const;
    vespalib::string checkAndFlush(vespalib::string prev);

    friend class FlushTask;
    friend class FlushEngineExplorer;

public:
    /**
     * Convenience typedefs.
     */
    using UP = std::unique_ptr<FlushEngine>;
    using SP = std::shared_ptr<FlushEngine>;

    /**
     * Constructs a new instance of this class.
     *
     * @param tlsStatsFactory A factory for creating tls statistics
     *                        used by strategy to select best flush candiate.
     * @param strategy   The flushing strategy to use.
     * @param numThreads The number of worker threads to use.
     * @param idleInterval The interval between when flushes are checked whne there are no one progressing.
     */
    FlushEngine(std::shared_ptr<flushengine::ITlsStatsFactory> tlsStatsFactory,
                IFlushStrategy::SP strategy, uint32_t numThreads, vespalib::duration idleInterval);

    /**
     * Destructor. Waits for all pending tasks to complete.
     */
    ~FlushEngine();

    /**
     * Observe and reset internal executor stats
     *
     * @return executor stats
     **/
    vespalib::ExecutorStats getExecutorStats() { return _executor.getStats(); }

    /**
     * Returns the underlying executor. Only used for state explorers.
     */
    const vespalib::ThreadExecutor& get_executor() const { return _executor; }

    /**
     * Starts the scheduling thread of this manager.
     *
     * @return This, to allow chaining.
     */
    FlushEngine &start();
    bool has_thread() const { return _has_thread; }
    
    /**
     * Stops the scheduling thread and. This will prevent any more flush
     * requests being performed on the attached handlers, allowing you to flush
     * all pending operations without having to safe-guard against this.
     *
     * @return This, to allow chaining.
     */
    FlushEngine &close();

    /**
     * Triggers an immediate flush of all flush targets.
     * This method is synchronous and thread-safe.
     */
    void triggerFlush();

    void kick();

    /**
     * Registers a new flush handler for the given document type. If another
     * handler was already registered under the same type, this method will
     * return a pointer to that handler.
     *
     * @param docType      The document type to register a handler for.
     * @param flushHandler The handler to register.
     * @return The replaced handler, if any.
     */
    IFlushHandler::SP putFlushHandler(const DocTypeName &docTypeName, const IFlushHandler::SP &flushHandler);


    /**
     * Removes and returns the flush handler for the given document type. If no
     * handler was registered, this method returns an empty shared pointer.
     *
     * @param docType The document type whose handler to remove.
     * @return The removed handler, if any.
     */
    IFlushHandler::SP removeFlushHandler(const DocTypeName &docTypeName);

    void run();

    FlushMetaSet getCurrentlyFlushingSet() const;

    void setStrategy(IFlushStrategy::SP strategy);
    uint32_t maxConcurrentTotal() const { return _maxConcurrentNormal + 1; }
    uint32_t maxConcurrentNormal() const { return _maxConcurrentNormal; }
};

} // namespace proton

