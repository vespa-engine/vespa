// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcsendv1.h"
#include "rpcnetwork.h"
#include "rpcserviceaddress.h"
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/error.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fnet/frt/reflection.h>

using vespalib::make_string;

namespace mbus {

namespace {

const char *METHOD_NAME   = "mbus.send1";
const char *METHOD_PARAMS = "sssbilsxi";
const char *METHOD_RETURN = "sdISSsxs";

}

bool RPCSendV1::isCompatible(vespalib::stringref method, vespalib::stringref request, vespalib::stringref respons)
{
    return  (method == METHOD_NAME) &&
            (request == METHOD_PARAMS) &&
            (respons == METHOD_RETURN);
}

const char *
RPCSendV1::getReturnSpec() const {
    return METHOD_RETURN;
}

void
RPCSendV1::build(FRT_ReflectionBuilder & builder)
{
    builder.DefineMethod(METHOD_NAME, METHOD_PARAMS, METHOD_RETURN, FRT_METHOD(RPCSendV1::invoke), this);
    builder.MethodDesc("Send a message bus request and get a reply back.");
    builder.ParamDesc("version", "The version of the message.");
    builder.ParamDesc("route", "Names of additional hops to visit.");
    builder.ParamDesc("session", "The local session that should receive this message.");
    builder.ParamDesc("retryEnabled", "Whether or not this message can be resent.");
    builder.ParamDesc("retry", "The number of times the sending of this message has been retried.");
    builder.ParamDesc("timeRemaining", "The number of milliseconds until timeout.");
    builder.ParamDesc("protocol", "The name of the protocol that knows how to decode this message.");
    builder.ParamDesc("payload", "The protocol specific message payload.");
    builder.ParamDesc("level", "The trace level of the message.");
    builder.ReturnDesc("version", "The lowest version the message was serialized as.");
    builder.ReturnDesc("retry", "The retry request of the reply.");
    builder.ReturnDesc("errorCodes", "The reply error codes.");
    builder.ReturnDesc("errorMessages", "The reply error messages.");
    builder.ReturnDesc("errorServices", "The reply error service names.");
    builder.ReturnDesc("protocol", "The name of the protocol that knows how to decode this reply.");
    builder.ReturnDesc("payload", "The protocol specific reply payload.");
    builder.ReturnDesc("trace", "A string representation of the trace.");
}

void
RPCSendV1::encodeRequest(FRT_RPCRequest &req, const vespalib::Version &version, const Route & route,
                         const RPCServiceAddress & address, const Message & msg, uint32_t traceLevel,
                         const PayLoadFiller &filler, duration timeRemaining) const
{

    FRT_Values &args = *req.GetParams();
    req.SetMethodName(METHOD_NAME);
    args.AddString(version.toString().c_str());
    args.AddString(route.toString().c_str());
    args.AddString(address.getSessionName().c_str());
    args.AddInt8(msg.getRetryEnabled() ? 1 : 0);
    args.AddInt32(msg.getRetry());
    args.AddInt64(vespalib::count_ms(timeRemaining));
    args.AddString(msg.getProtocol().c_str());
    filler.fill(args);
    args.AddInt32(traceLevel);
}

namespace {

class ParamsV1 : public RPCSend::Params
{
public:
    ParamsV1(const FRT_Values &args) : _args(args) { }

    uint32_t getTraceLevel() const override { return _args[8]._intval32; }
    bool useRetry() const override { return _args[3]._intval8 != 0; }
    uint32_t getRetries() const override { return _args[4]._intval32; }
    duration getRemainingTime() const override { return std::chrono::milliseconds(_args[5]._intval64); }

    vespalib::Version getVersion() const override {
        return vespalib::Version(vespalib::stringref(_args[0]._string._str, _args[0]._string._len));
    }
    vespalib::stringref getRoute() const override {
        return vespalib::stringref(_args[1]._string._str, _args[1]._string._len);
    }
    vespalib::stringref getSession() const override {
        return vespalib::stringref(_args[2]._string._str, _args[2]._string._len);
    }
    vespalib::stringref getProtocol() const override {
        return vespalib::stringref(_args[6]._string._str, _args[6]._string._len);
    }
    BlobRef getPayload() const override {
        return BlobRef(_args[7]._data._buf, _args[7]._data._len);
    }
private:
    const FRT_Values & _args;
};

}

std::unique_ptr<RPCSend::Params>
RPCSendV1::toParams(const FRT_Values &args) const
{
    return std::make_unique<ParamsV1>(args);
}


std::unique_ptr<Reply>
RPCSendV1::createReply(const FRT_Values & ret, const string & serviceName, Error & error, vespalib::Trace & trace) const
{
    vespalib::Version version          = vespalib::Version(ret[0]._string._str);
    double            retryDelay       = ret[1]._double;
    uint32_t         *errorCodes       = ret[2]._int32_array._pt;
    uint32_t          errorCodesLen    = ret[2]._int32_array._len;
    FRT_StringValue  *errorMessages    = ret[3]._string_array._pt;
    uint32_t          errorMessagesLen = ret[3]._string_array._len;
    FRT_StringValue  *errorServices    = ret[4]._string_array._pt;
    uint32_t          errorServicesLen = ret[4]._string_array._len;
    const char       *protocolName     = ret[5]._string._str;
    BlobRef payload(ret[6]._data._buf, ret[6]._data._len);
    const char       *traceStr         = ret[7]._string._str;

    Reply::UP reply;
    if (payload.size() > 0) {
        reply = decode(protocolName, version, payload, error);
    }
    if ( ! reply ) {
        reply = std::make_unique<EmptyReply>();
    }
    reply->setRetryDelay(retryDelay);
    for (uint32_t i = 0; i < errorCodesLen && i < errorMessagesLen && i < errorServicesLen; ++i) {
        reply->addError(Error(errorCodes[i], errorMessages[i]._str,
                              errorServices[i]._len > 0 ? errorServices[i]._str : serviceName.c_str()));
    }
    trace.addChild(TraceNode::decode(traceStr));
    return reply;
}

void
RPCSendV1::createResponse(FRT_Values & ret, const string & version, Reply & reply, Blob payload) const {
    ret.AddString(version.c_str());
    ret.AddDouble(reply.getRetryDelay());

    uint32_t         errorCount    = reply.getNumErrors();
    uint32_t        *errorCodes    = ret.AddInt32Array(errorCount);
    FRT_StringValue *errorMessages = ret.AddStringArray(errorCount);
    FRT_StringValue *errorServices = ret.AddStringArray(errorCount);
    for (uint32_t i = 0; i < errorCount; ++i) {
        errorCodes[i] = reply.getError(i).getCode();
        ret.SetString(errorMessages + i, reply.getError(i).getMessage().c_str());
        ret.SetString(errorServices + i, reply.getError(i).getService().c_str());
    }

    ret.AddString(reply.getProtocol().c_str());
    ret.AddData(std::move(payload.payload()), payload.size());
    if (reply.getTrace().getLevel() > 0) {
        ret.AddString(reply.getTrace().encode().c_str());
    } else {
        ret.AddString("");
    }
}

} // namespace mbus
