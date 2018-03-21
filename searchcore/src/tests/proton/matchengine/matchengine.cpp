// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcore/proton/matchengine/matchengine.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/searchlib/engine/docsumreply.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <mutex>
#include <condition_variable>
#include <chrono>

using namespace proton;
using namespace search::engine;
using namespace vespalib::slime;
using vespalib::Slime;

class MySearchHandler : public ISearchHandler {
    size_t _numHits;
    std::string _name;
    std::string _reply;
public:
    MySearchHandler(size_t numHits = 0) :
        _numHits(numHits), _name("my"), _reply("myreply") {}
    virtual DocsumReply::UP getDocsums(const DocsumRequest &) override {
        return DocsumReply::UP(new DocsumReply);
    }

    virtual search::engine::SearchReply::UP match(
            const ISearchHandler::SP &,
            const search::engine::SearchRequest &,
            vespalib::ThreadBundle &) const override {
        SearchReply::UP retval(new SearchReply);
        for (size_t i = 0; i < _numHits; ++i) {
            retval->hits.push_back(SearchReply::Hit());
        }
        return retval;
    }
};

class LocalSearchClient : public SearchClient {
private:
    std::mutex              _lock;
    std::condition_variable _cond;
    SearchReply::UP         _reply;

public:
    LocalSearchClient();
    ~LocalSearchClient();
    void searchDone(SearchReply::UP reply) override {
        std::lock_guard<std::mutex> guard(_lock);
        _reply = std::move(reply);
        _cond.notify_all();
    }

    SearchReply::UP getReply(uint32_t millis) {
        std::unique_lock<std::mutex> guard(_lock);
        auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(millis);
        while (!_reply) {
            if (_cond.wait_until(guard, deadline) == std::cv_status::timeout) {
                break;
            }
        }
        return std::move(_reply);
    }
};

LocalSearchClient::LocalSearchClient() {}
LocalSearchClient::~LocalSearchClient() {}

TEST("requireThatSearchesExecute")
{
    int numMatcherThreads = 16;
    MatchEngine engine(numMatcherThreads, 1, 7);
    engine.setNodeUp(true);

    MySearchHandler::SP handler(new MySearchHandler);
    DocTypeName dtnvfoo("foo");
    engine.putSearchHandler(dtnvfoo, handler);

    LocalSearchClient client;
    SearchRequest::Source request(new SearchRequest());
    SearchReply::UP reply = engine.search(std::move(request), client);
    EXPECT_TRUE(reply.get() == NULL);

    reply = client.getReply(10000);
    EXPECT_TRUE(reply.get() != NULL);
}

bool
assertSearchReply(MatchEngine & engine, const std::string & searchDocType, size_t expHits)
{
    SearchRequest *request = new SearchRequest();
    request->propertiesMap.lookupCreate(search::MapNames::MATCH).add("documentdb.searchdoctype", searchDocType);
    LocalSearchClient client;
    engine.search(SearchRequest::Source(request), client);
    SearchReply::UP reply = client.getReply(10000);
    return EXPECT_EQUAL(expHits, reply->hits.size());
}

TEST("requireThatCorrectHandlerIsUsed")
{
    MatchEngine engine(1, 1, 7);
    engine.setNodeUp(true);
    ISearchHandler::SP h1(new MySearchHandler(2));
    ISearchHandler::SP h2(new MySearchHandler(4));
    ISearchHandler::SP h3(new MySearchHandler(6));
    DocTypeName dtnvfoo("foo");
    DocTypeName dtnvbar("bar");
    DocTypeName dtnvbaz("baz");
    engine.putSearchHandler(dtnvfoo, h1);
    engine.putSearchHandler(dtnvbar, h2);
    engine.putSearchHandler(dtnvbaz, h3);

    EXPECT_TRUE(assertSearchReply(engine, "foo", 2));
    EXPECT_TRUE(assertSearchReply(engine, "bar", 4));
    EXPECT_TRUE(assertSearchReply(engine, "baz", 6));
    EXPECT_TRUE(assertSearchReply(engine, "not", 4)); // uses the first (sorted on name)
}

struct ObserveBundleMatchHandler : MySearchHandler {
    typedef std::shared_ptr<ObserveBundleMatchHandler> SP;
    mutable size_t bundleSize;
    ObserveBundleMatchHandler() : bundleSize(0) {}

    virtual search::engine::SearchReply::UP match(
            const ISearchHandler::SP &,
            const search::engine::SearchRequest &,
            vespalib::ThreadBundle &threadBundle) const override
    {
        bundleSize = threadBundle.size();
        return SearchReply::UP(new SearchReply);
    }
};

TEST("requireThatBundlesAreUsed")
{
    MatchEngine engine(15, 5, 7);
    engine.setNodeUp(true);

    ObserveBundleMatchHandler::SP handler(new ObserveBundleMatchHandler());
    DocTypeName dtnvfoo("foo");
    engine.putSearchHandler(dtnvfoo, handler);

    LocalSearchClient client;
    SearchRequest::Source request(new SearchRequest());
    engine.search(std::move(request), client);
    SearchReply::UP reply = client.getReply(10000);
    EXPECT_EQUAL(7u, reply->getDistributionKey());
    EXPECT_EQUAL(5u, handler->bundleSize);
}

TEST("requireThatHandlersCanBeRemoved")
{
    MatchEngine engine(1, 1, 7);
    engine.setNodeUp(true);
    ISearchHandler::SP h(new MySearchHandler(1));
    DocTypeName docType("foo");
    engine.putSearchHandler(docType, h);

    ISearchHandler::SP r = engine.getSearchHandler(docType);
    EXPECT_TRUE(r.get() != NULL);
    EXPECT_TRUE(h.get() == r.get());

    r = engine.removeSearchHandler(docType);
    EXPECT_TRUE(r.get() != NULL);
    EXPECT_TRUE(h.get() == r.get());

    r = engine.getSearchHandler(docType);
    EXPECT_TRUE(r.get() == NULL);
}

TEST("requireThatEmptySearchReplyIsReturnedWhenEngineIsClosed")
{
    MatchEngine engine(1, 1, 7);
    engine.setNodeUp(true);
    engine.close();
    LocalSearchClient client;
    SearchRequest::Source request(new SearchRequest());
    SearchReply::UP reply = engine.search(std::move(request), client);
    EXPECT_TRUE(reply.get() != NULL);
    EXPECT_EQUAL(0u, reply->hits.size());
    EXPECT_EQUAL(7u, reply->getDistributionKey());
}

TEST("requireThatStateIsReported")
{
    MatchEngine engine(1, 1, 7);

    Slime slime;
    SlimeInserter inserter(slime);
    engine.get_state(inserter, false);
    EXPECT_EQUAL(
            "{\n"
            "    \"status\": {\n"
            "        \"state\": \"ONLINE\"\n"
            "    }\n"
            "}\n",
            slime.toString());
}

TEST_MAIN() { TEST_RUN_ALL(); }
