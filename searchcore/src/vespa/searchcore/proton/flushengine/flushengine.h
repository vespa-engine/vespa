// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "flushcontext.h"
#include "iflushstrategy.h"
#include <vespa/searchcore/proton/common/handlermap.hpp>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <set>

namespace proton {

namespace flushengine { class ITlsStatsFactory; }

class FlushEngine : public FastOS_Runnable
{
public:
    class FlushMeta {
    public:
        FlushMeta(const vespalib::string & name, fastos::TimeStamp start, uint32_t id);
        ~FlushMeta();
        const vespalib::string & getName() const { return _name; }
        fastos::TimeStamp getStart() const { return _start; }
        uint32_t getId() const { return _id; }
        bool operator < (const FlushMeta & rhs) const { return _id < rhs._id; }
    private:
        vespalib::string  _name;
        fastos::TimeStamp _start;
        uint32_t          _id;
    };
    typedef std::set<FlushMeta> FlushMetaSet;
private:
    struct FlushInfo : public FlushMeta
    {
        FlushInfo();
        FlushInfo(uint32_t taskId,
                  const IFlushTarget::SP &target,
                  const vespalib::string &destination);
        ~FlushInfo();

        IFlushTarget::SP  _target;
    };
    typedef std::map<uint32_t, FlushInfo> FlushMap;
    typedef HandlerMap<IFlushHandler> FlushHandlerMap;
    bool                           _closed;
    const uint32_t                 _maxConcurrent;
    const uint32_t                 _idleIntervalMS;
    uint32_t                       _taskId;    
    FastOS_ThreadPool              _threadPool;
    IFlushStrategy::SP             _strategy;
    mutable IFlushStrategy::SP     _priorityStrategy;
    vespalib::ThreadStackExecutor  _executor;
    vespalib::Monitor              _monitor;
    FlushHandlerMap                _handlers;
    FlushMap                       _flushing;
    vespalib::Lock                 _strategyLock; // serialize setStrategy calls
    vespalib::Monitor              _strategyMonitor;
    std::shared_ptr<flushengine::ITlsStatsFactory> _tlsStatsFactory;
    std::set<IFlushHandler::SP>    _pendingPrune;

    FlushContext::List getTargetList(bool includeFlushingTargets) const;
    std::pair<FlushContext::List,bool> getSortedTargetList(vespalib::MonitorGuard &strategyGuard) const;
    FlushContext::SP initNextFlush(const FlushContext::List &lst);
    vespalib::string flushNextTarget(const vespalib::string & name);
    void flushAll(const FlushContext::List &lst);
    bool prune();
    uint32_t initFlush(const FlushContext &ctx);
    uint32_t initFlush(const IFlushHandler::SP &handler, const IFlushTarget::SP &target);
    void flushDone(const FlushContext &ctx, uint32_t taskId);
    bool canFlushMore(const vespalib::MonitorGuard & guard) const;
    bool wait(size_t minimumWaitTimeIfReady);
    bool isFlushing(const vespalib::MonitorGuard & guard, const vespalib::string & name) const;

    friend class FlushTask;
    friend class FlushEngineExplorer;

public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<FlushEngine> UP;
    typedef std::shared_ptr<FlushEngine> SP;

    /**
     * Constructs a new instance of this class.
     *
     * @param tlsStatsFactory A factory for creating tls statistics
     *                        used by strategy to select best flush candiate.
     * @param strategy   The flushing strategy to use.
     * @param numThreads The number of worker threads to use.
     * @param idleInterval The interval between when flushes are checked whne there are no one progressing.
     */
    FlushEngine(std::shared_ptr<flushengine::ITlsStatsFactory>
                tlsStatsFactory,
                IFlushStrategy::SP strategy, uint32_t numThreads, uint32_t idleIntervalMS);

    /**
     * Destructor. Waits for all pending tasks to complete.
     */
    ~FlushEngine();

    /**
     * Observe and reset internal executor stats
     *
     * @return executor stats
     **/
    vespalib::ThreadStackExecutor::Stats getExecutorStats() { return _executor.getStats(); }

    /**
     * Starts the scheduling thread of this manager.
     *
     * @return This, to allow chaining.
     */
    FlushEngine &start();

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

    void kick(void);

    /**
     * Registers a new flush handler for the given document type. If another
     * handler was already registered under the same type, this method will
     * return a pointer to that handler.
     *
     * @param docType      The document type to register a handler for.
     * @param flushHandler The handler to register.
     * @return The replaced handler, if any.
     */
    IFlushHandler::SP
    putFlushHandler(const DocTypeName &docTypeName,
                    const IFlushHandler::SP &flushHandler);

    /**
     * Returns the flush handler for the given document type. If no handler was
     * registered, this method returns an empty shared pointer.
     *
     * @param docType The document type whose handler to return.
     * @return The registered handler, if any.
     */
    IFlushHandler::SP
    getFlushHandler(const DocTypeName &docTypeName) const;

    /**
     * Removes and returns the flush handler for the given document type. If no
     * handler was registered, this method returns an empty shared pointer.
     *
     * @param docType The document type whose handler to remove.
     * @return The removed handler, if any.
     */
    IFlushHandler::SP
    removeFlushHandler(const DocTypeName &docTypeName);

    // Implements FastOS_Runnable.
    void Run(FastOS_ThreadInterface *thread, void *arg);

    FlushMetaSet getCurrentlyFlushingSet() const;

    void setStrategy(IFlushStrategy::SP strategy);
};

} // namespace proton

