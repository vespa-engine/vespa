// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proto_rpc_adapter.h"
#include "searchapi.h"
#include "docsumapi.h"
#include "monitorapi.h"
#include <vespa/fnet/frt/require_capabilities.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/searchlib/common/packets.h>

#include <vespa/log/log.h>
LOG_SETUP(".engine.proto_rpc_adapter");

namespace search::engine {

using vespalib::DataBuffer;
using vespalib::ConstBufferRef;
using vespalib::compression::CompressionConfig;
using ProtoSearchRequest = ProtoConverter::ProtoSearchRequest;
using ProtoSearchReply = ProtoConverter::ProtoSearchReply;
using ProtoDocsumRequest = ProtoConverter::ProtoDocsumRequest;
using ProtoDocsumReply = ProtoConverter::ProtoDocsumReply;
using ProtoMonitorRequest = ProtoConverter::ProtoMonitorRequest;
using ProtoMonitorReply = ProtoConverter::ProtoMonitorReply;
using QueryStats = SearchProtocolMetrics::QueryStats;
using DocsumStats = SearchProtocolMetrics::DocsumStats;

namespace {

CompressionConfig get_compression_config() {
    using search::fs4transport::FS4PersistentPacketStreamer;
    const FS4PersistentPacketStreamer & streamer = FS4PersistentPacketStreamer::Instance;
    return CompressionConfig(streamer.getCompressionType(), streamer.getCompressionLevel(), 80, streamer.getCompressionLimit());
}

template <typename MSG>
void encode_message(const MSG &src, FRT_Values &dst) {
    using vespalib::compression::compress;
    auto output = src.SerializeAsString();
    ConstBufferRef buf(output.data(), output.size());
    DataBuffer compressed(output.data(), output.size());
    CompressionConfig::Type type = compress(get_compression_config(), buf, compressed, true);
    dst.AddInt8(type);
    dst.AddInt32(buf.size());
    dst.AddData(compressed.getData(), compressed.getDataLen());
}

void encode_search_reply(const ProtoSearchReply &src, FRT_Values &dst) {
    using vespalib::compression::compress;
    auto output = src.SerializeAsString();
    if (src.grouping_blob().empty()) {
        dst.AddInt8(CompressionConfig::Type::NONE);
        dst.AddInt32(output.size());
        dst.AddData(output.data(), output.size());
    } else {
        ConstBufferRef buf(output.data(), output.size());
        DataBuffer compressed(output.data(), output.size());
        CompressionConfig::Type type = compress(get_compression_config(), buf, compressed, true);
        dst.AddInt8(type);
        dst.AddInt32(buf.size());
        dst.AddData(compressed.getData(), compressed.getDataLen());
    }
}

template <typename MSG>
bool decode_message(const FRT_Values &src, MSG &dst) {
    using vespalib::compression::decompress;
    uint8_t encoding = src[0]._intval8;
    uint32_t uncompressed_size = src[1]._intval32;
    DataBuffer uncompressed(src[2]._data._buf, src[2]._data._len);
    ConstBufferRef blob(src[2]._data._buf, src[2]._data._len);
    decompress(CompressionConfig::toType(encoding), uncompressed_size, blob, uncompressed, true);
    assert(uncompressed_size == uncompressed.getDataLen());
    return dst.ParseFromArray(uncompressed.getData(), uncompressed.getDataLen());
}

//-----------------------------------------------------------------------------

struct SearchRequestDecoder : SearchRequest::Source::Decoder {
    FRT_RPCRequest &rpc; // valid until Return is called
    QueryStats &stats;
    RelativeTime relative_time;
    SearchRequestDecoder(FRT_RPCRequest &rpc_in, QueryStats &stats_in)
        : rpc(rpc_in), stats(stats_in), relative_time(std::make_unique<SteadyClock>()) {}
    std::unique_ptr<SearchRequest> decode() override {
        ProtoSearchRequest msg;
        stats.request_size = (*rpc.GetParams())[2]._data._len;
        if (!decode_message(*rpc.GetParams(), msg)) {
            LOG(warning, "got bad protobuf search request over rpc (unable to decode)");
            return std::unique_ptr<SearchRequest>(nullptr);
        }
        auto req = std::make_unique<SearchRequest>(std::move(relative_time));
        ProtoConverter::search_request_from_proto(msg, *req);
        return req;
    }
};

std::unique_ptr<SearchRequest::Source::Decoder> search_request_decoder(FRT_RPCRequest &rpc, QueryStats &stats) {
    return std::make_unique<SearchRequestDecoder>(rpc, stats);
}

// allocated in the stash of the request it is completing; no self-delete needed
struct SearchCompletionHandler : SearchClient {
    FRT_RPCRequest &req;
    SearchProtocolMetrics &metrics;
    QueryStats stats;
    SearchCompletionHandler(FRT_RPCRequest &req_in, SearchProtocolMetrics &metrics_in)
        : req(req_in), metrics(metrics_in), stats() {}
    void searchDone(SearchReply::UP reply) override {
        ProtoSearchReply msg;
        ProtoConverter::search_reply_to_proto(*reply, msg);
        encode_search_reply(msg, *req.GetReturn());
        stats.reply_size = (*req.GetReturn())[2]._data._len;
        if (reply->request) {
            stats.latency = vespalib::to_s(reply->request->getTimeUsed());
            metrics.update_query_metrics(stats);
        }
        req.Return();
    }
};

//-----------------------------------------------------------------------------

struct DocsumRequestDecoder : DocsumRequest::Source::Decoder {
    FRT_RPCRequest &rpc; // valid until Return is called
    DocsumStats &stats;
    RelativeTime relative_time;
    DocsumRequestDecoder(FRT_RPCRequest &rpc_in, DocsumStats &stats_in)
        : rpc(rpc_in), stats(stats_in), relative_time(std::make_unique<SteadyClock>()) {}
    std::unique_ptr<DocsumRequest> decode() override {
        ProtoDocsumRequest msg;
        stats.request_size = (*rpc.GetParams())[2]._data._len;
        if (!decode_message(*rpc.GetParams(), msg)) {
            LOG(warning, "got bad protobuf docsum request over rpc (unable to decode)");
            return std::unique_ptr<DocsumRequest>(nullptr);
        }
        stats.requested_documents = msg.global_ids_size();
        auto req = std::make_unique<DocsumRequest>(std::move(relative_time));
        ProtoConverter::docsum_request_from_proto(msg, *req);
        return req;
    }
};

std::unique_ptr<DocsumRequest::Source::Decoder> docsum_request_decoder(FRT_RPCRequest &rpc, DocsumStats &stats) {
    return std::make_unique<DocsumRequestDecoder>(rpc, stats);
}

// allocated in the stash of the request it is completing; no self-delete needed
struct GetDocsumsCompletionHandler : DocsumClient {
    FRT_RPCRequest &req;
    SearchProtocolMetrics &metrics;
    DocsumStats stats;
    GetDocsumsCompletionHandler(FRT_RPCRequest &req_in, SearchProtocolMetrics &metrics_in)
        : req(req_in), metrics(metrics_in), stats() {}
    void getDocsumsDone(DocsumReply::UP reply) override {
        ProtoDocsumReply msg;
        ProtoConverter::docsum_reply_to_proto(*reply, msg);
        encode_message(msg, *req.GetReturn());
        stats.reply_size = (*req.GetReturn())[2]._data._len;
        if (reply->hasRequest()) {
            stats.latency = vespalib::to_s(reply->request().getTimeUsed());
            metrics.update_docsum_metrics(stats);
        }
        req.Return();
    }
};

//-----------------------------------------------------------------------------

// allocated in the stash of the request it is completing; no self-delete needed
struct PingCompletionHandler : MonitorClient {
    FRT_RPCRequest &req;
    PingCompletionHandler(FRT_RPCRequest &req_in) : req(req_in) {}
    void pingDone(std::unique_ptr<MonitorReply> reply) override {
        ProtoMonitorReply msg;
        ProtoConverter::monitor_reply_to_proto(*reply, msg);
        encode_message(msg, *req.GetReturn());
        req.Return();
    }
};

//-----------------------------------------------------------------------------

void describe_bix_param_return(FRT_ReflectionBuilder &rb) {
    rb.ParamDesc("encoding", "0=raw, 6=lz4, 7=zstd");
    rb.ParamDesc("uncompressed_size", "uncompressed size of serialized request");
    rb.ParamDesc("request", "possibly compressed serialized request");
    rb.ReturnDesc("encoding",  "0=raw, 6=lz4, 7=zstd");
    rb.ReturnDesc("uncompressed_size", "uncompressed size of serialized reply");
    rb.ReturnDesc("reply", "possibly compressed serialized reply");
}

std::unique_ptr<FRT_RequireCapabilities> make_search_api_capability_filter() {
    return FRT_RequireCapabilities::of(vespalib::net::tls::Capability::content_search_api());
}

}

ProtoRpcAdapter::ProtoRpcAdapter(SearchServer &search_server,
                                 DocsumServer &docsum_server,
                                 MonitorServer &monitor_server,
                                 FRT_Supervisor &orb)
    : _search_server(search_server),
      _docsum_server(docsum_server),
      _monitor_server(monitor_server),
      _online(false),
      _metrics()
{
    FRT_ReflectionBuilder rb(&orb);
    //-------------------------------------------------------------------------
    rb.DefineMethod("vespa.searchprotocol.search", "bix", "bix",
                    FRT_METHOD(ProtoRpcAdapter::rpc_search), this);
    rb.MethodDesc("perform a search against this back-end");
    rb.RequestAccessFilter(make_search_api_capability_filter());
    describe_bix_param_return(rb);
    //-------------------------------------------------------------------------
    rb.DefineMethod("vespa.searchprotocol.getDocsums", "bix", "bix",
                    FRT_METHOD(ProtoRpcAdapter::rpc_getDocsums), this);
    rb.MethodDesc("fetch document summaries from this back-end");
    rb.RequestAccessFilter(make_search_api_capability_filter());
    describe_bix_param_return(rb);
    //-------------------------------------------------------------------------
    rb.DefineMethod("vespa.searchprotocol.ping", "bix", "bix",
                    FRT_METHOD(ProtoRpcAdapter::rpc_ping), this);
    rb.MethodDesc("ping this back-end");
    rb.RequestAccessFilter(make_search_api_capability_filter());
    describe_bix_param_return(rb);
    //-------------------------------------------------------------------------
}

void
ProtoRpcAdapter::rpc_search(FRT_RPCRequest *req)
{
    if (!is_online()) {
        return req->SetError(FRTE_RPC_METHOD_FAILED, "Server not online");
    }
    req->Detach();
    auto &client = req->getStash().create<SearchCompletionHandler>(*req, _metrics);
    auto reply = _search_server.search(search_request_decoder(*req, client.stats), client);
    if (reply) {
        client.searchDone(std::move(reply));
    }
}

void
ProtoRpcAdapter::rpc_getDocsums(FRT_RPCRequest *req)
{
    if (!is_online()) {
        return req->SetError(FRTE_RPC_METHOD_FAILED, "Server not online");
    }
    req->Detach();
    auto &client = req->getStash().create<GetDocsumsCompletionHandler>(*req, _metrics);
    auto reply = _docsum_server.getDocsums(docsum_request_decoder(*req, client.stats), client);
    if (reply) {
        client.getDocsumsDone(std::move(reply));
    }
}

void
ProtoRpcAdapter::rpc_ping(FRT_RPCRequest *rpc)
{
    if (!is_online()) {
        return rpc->SetError(FRTE_RPC_METHOD_FAILED, "Server not online");
    }
    rpc->Detach();
    ProtoMonitorRequest msg;
    if (decode_message(*rpc->GetParams(), msg)) {
        auto req = std::make_unique<MonitorRequest>();
        ProtoConverter::monitor_request_from_proto(msg, *req);
        auto &client = rpc->getStash().create<PingCompletionHandler>(*rpc);
        auto reply = _monitor_server.ping(std::move(req), client);
        if (reply) {
            client.pingDone(std::move(reply));
        }
    } else {
        LOG(warning, "got bad protobuf monitor request over rpc (unable to decode)");
        rpc->SetError(FRTE_RPC_METHOD_FAILED, "malformed monitor request");
        rpc->Return();
    }
}

//-----------------------------------------------------------------------------

void
ProtoRpcAdapter::encode_search_request(const ProtoSearchRequest &src, FRT_RPCRequest &dst)
{
    dst.SetMethodName("vespa.searchprotocol.search");
    encode_message(src, *dst.GetParams());
}

bool
ProtoRpcAdapter::decode_search_reply(FRT_RPCRequest &src, ProtoSearchReply &dst)
{
    return (src.CheckReturnTypes("bix") && decode_message(*src.GetReturn(), dst));
}

void
ProtoRpcAdapter::encode_docsum_request(const ProtoDocsumRequest &src, FRT_RPCRequest &dst)
{
    dst.SetMethodName("vespa.searchprotocol.getDocsums");
    encode_message(src, *dst.GetParams());
}

bool
ProtoRpcAdapter::decode_docsum_reply(FRT_RPCRequest &src, ProtoDocsumReply &dst)
{
    return (src.CheckReturnTypes("bix") && decode_message(*src.GetReturn(), dst));
}

void
ProtoRpcAdapter::encode_monitor_request(const ProtoMonitorRequest &src, FRT_RPCRequest &dst)
{
    dst.SetMethodName("vespa.searchprotocol.ping");
    encode_message(src, *dst.GetParams());
}

bool
ProtoRpcAdapter::decode_monitor_reply(FRT_RPCRequest &src, ProtoMonitorReply &dst)
{
    return (src.CheckReturnTypes("bix") && decode_message(*src.GetReturn(), dst));
}

}
