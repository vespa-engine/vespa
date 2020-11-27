// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/summaryengine/summaryengine.h>
#include <vespa/searchcore/proton/summaryengine/docsum_by_slime.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/searchlib/util/slime_output_raw_buf_adapter.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/searchsummary/docsummary/docsumwriter.h>
#include <vespa/metrics/metricset.h>
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
        return (request.useRootSlime())
               ? std::make_unique<DocsumReply>(createSlimeReply(request.hits.size()))
               : createOldDocSum(request);
    }

    vespalib::Slime::UP createSlimeReply(size_t count) {
        vespalib::Slime::UP response(std::make_unique<vespalib::Slime>());
        Cursor &root = response->setObject();
        Cursor &array = root.setArray(DOCSUMS);
        const Symbol docsumSym = response->insert(DOCSUM);
        for (size_t i = 0; i < count; i++) {
            Cursor &docSumC = array.addObject();
            ObjectSymbolInserter inserter(docSumC, docsumSym);
            inserter.insertObject().setLong("long", 982);
        }
        return response;
    }

    DocsumReply::UP createOldDocSum(const DocsumRequest &request) {
        auto retval = std::make_unique<DocsumReply>();
        for (size_t i = 0; i < request.hits.size(); i++) {
            const DocsumRequest::Hit &h = request.hits[i];
            DocsumReply::Docsum docsum;
            docsum.docid = 10 + i;
            docsum.gid = h.gid;
            docsum.setData(_reply.data(), _reply.size());
            retval->docsums.push_back(docsum);
        }
        return retval;
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
        EXPECT_EQUAL(1u, reply->docsums.size());
        EXPECT_EQUAL(10u, reply->docsums[0].docid);
        EXPECT_EQUAL(GlobalId("aaaaaaaaaaaa"), reply->docsums[0].gid);
        EXPECT_EQUAL("myreply", std::string(reply->docsums[0].data.c_str(), reply->docsums[0].data.size()));
    }
    engine.close();
    { // sync call when engine closed
        DocsumRequest::Source request(createRequest());
        DocsumReply::UP reply = engine.getDocsums(std::move(request), client);
        EXPECT_TRUE(reply);
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
    return EXPECT_EQUAL(vespalib::stringref(expReply),
                        vespalib::stringref(reply->docsums[0].data.c_str(), reply->docsums[0].data.size()));
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

    EXPECT_TRUE(assertDocsumReply(engine, "foo", "foo reply"));
    EXPECT_TRUE(assertDocsumReply(engine, "bar", "bar reply"));
    EXPECT_TRUE(assertDocsumReply(engine, "baz", "baz reply"));
    EXPECT_TRUE(assertDocsumReply(engine, "not", "bar reply")); // uses the first (sorted on name)
    EXPECT_EQUAL(4ul, static_cast<metrics::LongCountMetric *>(engine.getMetrics().getMetric("count"))->getValue());
    EXPECT_EQUAL(4ul, static_cast<metrics::LongCountMetric *>(engine.getMetrics().getMetric("docs"))->getValue());
    EXPECT_LESS(0.0, static_cast<metrics::DoubleAverageMetric *>(engine.getMetrics().getMetric("latency"))->getAverage());
}

using vespalib::Slime;

const char *GID1 = "abcdefghijkl";
const char *GID2 = "bcdefghijklm";

void
verify(vespalib::stringref exp, const Slime &slime) {
    Memory expMemory(exp);
    vespalib::Slime expSlime;
    size_t used = vespalib::slime::JsonFormat::decode(expMemory, expSlime);
    EXPECT_TRUE(used > 0);
    vespalib::SimpleBuffer output;
    vespalib::slime::JsonFormat::encode(slime, output, true);
    Slime reSlimed;
    used = vespalib::slime::JsonFormat::decode(output.get(), reSlimed);
    EXPECT_TRUE(used > 0);
    EXPECT_EQUAL(expSlime, reSlimed);
}

Slime
createSlimeRequestLarger(size_t num,
                         const vespalib::string & sessionId = vespalib::string(),
                         const vespalib::string & ranking = vespalib::string(),
                         const vespalib::string & docType = vespalib::string())
{
    Slime r;
    Cursor &root = r.setObject();
    root.setString("class", "your-summary");
    if ( ! sessionId.empty()) {
        root.setData("sessionid", sessionId);
    }
    if (!ranking.empty()) {
        root.setString("ranking", ranking);
    }
    if (!docType.empty()) {
        root.setString("doctype", docType);
    }
    Cursor &array = root.setArray("gids");
    for (size_t i(0); i < num; i++) {
        array.addData(Memory(GID1, 12));
        array.addData(Memory(GID2, 12));
    }
    return r;
}

