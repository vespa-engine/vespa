// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/engine/search_protocol_proto.h>
#include <vespa/searchlib/engine/proto_rpc_adapter.h>
#include <vespa/searchlib/engine/searchapi.h>
#include <vespa/searchlib/engine/docsumapi.h>
#include <vespa/searchlib/engine/monitorapi.h>
#include <vespa/fnet/frt/frt.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/slime/binary_format.h>

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Winline"

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

struct MySearchServer : SearchServer {
    SearchReply::UP search(SearchRequest::Source src, SearchClient &client) override {
        auto req = src.release();
        assert(req);
        auto reply = std::make_unique<SearchReply>();
        reply->totalHitCount = req->offset; // simplified search implementation
        client.searchDone(std::move(reply)); // simplified async response
        return std::unique_ptr<SearchReply>();
    }
};

struct MyDocsumServer : DocsumServer {
    DocsumReply::UP getDocsums(DocsumRequest::Source src, DocsumClient &client) override {
        auto req = src.release();
        assert(req);
        auto reply = std::make_unique<DocsumReply>();
        reply->_root = std::make_unique<Slime>();
        auto &list = reply->_root->setArray();
        list.addObject().setBool("use_root_slime", req->useRootSlime());
        list.addObject().setString("ranking", req->ranking);
        client.getDocsumsDone(std::move(reply)); // simplified async response
        return std::unique_ptr<DocsumReply>();
    }
};

struct MyMonitorServer : MonitorServer {
    MonitorReply::UP ping(MonitorRequest::UP req, MonitorClient &) override {
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
    ~ProtoRpcAdapterTest() = default;
};

//-----------------------------------------------------------------------------

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
}

TEST_F(ProtoRpcAdapterTest, require_that_proto_rpc_getDocsums_works) {
    auto target = connect();
    for (bool online: {false, true, true}) {
        auto *rpc = new FRT_RPCRequest();
        ProtoDocsumRequest req;
        req.set_rank_profile("mlr");
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
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()

#pragma GCC diagnostic pop
