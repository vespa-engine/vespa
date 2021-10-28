// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchrequest.h"
#include "searchreply.h"
#include "docsumrequest.h"
#include "docsumreply.h"
#include "monitorrequest.h"
#include "monitorreply.h"
#include "search_protocol_proto.h"

namespace search::engine {

struct ProtoConverter {
    using ProtoSearchRequest = ::searchlib::searchprotocol::protobuf::SearchRequest;
    using ProtoSearchReply = ::searchlib::searchprotocol::protobuf::SearchReply;

    using ProtoDocsumRequest = ::searchlib::searchprotocol::protobuf::DocsumRequest;
    using ProtoDocsumReply = ::searchlib::searchprotocol::protobuf::DocsumReply;

    using ProtoMonitorRequest = ::searchlib::searchprotocol::protobuf::MonitorRequest;
    using ProtoMonitorReply = ::searchlib::searchprotocol::protobuf::MonitorReply;

    static void search_request_from_proto(const ProtoSearchRequest &proto, SearchRequest &request);
    static void search_reply_to_proto(const SearchReply &reply, ProtoSearchReply &proto);

    static void docsum_request_from_proto(const ProtoDocsumRequest &proto, DocsumRequest &request);
    static void docsum_reply_to_proto(const DocsumReply &reply, ProtoDocsumReply &proto);

    static void monitor_request_from_proto(const ProtoMonitorRequest &proto, MonitorRequest &request);
    static void monitor_reply_to_proto(const MonitorReply &reply, ProtoMonitorReply &proto);
};

}
