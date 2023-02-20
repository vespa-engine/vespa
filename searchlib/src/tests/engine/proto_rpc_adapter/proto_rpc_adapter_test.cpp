// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/engine/search_protocol_proto.h>
#include <vespa/searchlib/engine/proto_rpc_adapter.h>
#include <vespa/searchlib/engine/searchapi.h>
#include <vespa/searchlib/engine/docsumapi.h>
#include <vespa/searchlib/engine/monitorapi.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/slime/binary_format.h>
#include <thread>
#include <chrono>

using namespace search::engine;

using vespalib::Slime;
using vespalib::Memory;
using vespalib::slime::BinaryFormat;

using ProtoSearchRequest = ProtoRpcAdapter::ProtoSearchRequest;
using ProtoSearchReply = ProtoRpcAdapter::ProtoSearchReply;
using ProtoDocsumRequest = ProtoRpcAdapter::ProtoDocsumRequest;
using ProtoDocsumReply = ProtoRpcAdapter::ProtoDocsumReply;
using ProtoMonitorRequest = ProtoRpcAdapter::ProtoMonitorRequest;
using ProtoMonitorReply = ProtoRpcAdapter::ProtoMonitorReply;
using QueryStats = SearchProtocolMetrics::QueryStats;
using DocsumStats = SearchProtocolMetrics::DocsumStats;

struct MySearchServer : SearchServer {
    SearchReply::UP search(SearchRequest::Source src, SearchClient &client) override {
        auto req = src.release();
        assert(req);
        auto reply = std::make_unique<SearchReply>();
        reply->totalHitCount = req->offset; // simplified search implementation
        reply->request = std::move(req);
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
        client.searchDone(std::move(reply)); // simplified async response
        return std::unique_ptr<SearchReply>();
    }
};

struct MyDocsumServer : DocsumServer {
    DocsumReply::UP getDocsums(DocsumRequest::Source src, DocsumClient &client) override {
        auto req = src.release();
        assert(req);
        auto slime = std::make_unique<Slime>();
        auto &list = slime->setArray();
        list.addObject().setBool("use_root_slime", true);
        list.addObject().setString("ranking", req->ranking);
        auto reply = std::make_unique<DocsumReply>(std::move(slime), std::move(req));
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
        client.getDocsumsDone(std::move(reply)); // simplified async response
        return std::unique_ptr<DocsumReply>();
    }
};

struct MyMonitorServer : MonitorServer {
    std::unique_ptr<MonitorReply> ping(std::unique_ptr<MonitorRequest> req, MonitorClient &) override {
        (void) req;
        assert(req);
        auto reply = std::make_unique<MonitorReply>();
        reply->activeDocs = 53;
        return reply; // proton does sync response here
    }
};

struct ProtoRpcAdapterTest : ::testing::Test {
    fnet::frt::StandaloneFRT server;
    MySearchServer search;
    MyDocsumServer docsum;
    MyMonitorServer monitor;
    ProtoRpcAdapter adapter;
    ProtoRpcAdapterTest()
        : server(), adapter(search, docsum, monitor, server.supervisor())
    {
        server.supervisor().Listen(0);
    }
    FRT_Target *connect() {
        return server.supervisor().GetTarget(server.supervisor().GetListenPort());
    }
    ~ProtoRpcAdapterTest() override = default;
};

//-----------------------------------------------------------------------------

TEST(QueryMetricTest, require_that_update_query_metrics_works_as_intended) {
    SearchProtocolMetrics metrics;
    QueryStats stats;
    stats.latency = 0.25;
    stats.request_size = 1000;
    stats.reply_size = 500;
    metrics.update_query_metrics(stats);
    EXPECT_EQ(metrics.query().latency.getCount(), 1);
    EXPECT_EQ(metrics.query().latency.getTotal(), 0.25);
    EXPECT_EQ(metrics.query().request_size.getCount(), 1);
    EXPECT_EQ(metrics.query().request_size.getTotal(), 1000);
    EXPECT_EQ(metrics.query().reply_size.getCount(), 1);
    EXPECT_EQ(metrics.query().reply_size.getTotal(), 500);
}

TEST(DocsumMetricTest, require_that_update_docsum_metrics_works_as_intended) {
    SearchProtocolMetrics metrics;
    DocsumStats stats;
    stats.latency = 0.25;
    stats.request_size = 1000;
    stats.reply_size = 500;
    stats.requested_documents = 10;
    metrics.update_docsum_metrics(stats);
    EXPECT_EQ(metrics.docsum().latency.getCount(), 1);
    EXPECT_EQ(metrics.docsum().latency.getTotal(), 0.25);
    EXPECT_EQ(metrics.docsum().request_size.getCount(), 1);
    EXPECT_EQ(metrics.docsum().request_size.getTotal(), 1000);
    EXPECT_EQ(metrics.docsum().reply_size.getCount(), 1);
    EXPECT_EQ(metrics.docsum().reply_size.getTotal(), 500);
    EXPECT_EQ(metrics.docsum().requested_documents.getValue(), 10);
}

