// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fnet/frt/invokable.h>
#include "proto_converter.h"
#include <atomic>

#include "search_protocol_metrics.h"

class FRT_Supervisor;

namespace search::engine {

class SearchServer;
class DocsumServer;
class MonitorServer;

/**
 * Class adapting the internal search engine interfaces (SearchServer,
 * DocsumServer, MonitorServer) to the external searchprotocol api
 * (possibly compressed protobuf over frt rpc).
 **/
class ProtoRpcAdapter : FRT_Invokable
{
public:
    using ProtoSearchRequest = ProtoConverter::ProtoSearchRequest;
    using ProtoSearchReply = ProtoConverter::ProtoSearchReply;
    using ProtoDocsumRequest = ProtoConverter::ProtoDocsumRequest;
    using ProtoDocsumReply = ProtoConverter::ProtoDocsumReply;
    using ProtoMonitorRequest = ProtoConverter::ProtoMonitorRequest;
    using ProtoMonitorReply = ProtoConverter::ProtoMonitorReply;
private:
    SearchServer   &_search_server;
    DocsumServer   &_docsum_server;
    MonitorServer  &_monitor_server;
    std::atomic<bool> _online;
    SearchProtocolMetrics _metrics;
public:
    ProtoRpcAdapter(SearchServer &search_server,
                    DocsumServer &docsum_server,
                    MonitorServer &monitor_server,
                    FRT_Supervisor &orb);

    SearchProtocolMetrics &metrics() { return _metrics; }

    void set_online() { _online.store(true, std::memory_order_release); }
    bool is_online() const { return _online.load(std::memory_order_acquire); }

    void rpc_search(FRT_RPCRequest *req);
    void rpc_getDocsums(FRT_RPCRequest *req);
    void rpc_ping(FRT_RPCRequest *req);

    // convenience functions used for testing
    static void encode_search_request(const ProtoSearchRequest &src, FRT_RPCRequest &dst);
    static bool decode_search_reply(FRT_RPCRequest &src, ProtoSearchReply &dst);

    static void encode_docsum_request(const ProtoDocsumRequest &src, FRT_RPCRequest &dst);
    static bool decode_docsum_reply(FRT_RPCRequest &src, ProtoDocsumReply &dst);

    static void encode_monitor_request(const ProtoMonitorRequest &src, FRT_RPCRequest &dst);
    static bool decode_monitor_reply(FRT_RPCRequest &src, ProtoMonitorReply &dst);

};

}