Slime
createSlimeRequest(const vespalib::string & sessionId = vespalib::string(),
                   const vespalib::string & ranking = vespalib::string(),
                   const vespalib::string & docType = vespalib::string()) {
    return createSlimeRequestLarger(1, sessionId, ranking, docType);
}

TEST("requireThatSlimeRequestIsConvertedCorrectly") {
    vespalib::Slime slimeRequest = createSlimeRequest();
    TEST_DO(verify("{"
                   "    class: 'your-summary',"
                   "    gids: ["
                   "        '0x6162636465666768696A6B6C',"
                   "        '0x62636465666768696A6B6C6D'"
                   "    ]"
                   "}", slimeRequest));
    DocsumRequest::UP r = DocsumBySlime::slimeToRequest(slimeRequest.get());
    EXPECT_EQUAL("your-summary", r->resultClassName);
    EXPECT_FALSE(r->propertiesMap.cacheProperties().lookup("query").found());
    EXPECT_TRUE(r->sessionId.empty());
    EXPECT_TRUE(r->ranking.empty());
    EXPECT_EQUAL(2u, r->hits.size());
    EXPECT_EQUAL(GlobalId(GID1), r->hits[0].gid);
    EXPECT_EQUAL(GlobalId(GID2), r->hits[1].gid);
}

TEST("require that presence of sessionid affect both request.sessionid and enables cache") {
    vespalib::Slime slimeRequest = createSlimeRequest("1.some.key.7", "my-rank-profile");
    TEST_DO(verify("{"
                   "    class: 'your-summary',"
                   "    sessionid: '0x312E736F6D652E6B65792E37',"
                   "    ranking: 'my-rank-profile',"
                   "    gids: ["
                   "        '0x6162636465666768696A6B6C',"
                   "        '0x62636465666768696A6B6C6D'"
                   "    ]"
                   "}", slimeRequest));
    DocsumRequest::UP r = DocsumBySlime::slimeToRequest(slimeRequest.get());
    EXPECT_EQUAL("your-summary", r->resultClassName);
    EXPECT_EQUAL("my-rank-profile", r->ranking);

    EXPECT_EQUAL(0, strncmp("1.some.key.7", &r->sessionId[0],r->sessionId.size()));
    EXPECT_TRUE(r->propertiesMap.cacheProperties().lookup("query").found());
    EXPECT_EQUAL(2u, r->hits.size());
    EXPECT_EQUAL(GlobalId(GID1), r->hits[0].gid);
    EXPECT_EQUAL(GlobalId(GID2), r->hits[1].gid);
}

TEST("require that 'doctype' affects DocTypeName in a good way...") {
    vespalib::Slime slimeRequest = createSlimeRequest("1.some.key.7", "my-rank-profile", "my-document-type");
    TEST_DO(verify("{"
                           "    class: 'your-summary',"
                           "    sessionid: '0x312E736F6D652E6B65792E37',"
                           "    ranking: 'my-rank-profile',"
                           "    doctype: 'my-document-type',"
                           "    gids: ["
                           "        '0x6162636465666768696A6B6C',"
                           "        '0x62636465666768696A6B6C6D'"
                           "    ]"
                           "}", slimeRequest));
    DocsumRequest::UP r = DocsumBySlime::slimeToRequest(slimeRequest.get());
    EXPECT_EQUAL("your-summary", r->resultClassName);
    EXPECT_EQUAL("my-rank-profile", r->ranking);

    EXPECT_EQUAL(0, strncmp("1.some.key.7", &r->sessionId[0],r->sessionId.size()));
    EXPECT_TRUE(r->propertiesMap.cacheProperties().lookup("query").found());
    EXPECT_TRUE(r->propertiesMap.matchProperties().lookup("documentdb.searchdoctype").found());
    EXPECT_EQUAL(1u, r->propertiesMap.matchProperties().lookup("documentdb.searchdoctype").size());
    EXPECT_EQUAL("my-document-type", r->propertiesMap.matchProperties().lookup("documentdb.searchdoctype").get());
    EXPECT_EQUAL(DocTypeName("my-document-type").getName(), DocTypeName(*r).getName());
    EXPECT_EQUAL(2u, r->hits.size());
    EXPECT_EQUAL(GlobalId(GID1), r->hits[0].gid);
    EXPECT_EQUAL(GlobalId(GID2), r->hits[1].gid);
}

void
createSummary(search::RawBuf &buf) {
    vespalib::Slime summary;
    summary.setObject().setLong("long", 982);
    uint32_t magic = search::docsummary::SLIME_MAGIC_ID;
    buf.append(&magic, sizeof(magic));
    search::SlimeOutputRawBufAdapter adapter(buf);
    BinaryFormat::encode(summary, adapter);
}

