// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "matchengine.h"
#include <vespa/searchcore/proton/common/state_reporter_utils.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/smart_buffer.h>
#include <vespa/vespalib/data/slime/binary_format.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/cpu_usage.h>

#include <vespa/log/log.h>

LOG_SETUP(".proton.matchengine.matchengine");

using search::engine::SearchRequest;
using search::engine::SearchReply;
using search::engine::SearchClient;

using namespace search::fef::indexproperties;

namespace {

class SearchTask : public vespalib::Executor::Task {
private:
    proton::MatchEngine   &_engine;
    SearchRequest::Source  _request;
    SearchClient          &_client;

public:
    SearchTask(proton::MatchEngine &engine, SearchRequest::Source request, SearchClient &client)
        : _engine(engine),
          _request(std::move(request)),
          _client(client)
    { }

    void run() override {
        _client.searchDone(_engine.performSearch(std::move(_request)));
    }
};

VESPA_THREAD_STACK_TAG(match_engine_executor)
VESPA_THREAD_STACK_TAG(match_engine_thread_bundle)

} // namespace anon

namespace proton {

using namespace vespalib::slime;
using vespalib::CpuUsage;

MatchEngine::MatchEngine(size_t numThreads, size_t threadsPerSearch, uint32_t distributionKey, bool async)
    : _lock(),
      _distributionKey(distributionKey),
      _async(async),
      _closed(false),
      _forward_issues(true),
      _handlers(),
      _executor(std::max(size_t(1), numThreads / threadsPerSearch),
                CpuUsage::wrap(match_engine_executor, CpuUsage::Category::READ)),
      _threadBundlePool(std::max(size_t(1), threadsPerSearch),
                        CpuUsage::wrap(match_engine_thread_bundle, CpuUsage::Category::READ)),
      _nodeUp(false),
      _nodeMaintenance(false)
{
}

MatchEngine::~MatchEngine()
{
    _executor.shutdown().sync();
}

void
MatchEngine::close()
{
    LOG(debug, "Closing search interface.");
    {
        std::lock_guard<std::mutex> guard(_lock);
        _closed.store(true, std::memory_order_relaxed);
    }

    LOG(debug, "Handshaking with task manager.");
    _executor.sync();
}

ISearchHandler::SP
MatchEngine::putSearchHandler(const DocTypeName &docTypeName,
                              const ISearchHandler::SP &searchHandler)
{
    std::lock_guard<std::mutex> guard(_lock);
    return _handlers.putHandler(docTypeName, searchHandler);
}

ISearchHandler::SP
MatchEngine::getSearchHandler(const DocTypeName &docTypeName)
{
    std::lock_guard<std::mutex> guard(_lock);
    return _handlers.getHandler(docTypeName);
}

ISearchHandler::SP
MatchEngine::removeSearchHandler(const DocTypeName &docTypeName)
{
    std::lock_guard<std::mutex> guard(_lock);
    return _handlers.removeHandler(docTypeName);
}

SearchReply::UP
MatchEngine::search(SearchRequest::Source request, SearchClient &client)
{
    // We continue to allow searches if the node is in Maintenance mode
    if (_closed.load(std::memory_order_relaxed) || (!_nodeUp && !_nodeMaintenance.load(std::memory_order_relaxed))) {
        auto ret = std::make_unique<SearchReply>();
        ret->setDistributionKey(_distributionKey);

        // TODO: Notify closed.

        return ret;
    }
    if (_async) {
        _executor.execute(std::make_unique<SearchTask>(*this, std::move(request), client));
        return {};
    }
    return performSearch(std::move(request));
}

std::unique_ptr<SearchReply>
MatchEngine::doSearch(const SearchRequest & searchRequest) {
    if (searchRequest.expired()) {
        vespalib::Issue::report("Query timed out in the query Q.");
        return std::make_unique<SearchReply>();
    }
    // 3 is the minimum level required for backend tracing.
    searchRequest.setTraceLevel(trace::Level::lookup(searchRequest.propertiesMap.modelOverrides(),
                                                      searchRequest.trace().getLevel()), 3);
    ISearchHandler::SP searchHandler;
    auto threadBundle = _threadBundlePool.getBundle();
    { // try to find the match handler corresponding to the specified search doc type
        DocTypeName docTypeName(searchRequest);
        std::lock_guard<std::mutex> guard(_lock);
        searchHandler = _handlers.getHandler(docTypeName);
    }
    std::unique_ptr<SearchReply> ret;
    if (searchHandler) {
        ret = searchHandler->match(searchRequest, threadBundle.bundle());
    } else {
        HandlerMap<ISearchHandler>::Snapshot snapshot;
        {
            std::lock_guard<std::mutex> guard(_lock);
            snapshot = _handlers.snapshot();
        }
        ret = (snapshot.valid())
                ? snapshot.get()->match(searchRequest, threadBundle.bundle()) // use the first handler
                :  std::make_unique<SearchReply>();
    }
    if (searchRequest.expired()) {
        vespalib::Issue::report("search request timed out; results may be incomplete");
    }
    return ret;
}

std::unique_ptr<SearchReply>
MatchEngine::performSearch(SearchRequest::Source req)
{
    auto my_issues = std::make_unique<search::UniqueIssues>();
    auto capture_issues = vespalib::Issue::listen(*my_issues);

    const SearchRequest * searchRequest = req.get();
    auto ret = (searchRequest)
            ? doSearch(*searchRequest)
            : std::make_unique<SearchReply>();
    ret->request = req.release();
    if (_forward_issues) {
        ret->my_issues = std::move(my_issues);
    } else {
        my_issues->for_each_message([](const auto &msg){
            LOG(warning, "unhandled issue: %s", msg.c_str());
        });
    }
    ret->setDistributionKey(_distributionKey);
    if ((ret->request->trace().getLevel() > 0) && ret->request->trace().hasTrace()) {
        ret->request->trace().getRoot().setLong("distribution-key", _distributionKey);
        DocTypeName doc_type(*ret->request);
        ret->request->trace().getRoot().setString("document-type", doc_type.getName());
        ret->request->trace().done();
        search::fef::Properties & trace = ret->propertiesMap.lookupCreate("trace");
        vespalib::SmartBuffer output(4_Ki);
        vespalib::slime::BinaryFormat::encode(ret->request->trace().getSlime(), output);
        trace.add("slime", output.obtain().make_stringref());
    }
    return ret;
}

bool MatchEngine::isOnline() const {
    return _nodeUp.load(std::memory_order_relaxed);
}


void
MatchEngine::setNodeUp(bool nodeUp)
{
    _nodeUp.store(nodeUp, std::memory_order_relaxed);
}

void
MatchEngine::setNodeMaintenance(bool nodeMaintenance)
{
    _nodeMaintenance.store(nodeMaintenance, std::memory_order_relaxed);
    if (nodeMaintenance) {
        _nodeUp.store(false, std::memory_order_relaxed);
    }
}

StatusReport::UP
MatchEngine::reportStatus() const
{
    if (isOnline()) {
        return StatusReport::create(StatusReport::Params("matchengine").
                state(StatusReport::UPOK).
                internalState("ONLINE"));
    } else {
        return StatusReport::create(StatusReport::Params("matchengine").
                state(StatusReport::DOWN).
                internalState("OFFLINE").
                message("Search interface is offline"));
    }
}

void
MatchEngine::get_state(const Inserter &inserter, bool full) const
{
    (void) full;
    Cursor &object = inserter.insertObject();
    StateReporterUtils::convertToSlime(*reportStatus(), ObjectInserter(object, "status"));
}

} // namespace proton
