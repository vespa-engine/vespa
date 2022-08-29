// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/searchcore/proton/summaryengine/summaryengine.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/fnet/frt/rpcrequest.h>

#include <vespa/log/log.h>

LOG_SETUP("summaryengine_test");

using namespace search::engine;
using namespace document;
using namespace vespalib::slime;
using vespalib::stringref;
using vespalib::ConstBufferRef;
using vespalib::DataBuffer;
using vespalib::Memory;
using vespalib::compression::CompressionConfig;

vespalib::string
getAnswer(size_t num, const char * reply = "myreply") {
    vespalib::string s;
    s += "{";
    s += "  docsums: [";
    for (size_t i = 0; i < num; i++) {
        if (i > 0) {
            s += ",";
        }
        s += "{docsum:{long:";
        s += vespalib::make_string("%zu", 982+i);
        s += ",str:'";
        s+= reply;
        s+= "'}}";
    }
    s += "  ]";
    s += "}";
    return s;
}

namespace proton {

namespace {
stringref MYREPLY("myreply");
Memory DOCSUMS("docsums");
Memory DOCSUM("docsum");
}

class MySearchHandler : public ISearchHandler {
    std::string _name;
    stringref _reply;
public:
    MySearchHandler(const std::string &name = "my", stringref reply = MYREPLY)
        : _name(name), _reply(reply)
    {}

    DocsumReply::UP getDocsums(const DocsumRequest &request) override {
        return std::make_unique<DocsumReply>(createSlimeReply(request.hits.size()));
    }

    vespalib::Slime::UP createSlimeReply(size_t count) {
        vespalib::Slime::UP response(std::make_unique<vespalib::Slime>());
        Cursor &root = response->setObject();
        Cursor &array = root.setArray(DOCSUMS);
        const Symbol docsumSym = response->insert(DOCSUM);
        for (size_t i = 0; i < count; i++) {
            Cursor &docSumC = array.addObject();
            ObjectSymbolInserter inserter(docSumC, docsumSym);
            Cursor &obj = inserter.insertObject();
            obj.setLong("long", 982+i);
            obj.setString("str", _reply);
        }
        return response;
    }

    SearchReply::UP match(const SearchRequest &, vespalib::ThreadBundle &) const override {
        return std::make_unique<SearchReply>();
    }
};

class MyDocsumClient : public DocsumClient {
private:
    std::mutex              _lock;
    std::condition_variable _cond;
    DocsumReply::UP         _reply;

public:
    MyDocsumClient();
    ~MyDocsumClient() override;

    void getDocsumsDone(DocsumReply::UP reply) override {
        std::lock_guard<std::mutex> guard(_lock);
        _reply = std::move(reply);
        _cond.notify_all();
    }

