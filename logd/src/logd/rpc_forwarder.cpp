// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "exceptions.h"
#include "proto_converter.h"
#include "rpc_forwarder.h"
#include <vespa/log/exceptions.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".logd.rpc_forwarder");

using ns_log::BadLogLineException;
using ns_log::LogMessage;
using vespalib::make_string;

namespace logdemon {

RpcForwarder::RpcForwarder(const vespalib::string &hostname, int rpc_port,
                           double rpc_timeout_secs, size_t max_messages_per_request)
    : _connection_spec(make_string("tcp/%s:%d", hostname.c_str(), rpc_port)),
      _rpc_timeout_secs(rpc_timeout_secs),
      _max_messages_per_request(max_messages_per_request),
      _supervisor(),
      _target(),
      _messages(),
      _bad_lines(0)
{
    _supervisor.Start();
    _target = _supervisor.GetTarget(_connection_spec.c_str());
}

RpcForwarder::~RpcForwarder()
{
    _target->SubRef();
    _supervisor.ShutDown(true);
}

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
    _messages.push_back(std::move(message));
    if (_messages.size() == _max_messages_per_request) {
        flush();
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
    auto request = new FRT_RPCRequest();
    encode_log_request(proto_request, *request);
    _target->InvokeSync(request, _rpc_timeout_secs);
    if (!request->CheckReturnTypes("bix")) {
        auto error_msg = make_string("Error in rpc reply from '%s': '%s'",
                                     _connection_spec.c_str(), request->GetErrorMessage());
        request->SubRef();
        throw ConnectionException(error_msg);
    }
    ProtoConverter::ProtoLogResponse proto_response;
    if (!decode_log_response(*request, proto_response)) {
        auto error_msg = make_string("Error during decoding of protobuf response from '%s'", _connection_spec.c_str());
        request->SubRef();
        throw DecodeException(error_msg);
    }
    request->SubRef();
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
