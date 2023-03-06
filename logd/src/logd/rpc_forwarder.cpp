// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "exceptions.h"
#include "metrics.h"
#include "proto_converter.h"
#include "rpc_forwarder.h"
#include <vespa/log/exceptions.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>



#include <vespa/log/log.h>
LOG_SETUP(".logd.rpc_forwarder");

using ns_log::BadLogLineException;
using ns_log::LogMessage;
using vespalib::make_string;

namespace logdemon {

namespace {

class GuardedRequest {
private:
    FRT_RPCRequest* _request;
public:
    GuardedRequest()
        : _request(new FRT_RPCRequest())
    {}
    ~GuardedRequest() {
        _request->internal_subref();
    }
    FRT_RPCRequest& operator*() const { return *_request; }
    FRT_RPCRequest* get() const { return _request; }
    FRT_RPCRequest* operator->() const { return get(); }
};

}

void
RpcForwarder::ping_logserver()
{
    GuardedRequest request;
    request->SetMethodName("frt.rpc.ping");
    _target->InvokeSync(request.get(), _rpc_timeout_secs);
    if (!request->CheckReturnTypes("")) {
        auto error_msg = make_string("Error in rpc ping to logserver ('%s'): '%s'",
                                     _connection_spec.c_str(), request->GetErrorMessage());
        LOG(debug, "%s", error_msg.c_str());
        throw ConnectionException(error_msg);
    }
}

RpcForwarder::RpcForwarder(Metrics& metrics, const ForwardMap& forward_filter, FRT_Supervisor& supervisor,
                           const vespalib::string &hostname, int rpc_port,
                           double rpc_timeout_secs, size_t max_messages_per_request)
    : _metrics(metrics),
      _connection_spec(make_string("tcp/%s:%d", hostname.c_str(), rpc_port)),
      _rpc_timeout_secs(rpc_timeout_secs),
      _max_messages_per_request(max_messages_per_request),
      _target(supervisor.GetTarget(_connection_spec.c_str())),
      _messages(),
      _bad_lines(0),
      _forward_filter(forward_filter)
{
    ping_logserver();
}

RpcForwarder::~RpcForwarder() = default;

namespace {

void
encode_log_request(const ProtoConverter::ProtoLogRequest& src, FRT_RPCRequest& dst)
{
    dst.SetMethodName("vespa.logserver.archiveLogMessages");
    auto buf = src.SerializeAsString();
    auto& params = *dst.GetParams();
    params.AddInt8(0); // '0' indicates no compression
    params.AddInt32(buf.size());
    params.AddData(buf.data(), buf.size());
}

bool
decode_log_response(FRT_RPCRequest& src, ProtoConverter::ProtoLogResponse& dst)
{
    auto& values = *src.GetReturn();
    uint8_t encoding = values[0]._intval8;
    assert(encoding == 0); // Not using compression
    uint32_t uncompressed_size = values[1]._intval32;
    (void) uncompressed_size;
    return dst.ParseFromArray(values[2]._data._buf, values[2]._data._len);
}

bool
should_forward_log_message(const LogMessage& message, const ForwardMap& filter)
{
    auto found = filter.find(message.level());
    if (found != filter.end()) {
        return found->second;
    }
    return false;
}

}

void
RpcForwarder::forwardLine(std::string_view line)
{
    LogMessage message;
    try {
        message.parse_log_line(line);
    } catch (BadLogLineException &e) {
        LOG(spam, "Skipping bad logline: %s", e.what());
        ++_bad_lines;
        return;
    }
    _metrics.countLine(ns_log::Logger::logLevelNames[message.level()], message.service());
    if (should_forward_log_message(message, _forward_filter)) {
        _messages.push_back(std::move(message));
        if (_messages.size() == _max_messages_per_request) {
            flush();
        }
    }
}

void
RpcForwarder::flush()
{
    if (_messages.empty()) {
        return;
    }
    ProtoConverter::ProtoLogRequest proto_request;
    ProtoConverter::log_messages_to_proto(_messages, proto_request);
    GuardedRequest request;
    encode_log_request(proto_request, *request);
    _target->InvokeSync(request.get(), _rpc_timeout_secs);
    if (!request->CheckReturnTypes("bix")) {
        auto error_msg = make_string("Error in rpc reply from logserver ('%s'): '%s'",
                                     _connection_spec.c_str(), request->GetErrorMessage());
        LOG(debug, "%s", error_msg.c_str());
        throw ConnectionException(error_msg);
    }
    ProtoConverter::ProtoLogResponse proto_response;
    if (!decode_log_response(*request, proto_response)) {
        auto error_msg = make_string("Error during decoding of protobuf response from logserver ('%s')", _connection_spec.c_str());
        LOG(warning, "%s", error_msg.c_str());
        throw DecodeException(error_msg);
    }
    _messages.clear();
}

int
RpcForwarder::badLines() const
{
    return _bad_lines;
}

void
RpcForwarder::resetBadLines()
{
    _bad_lines = 0;
}

}