class BaseServer {
protected:
    BaseServer() : buf(100)
    {
        createSummary(buf);
    }

protected:
    search::RawBuf buf;
};

class Server : public BaseServer {
public:
    Server();
    ~Server();

private:
    SummaryEngine engine;
    ISearchHandler::SP handler;
public:
    DocsumBySlime docsumBySlime;
    DocsumByRPC docsumByRPC;
};

Server::Server()
    : BaseServer(),
      engine(2),
      handler(std::make_shared<MySearchHandler>("slime", stringref(buf.GetDrainPos(), buf.GetUsedLen()))),
      docsumBySlime(engine),
      docsumByRPC(docsumBySlime)
{
    DocTypeName dtnvfoo("foo");
    engine.putSearchHandler(dtnvfoo, handler);
}

Server::~Server() = default;

vespalib::string
getAnswer(size_t num) {
    vespalib::string s;
    s += "{    docsums: [";
    for (size_t i(1); i < num * 2; i++) {
        s += "        {"
             "            docsum: {"
             "                long: 982"
             "            }"
             "        },";
    }
    s += "        {"
         "            docsum: {"
         "                long: 982"
         "            }"
         "        }"
         "    ]";
    s += "}";
    return s;
}

TEST("requireThatSlimeInterfaceWorksFine") {
    Server server;
    vespalib::Slime slimeRequest = createSlimeRequest();
    vespalib::Slime::UP response = server.docsumBySlime.getDocsums(slimeRequest.get());
    TEST_DO(verify("{"
                   "    docsums: ["
                   "        {"
                   "            docsum: {"
                   "                long: 982"
                   "            }"
                   "        },"
                   "        {"
                   "            docsum: {"
                   "                long: 982"
                   "            }"
                   "        }"
                   "    ]"
                   "}", *response));
}

void
verifyReply(size_t count, CompressionConfig::Type encoding, size_t orgSize, size_t compressedSize,
            FRT_RPCRequest *request) {
    FRT_Values &ret = *request->GetReturn();
    EXPECT_EQUAL(encoding, ret[0]._intval8);
    EXPECT_EQUAL(orgSize, ret[1]._intval32);
    EXPECT_EQUAL(compressedSize, ret[2]._data._len);

    DataBuffer uncompressed;
    ConstBufferRef blob(ret[2]._data._buf, ret[2]._data._len);
    vespalib::compression::decompress(CompressionConfig::toType(ret[0]._intval8), ret[1]._intval32,
                                      blob, uncompressed, false);
    EXPECT_EQUAL(orgSize, uncompressed.getDataLen());

    vespalib::Slime summaries;
    BinaryFormat::decode(Memory(uncompressed.getData(), uncompressed.getDataLen()), summaries);
    TEST_DO(verify(getAnswer(count), summaries));
}

void
verifyRPC(size_t count,
          CompressionConfig::Type requestCompression, size_t requestSize, size_t requestBlobSize,
          CompressionConfig::Type replyCompression, size_t replySize, size_t replyBlobSize) {
    Server server;
    vespalib::Slime slimeRequest = createSlimeRequestLarger(count);
    vespalib::SimpleBuffer buf;
    BinaryFormat::encode(slimeRequest, buf);
    EXPECT_EQUAL(requestSize, buf.get().size);

    CompressionConfig config(requestCompression, 9, 100);
    DataBuffer compressed(const_cast<char *>(buf.get().data), buf.get().size);
    CompressionConfig::Type type = vespalib::compression::compress(config,
                                                                   ConstBufferRef(buf.get().data, buf.get().size),
                                                                   compressed, true);
    EXPECT_EQUAL(type, requestCompression);

    FRT_RPCRequest *request = new FRT_RPCRequest();
    FRT_Values &arg = *request->GetParams();
    arg.AddInt8(type);
    arg.AddInt32(buf.get().size);
    arg.AddData(compressed.getData(), compressed.getDataLen());
    EXPECT_EQUAL(requestBlobSize, compressed.getDataLen());

    server.docsumByRPC.getDocsums(*request);
    verifyReply(count, replyCompression, replySize, replyBlobSize, request);

    request->SubRef();
}

TEST("requireThatRPCInterfaceWorks") {
    verifyRPC(1, CompressionConfig::NONE, 55, 55, CompressionConfig::NONE, 38, 38);
    verifyRPC(100, CompressionConfig::NONE, 2631, 2631, CompressionConfig::LZ4, 1426, 46);
    verifyRPC(100, CompressionConfig::LZ4, 2631, 69, CompressionConfig::LZ4, 1426, 46);
}

}

TEST_MAIN() { TEST_RUN_ALL(); }
