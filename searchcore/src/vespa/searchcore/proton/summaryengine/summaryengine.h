// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "isearchhandler.h"
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/common/handlermap.hpp>
#include <vespa/searchlib/engine/docsumapi.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/metrics/countmetric.h>
#include <vespa/metrics/metricset.h>
#include <mutex>


namespace proton {

class SummaryEngine : public search::engine::DocsumServer
{
private:
    void updateDocsumMetrics(double latency_s, uint32_t numDocs);
    using DocsumReply = search::engine::DocsumReply;
    using DocsumRequest = search::engine::DocsumRequest;
    using DocsumClient = search::engine::DocsumClient;

    struct DocsumMetrics : metrics::MetricSet {
        metrics::LongCountMetric     count;
        metrics::LongCountMetric     docs;
        metrics::DoubleAverageMetric latency;

        DocsumMetrics();
        ~DocsumMetrics();
    };

    std::mutex                    _lock;
    bool                          _closed;
    HandlerMap<ISearchHandler>    _handlers;
    vespalib::ThreadStackExecutor _executor;
    std::unique_ptr<metrics::MetricSet> _metrics;

public:
    SummaryEngine(const SummaryEngine &) = delete;
    SummaryEngine & operator = (const SummaryEngine &) = delete;

    /**
     * Constructs a new summary engine. This does the necessary setup of the
     * internal structures, without actually starting any threads. Before
     * calling start(), you should register handlers for all know document types
     * using the putSearchHandler() method.
     *
     * @param numThreads Number of threads allocated for handling summary requests.
     */
    SummaryEngine(size_t numThreads);

    /**
     * Frees any allocated resources. This will also stop all internal threads
     * and wait for them to finish. All pending docsum requests are deleted.
     */
    ~SummaryEngine();

    /**
     * Observe and reset internal executor stats
     *
     * @return executor stats
     **/
    vespalib::ThreadStackExecutor::Stats getExecutorStats() { return _executor.getStats(); }

    /**
     * Starts the underlying threads. This will throw a vespalib::Exception if
     * it failed to start for any reason.
     */
    void start();

    /**
     * Closes the request handler interface. This will prevent any more data
     * from entering this object, allowing you to flush all pending operations
     * without having to safe-guard against input.
     */
    void close();

    /**
     * Registers a new summary handler for the given document type. If another
     * handler was already registered under the same type, this method will
     * return a pointer to that handler.
     *
     * @param docType        The document type to register a handler for.
     * @param searchHandler The handler to register.
     * @return The replaced handler, if any.
     */
    ISearchHandler::SP putSearchHandler(const DocTypeName &docTypeName, const ISearchHandler::SP &searchHandler);

    /**
     * Returns the summary handler for the given document type. If no handler was
     * registered, this method returns an empty shared pointer.
     *
     * @param docType The document type whose handler to return.
     * @return The registered handler, if any.
     */
    ISearchHandler::SP getSearchHandler(const DocTypeName &docTypeName);

    /**
     * Removes and returns the summary handler for the given document type. If no
     * handler was registered, this method returns an empty shared pointer.
     *
     * @param docType The document type whose handler to remove.
     * @return The removed handler, if any.
     */
    ISearchHandler::SP removeSearchHandler(const DocTypeName &docTypeName);

    // Implements DocsumServer.
    DocsumReply::UP getDocsums(DocsumRequest::Source request, DocsumClient & client) override;

    /**
     * Performs the given docsum request in the current thread and returns the reply.
     *
     * @param req    The docsum request to perform.
     */
    DocsumReply::UP getDocsums(DocsumRequest::UP req) override;

    metrics::MetricSet & getMetrics() { return *_metrics; }
};

} // namespace proton

