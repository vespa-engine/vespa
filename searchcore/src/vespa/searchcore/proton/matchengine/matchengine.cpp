// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "matchengine.h"
#include <vespa/searchcore/proton/common/state_reporter_utils.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matchengine.matchengine");

namespace {

class SearchTask : public vespalib::Executor::Task {
private:
    proton::MatchEngine                   &_engine;
    search::engine::SearchRequest::Source  _request;
    search::engine::SearchClient          &_client;

public:
    SearchTask(proton::MatchEngine &engine,
               search::engine::SearchRequest::Source request,
               search::engine::SearchClient &client)
        : _engine(engine),
          _request(std::move(request)),
          _client(client)
    {
        // empty
    }

    void run() override {
        _engine.performSearch(std::move(_request), _client);
    }
};


} // namespace anon

namespace proton {

using namespace vespalib::slime;

MatchEngine::MatchEngine(size_t numThreads, size_t threadsPerSearch, uint32_t distributionKey)
    : _lock(),
      _distributionKey(distributionKey),
      _closed(false),
      _handlers(),
      _executor(std::max(size_t(1), numThreads / threadsPerSearch), 256 * 1024),
      _threadBundlePool(std::max(size_t(1), threadsPerSearch)),
      _nodeUp(true)
{
    // empty
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
        _closed = true;
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

search::engine::SearchReply::UP
MatchEngine::search(search::engine::SearchRequest::Source request,
                    search::engine::SearchClient &client)
{
    if (_closed || !_nodeUp) {
        search::engine::SearchReply::UP ret;
        ret.reset(new search::engine::SearchReply);
        ret->setDistributionKey(_distributionKey);

        // TODO: Notify closed.

        return ret;
    }
    vespalib::Executor::Task::UP task;
    task.reset(new SearchTask(*this, std::move(request), client));
    _executor.execute(std::move(task));
    return search::engine::SearchReply::UP();
}

void
MatchEngine::performSearch(search::engine::SearchRequest::Source req,
                           search::engine::SearchClient &client)
{
    search::engine::SearchReply::UP ret(new search::engine::SearchReply);

    if (req.get() != NULL) {
        ISearchHandler::SP searchHandler;
        vespalib::SimpleThreadBundle::UP threadBundle = _threadBundlePool.obtain();
        { // try to find the match handler corresponding to the specified search doc type
            std::lock_guard<std::mutex> guard(_lock);
            DocTypeName docTypeName(*req.get());
            searchHandler = _handlers.getHandler(docTypeName);
        }
        if (searchHandler.get() != NULL) {
            ret = searchHandler->match(searchHandler, *req.get(), *threadBundle);
        } else {
            HandlerMap<ISearchHandler>::Snapshot::UP snapshot;
            {
                std::lock_guard<std::mutex> guard(_lock);
                snapshot = _handlers.snapshot();
            }
            if (snapshot->valid()) {
                ISearchHandler::SP handler = snapshot->getSP();
                ret = handler->match(handler, *req.get(), *threadBundle); // use the first handler
            }
        }
        _threadBundlePool.release(std::move(threadBundle));
    }
    ret->request = req.release();
    ret->setDistributionKey(_distributionKey);
    client.searchDone(std::move(ret));
}

bool MatchEngine::isOnline() const {
    return _nodeUp;
}


void
MatchEngine::setNodeUp(bool nodeUp)
{
    _nodeUp = nodeUp || true;
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
