// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "summaryengine.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/cpu_usage.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.summaryengine.summaryengine");

using namespace search::engine;
using namespace proton;
using vespalib::Memory;
using vespalib::slime::Inspector;
using vespalib::CpuUsage;

namespace {

Memory DOCSUMS("docsums");

class DocsumTask : public vespalib::Executor::Task {
private:
    SummaryEngine       & _engine;
    DocsumClient        & _client;
    DocsumRequest::Source _request;

public:
    DocsumTask(SummaryEngine & engine, DocsumRequest::Source request, DocsumClient & client)
        : _engine(engine),
          _client(client),
          _request(std::move(request))
    {
    }

    void run() override {
        _client.getDocsumsDone(_engine.getDocsums(_request.release()));
    }
};

uint32_t getNumDocs(const DocsumReply &reply) {
    const Inspector &root = reply.root();
    return root[DOCSUMS].entries();
}

VESPA_THREAD_STACK_TAG(summary_engine_executor)

} // namespace anonymous

namespace proton {

SummaryEngine::DocsumMetrics::DocsumMetrics()
    : metrics::MetricSet("docsum", {}, "Docsum metrics", nullptr),
      count("count", {{"logdefault"}}, "Docsum requests handled", this),
      docs("docs", {{"logdefault"}}, "Total docsums returned", this),
      latency("latency", {{"logdefault"}}, "Docsum request latency", this)
{
}

SummaryEngine::DocsumMetrics::~DocsumMetrics() = default;

SummaryEngine::SummaryEngine(size_t numThreads, bool async)
    : _lock(),
      _async(async),
      _closed(false),
      _forward_issues(true),
      _handlers(),
      _executor(numThreads, 128_Ki, CpuUsage::wrap(summary_engine_executor, CpuUsage::Category::READ)),
      _metrics(std::make_unique<DocsumMetrics>())
{ }

SummaryEngine::~SummaryEngine()
{
    _executor.shutdown();
}

void
SummaryEngine::close()
{
    LOG(debug, "Closing summary engine");
    {
        std::lock_guard<std::mutex> guard(_lock);
        _closed = true;
    }
    LOG(debug, "Handshaking with task manager");
    _executor.sync();
}

ISearchHandler::SP
SummaryEngine::putSearchHandler(const DocTypeName &docTypeName, const ISearchHandler::SP & searchHandler)
{
    std::lock_guard<std::mutex> guard(_lock);
    return _handlers.putHandler(docTypeName, searchHandler);
}

ISearchHandler::SP
SummaryEngine::getSearchHandler(const DocTypeName &docTypeName)
{
    std::lock_guard<std::mutex> guard(_lock);
    return _handlers.getHandler(docTypeName);
}

ISearchHandler::SP
SummaryEngine::removeSearchHandler(const DocTypeName &docTypeName)
{
    std::lock_guard<std::mutex> guard(_lock);
    return _handlers.removeHandler(docTypeName);
}

DocsumReply::UP
SummaryEngine::getDocsums(DocsumRequest::Source request, DocsumClient & client)
{
    if (_closed) {
        vespalib::Issue::report("Received docsum request after engine has been shutdown");
        auto ret = std::make_unique<DocsumReply>();
        return ret;
    }
    if (_async) {
        auto task = std::make_unique<DocsumTask>(*this, std::move(request), client);
        _executor.execute(std::move(task));
        return DocsumReply::UP();
    }
    return getDocsums(request.release());
}

DocsumReply::UP
SummaryEngine::getDocsums(DocsumRequest::UP req)
{
    auto my_issues = std::make_unique<search::UniqueIssues>();
    auto capture_issues = vespalib::Issue::listen(*my_issues);

    DocsumReply::UP reply;
    if (req) {
        ISearchHandler::SP searchHandler = getSearchHandler(DocTypeName(*req));
        if (searchHandler) {
            reply = searchHandler->getDocsums(*req);
        } else {
            HandlerMap<ISearchHandler>::Snapshot snapshot;
            {
                std::lock_guard<std::mutex> guard(_lock);
                snapshot = _handlers.snapshot();
            }
            if (snapshot.valid()) {
                reply = snapshot.get()->getDocsums(*req); // use the first handler
            }
        }
        updateDocsumMetrics(vespalib::to_s(req->getTimeUsed()), getNumDocs(*reply));
        if (req->expired()) {
            vespalib::Issue::report("docsum request timed out; results may be incomplete");
        }
    }
    if (! reply) {
        reply = std::make_unique<DocsumReply>();
    }
    reply->setRequest(std::move(req));
    if (_forward_issues) {
        reply->setIssues(std::move(my_issues));
    } else {
        my_issues->for_each_message([](const auto &msg){
            LOG(warning, "unhandled issue: %s", msg.c_str());
        });
    }
    return reply;
}

void
SummaryEngine::updateDocsumMetrics(double latency_s, uint32_t numDocs)
{
    std::lock_guard guard(_lock);
    DocsumMetrics & m = static_cast<DocsumMetrics &>(*_metrics);
    m.count.inc();
    m.docs.inc(numDocs);
    m.latency.set(latency_s);
}

} // namespace proton