    DocsumReply::UP getReply(uint32_t millis) {
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

MyDocsumClient::MyDocsumClient() = default;

MyDocsumClient::~MyDocsumClient() = default;

DocsumRequest::UP
createRequest(size_t num = 1) {
    auto r = std::make_unique<DocsumRequest>();
    if (num == 1) {
        r->hits.emplace_back(GlobalId("aaaaaaaaaaaa"));
    } else {
        for (size_t i = 0; i < num; i++) {
            vespalib::string s = vespalib::make_string("aaaaaaaaaaa%c", char('a' + i % 26));
            r->hits.emplace_back(GlobalId(s.c_str()));
        }
    }
    return r;
}

void assertSlime(const std::string &exp, const DocsumReply &reply) {
    vespalib::Slime expSlime;
    size_t used = JsonFormat::decode(exp, expSlime);
    EXPECT_TRUE(used > 0);
    ASSERT_TRUE(reply.hasResult());
    EXPECT_EQUAL(expSlime, reply.slime());
}

TEST("requireThatGetDocsumsExecute") {
    int numSummaryThreads = 2;
    SummaryEngine engine(numSummaryThreads);
    auto handler = std::make_shared<MySearchHandler>();
    DocTypeName dtnvfoo("foo");
    engine.putSearchHandler(dtnvfoo, handler);

    MyDocsumClient client;
    { // async call when engine running
        DocsumRequest::Source request(createRequest());
        DocsumReply::UP reply = engine.getDocsums(std::move(request), client);
        EXPECT_FALSE(reply);
        reply = client.getReply(10000);
        EXPECT_TRUE(reply);
        assertSlime("{docsums:[{docsum:{long:982,str:'myreply'}}]}", *reply);
    }
    engine.close();
    { // sync call when engine closed
        DocsumRequest::Source request(createRequest());
        DocsumReply::UP reply = engine.getDocsums(std::move(request), client);
        EXPECT_TRUE(reply);
        EXPECT_FALSE(reply->hasResult());
    }
}

TEST("requireThatHandlersAreStored") {
    DocTypeName dtnvfoo("foo");
    DocTypeName dtnvbar("bar");
    int numSummaryThreads = 2;
    SummaryEngine engine(numSummaryThreads);
    auto h1 = std::make_shared<MySearchHandler>("foo");
    auto h2 = std::make_shared<MySearchHandler>("bar");
    auto h3 = std::make_shared<MySearchHandler>("baz");
    // not found
    EXPECT_FALSE(engine.getSearchHandler(dtnvfoo));
    EXPECT_FALSE(engine.removeSearchHandler(dtnvfoo));
    // put & get
    EXPECT_FALSE(engine.putSearchHandler(dtnvfoo, h1));
    EXPECT_EQUAL(engine.getSearchHandler(dtnvfoo).get(), h1.get());
    EXPECT_FALSE(engine.putSearchHandler(dtnvbar, h2));
    EXPECT_EQUAL(engine.getSearchHandler(dtnvbar).get(), h2.get());
    // replace
    EXPECT_TRUE(engine.putSearchHandler(dtnvfoo, h3).get() == h1.get());
    EXPECT_EQUAL(engine.getSearchHandler(dtnvfoo).get(), h3.get());
    // remove
    EXPECT_EQUAL(engine.removeSearchHandler(dtnvfoo).get(), h3.get());
    EXPECT_FALSE(engine.getSearchHandler(dtnvfoo));
}

bool
assertDocsumReply(SummaryEngine &engine, const std::string &searchDocType, stringref expReply) {
    DocsumRequest::UP request(createRequest());
    request->propertiesMap.lookupCreate(search::MapNames::MATCH).add("documentdb.searchdoctype", searchDocType);
    MyDocsumClient client;
    engine.getDocsums(DocsumRequest::Source(std::move(request)), client);
    DocsumReply::UP reply = client.getReply(10000);
    assertSlime(expReply, *reply);
    return true;
}

TEST("requireThatCorrectHandlerIsUsed") {
    DocTypeName dtnvfoo("foo");
    DocTypeName dtnvbar("bar");
    DocTypeName dtnvbaz("baz");
    SummaryEngine engine(1);
    auto h1 = std::make_shared<MySearchHandler>("foo", "foo reply");
    auto h2 = std::make_shared<MySearchHandler>("bar", "bar reply");
    auto h3 = std::make_shared<MySearchHandler>("baz", "baz reply");
    engine.putSearchHandler(dtnvfoo, h1);
    engine.putSearchHandler(dtnvbar, h2);
    engine.putSearchHandler(dtnvbaz, h3);

    EXPECT_TRUE(assertDocsumReply(engine, "foo", getAnswer(1, "foo reply")));
    EXPECT_TRUE(assertDocsumReply(engine, "bar", getAnswer(1, "bar reply")));
    EXPECT_TRUE(assertDocsumReply(engine, "baz", getAnswer(1, "baz reply")));
    EXPECT_TRUE(assertDocsumReply(engine, "not", getAnswer(1, "bar reply"))); // uses the first (sorted on name)
    EXPECT_EQUAL(4ul, static_cast<metrics::LongCountMetric *>(engine.getMetrics().getMetric("count"))->getValue());
    EXPECT_EQUAL(4ul, static_cast<metrics::LongCountMetric *>(engine.getMetrics().getMetric("docs"))->getValue());
    EXPECT_LESS(0.0, static_cast<metrics::DoubleAverageMetric *>(engine.getMetrics().getMetric("latency"))->getAverage());
}

}

TEST_MAIN() { TEST_RUN_ALL(); }
