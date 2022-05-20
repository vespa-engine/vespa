// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/common/handlermap.hpp>
#include <vespa/searchcore/proton/common/statusreport.h>
#include <vespa/searchlib/engine/searchapi.h>
#include <vespa/vespalib/net/http/state_explorer.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/simple_thread_bundle.h>
#include <mutex>

namespace proton {

class MatchEngine : public search::engine::SearchServer,
                    public vespalib::StateExplorer
{
private:
    std::mutex                         _lock;
    const uint32_t                     _distributionKey;
    bool                               _async;
    bool                               _closed;
    std::atomic<bool>                  _forward_issues;
    HandlerMap<ISearchHandler>         _handlers;
    vespalib::ThreadStackExecutor      _executor;
    vespalib::SimpleThreadBundle::Pool _threadBundlePool;
    std::atomic<bool>                  _nodeUp;
    std::atomic<bool>                  _nodeMaintenance;

public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<MatchEngine> UP;
    typedef std::shared_ptr<MatchEngine> SP;
    MatchEngine(const MatchEngine &) = delete;
    MatchEngine & operator = (const MatchEngine &) = delete;

    /**
     * Constructs a new match engine. This does the necessary setup of the
     * internal structures, without actually starting any threads. Before
     * searching, you should register handlers for all known document types
     * using the putSearchHandler() method.
     *
     * @param numThreads Number of threads allocated for handling search requests.
     * @param threadsPerSearch number of threads used for each search
     * @param distributionKey distributionkey of this node.
     * @param async if query is dispatched to threadpool
     */
    MatchEngine(size_t numThreads, size_t threadsPerSearch, uint32_t distributionKey, bool async);
    MatchEngine(size_t numThreads, size_t threadsPerSearch, uint32_t distributionKey)
        : MatchEngine(numThreads, threadsPerSearch, distributionKey, true)
    {}

    /**
     * Frees any allocated resources. this will also stop all internal threads
     * and wait for them to finish. All pending search requests are deleted.
     */
    ~MatchEngine() override;

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
     * Closes the request handler interface. This will prevent any more data
     * from entering this object, allowing you to flush all pending operations
     * without having to safe-guard against input.
     */
    void close();

    /**
     * Registers a new search handler for the given document type. If another
     * handler was already registered under the same type, this method will
     * return a pointer to that handler.
     *
     * @param docType      The document type to register a handler for.
     * @param matchHandler The handler to register.
     * @return The replaced handler, if any.
     */
    ISearchHandler::SP
    putSearchHandler(const DocTypeName &docTypeName,
                     const ISearchHandler::SP &searchHandler);

    /**
     * Returns the search handler for the given document type. If no
     * handler was registered, this method returns an empty shared
     * pointer.
     *
     * @param docType The document type whose handler to return.
     * @return The registered handler, if any.
     */
    ISearchHandler::SP
    getSearchHandler(const DocTypeName &docTypeName);

    /**
     * Removes and returns the search handler for the given document
     * type. If no handler was registered, this method returns an
     * empty shared pointer.
     *
     * @param docType The document type whose handler to remove.
     * @return The removed handler, if any.
     */
    ISearchHandler::SP
    removeSearchHandler(const DocTypeName &docTypeName);

    /**
     * Performs the given search request in the current thread and passes the
     * result to the given client. This method is used by the interal worker
     * threads.
     *
     * @param req    The search request to perform.
     * @param client The client to pass the results to.
     */
    std::unique_ptr<search::engine::SearchReply>
    performSearch(search::engine::SearchRequest::Source req);

    /** obtain current online status */
    bool isOnline() const;

    /** 
     * Set node up/down, based on info from cluster controller.
     */
    void setNodeUp(bool nodeUp);

    /**
     * Set node into maintenance, based on info from cluster controller. Note that
     * nodeMaintenance == true also implies setNodeUp(false), as the node is technically
     * not in a Up state.
     */
    void setNodeMaintenance(bool nodeMaintenance);

    StatusReport::UP reportStatus() const;

    search::engine::SearchReply::UP search(
            search::engine::SearchRequest::Source request,
            search::engine::SearchClient &client) override;

    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;

    void set_issue_forwarding(bool enable) { _forward_issues = enable; }
};

} // namespace proton