TEST_F(ProtoRpcAdapterTest, require_that_plain_rpc_ping_works) {
    auto target = connect();
    auto *req = new FRT_RPCRequest();
    req->SetMethodName("frt.rpc.ping");
    target->InvokeSync(req, 60.0);
    EXPECT_TRUE(req->CheckReturnTypes(""));
    req->SubRef();
    target->SubRef();
}

TEST_F(ProtoRpcAdapterTest, require_that_proto_rpc_search_works) {
    auto target = connect();
    for (bool online: {false, true, true}) {
        auto *rpc = new FRT_RPCRequest();
        ProtoSearchRequest req;
        req.set_offset(42);
        ProtoRpcAdapter::encode_search_request(req, *rpc);
        target->InvokeSync(rpc, 60.0);
        if (online) {
            ProtoSearchReply reply;
            EXPECT_TRUE(ProtoRpcAdapter::decode_search_reply(*rpc, reply));
            EXPECT_EQ(reply.total_hit_count(), 42);
        } else {
            EXPECT_EQ(rpc->GetErrorCode(), FRTE_RPC_METHOD_FAILED);
            EXPECT_EQ(std::string(rpc->GetErrorMessage()), std::string("Server not online"));
            adapter.set_online();
        }
        rpc->SubRef();
    }
    target->SubRef();
    SearchProtocolMetrics &metrics = adapter.metrics();
    EXPECT_EQ(metrics.query().latency.getCount(), 2);
    EXPECT_GT(metrics.query().latency.getTotal(), 0.0);
    EXPECT_GT(metrics.query().request_size.getTotal(), 0);
    EXPECT_GT(metrics.query().reply_size.getTotal(), 0);
    EXPECT_EQ(metrics.docsum().latency.getCount(), 0);
}

TEST_F(ProtoRpcAdapterTest, require_that_proto_rpc_getDocsums_works) {
    auto target = connect();
    for (bool online: {false, true, true}) {
        auto *rpc = new FRT_RPCRequest();
        ProtoDocsumRequest req;
        req.set_rank_profile("mlr");
        req.add_global_ids("foo");
        req.add_global_ids("bar");
        req.add_global_ids("baz");
        ProtoRpcAdapter::encode_docsum_request(req, *rpc);
        target->InvokeSync(rpc, 60.0);
        if (online) {
            ProtoDocsumReply reply;
            EXPECT_TRUE(ProtoRpcAdapter::decode_docsum_reply(*rpc, reply));
            const auto &mem = reply.slime_summaries();
            Slime slime;
            EXPECT_EQ(BinaryFormat::decode(Memory(mem.data(), mem.size()), slime), mem.size());
            EXPECT_EQ(slime.get()[0]["use_root_slime"].asBool(), true);
            EXPECT_EQ(slime.get()[1]["ranking"].asString().make_string(), "mlr");
        } else {
            EXPECT_EQ(rpc->GetErrorCode(), FRTE_RPC_METHOD_FAILED);
            EXPECT_EQ(std::string(rpc->GetErrorMessage()), std::string("Server not online"));
            adapter.set_online();
        }
        rpc->SubRef();
    }
    target->SubRef();
    SearchProtocolMetrics &metrics = adapter.metrics();
    EXPECT_EQ(metrics.query().latency.getCount(), 0);
    EXPECT_EQ(metrics.docsum().latency.getCount(), 2);
    EXPECT_GT(metrics.docsum().latency.getTotal(), 0.0);
    EXPECT_GT(metrics.docsum().request_size.getTotal(), 0);
    EXPECT_GT(metrics.docsum().reply_size.getTotal(), 0);
    EXPECT_EQ(metrics.docsum().requested_documents.getValue(), 6);
}

TEST_F(ProtoRpcAdapterTest, require_that_proto_rpc_ping_works) {
    auto target = connect();
    for (bool online: {false, true, true}) {
        auto *rpc = new FRT_RPCRequest();
        ProtoMonitorRequest req;
        ProtoRpcAdapter::encode_monitor_request(req, *rpc);
        target->InvokeSync(rpc, 60.0);
        if (online) {
            ProtoMonitorReply reply;
            EXPECT_TRUE(ProtoRpcAdapter::decode_monitor_reply(*rpc, reply));
            EXPECT_EQ(reply.active_docs(), 53);
        } else {
            EXPECT_EQ(rpc->GetErrorCode(), FRTE_RPC_METHOD_FAILED);
            EXPECT_EQ(std::string(rpc->GetErrorMessage()), std::string("Server not online"));
            adapter.set_online();
        }
        rpc->SubRef();
    }
    target->SubRef();
    SearchProtocolMetrics &metrics = adapter.metrics();
    EXPECT_EQ(metrics.query().latency.getCount(), 0);
    EXPECT_EQ(metrics.docsum().latency.getCount(), 0);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
