// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/messagebus/routing/routingnode.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/error.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/tracelevel.h>
#include <vespa/vespalib/util/stringfmt.h>
#include "rpcnetwork.h"
#include "rpcsendv1.h"
#include "rpctarget.h"

namespace {

/**
 * Implements a helper class to hold the necessary context to create a reply from
 * an rpc return value. This object is held as the context of an FRT_RPCRequest.
 */
class SendContext {
private:
    mbus::RoutingNode &_recipient;
    mbus::Trace        _trace;
    double             _timeout;

public:
    typedef std::unique_ptr<SendContext> UP;
    SendContext(const SendContext &) = delete;
    SendContext & operator = (const SendContext &) = delete;
    SendContext(mbus::RoutingNode &recipient, uint64_t timeRemaining)
        : _recipient(recipient),
          _trace(recipient.getTrace().getLevel()),
          _timeout(timeRemaining * 0.001) { }
    mbus::RoutingNode &getRecipient() { return _recipient; }
    mbus::Trace &getTrace() { return _trace; }
    double getTimeout() { return _timeout; }
};

/**
 * Implements a helper class to hold the necessary context to send a reply as an
 * rpc return value. This object is held in the callstack of the reply.
 */
class ReplyContext {
private:
    FRT_RPCRequest   &_request;
    vespalib::Version _version;

public:
    typedef std::unique_ptr<ReplyContext> UP;
    ReplyContext(const ReplyContext &) = delete;
    ReplyContext & operator = (const ReplyContext &) = delete;

    ReplyContext(FRT_RPCRequest &request, const vespalib::Version &version)
        : _request(request), _version(version) { }
    FRT_RPCRequest &getRequest() { return _request; }
    const vespalib::Version &getVersion() { return _version; }
};

}

namespace mbus {

const char *RPCSendV1::METHOD_NAME   = "mbus.send1";
const char *RPCSendV1::METHOD_PARAMS = "sssbilsxi";
const char *RPCSendV1::METHOD_RETURN = "sdISSsxs";

RPCSendV1::RPCSendV1() :
    _net(NULL),
    _clientIdent("client"),
    _serverIdent("server")
{ }

RPCSendV1::~RPCSendV1() {}

void
RPCSendV1::attach(RPCNetwork &net)
{
    _net = &net;
    const string &prefix = _net->getIdentity().getServicePrefix();
    if (!prefix.empty()) {
        _clientIdent = vespalib::make_vespa_string("'%s'", prefix.c_str());
        _serverIdent = _clientIdent;
    }

    FRT_ReflectionBuilder builder(&_net->getSupervisor());
    builder.DefineMethod(METHOD_NAME, METHOD_PARAMS, METHOD_RETURN, true,
                         FRT_METHOD(RPCSendV1::invoke), this);
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

namespace {

class FillByCopy : public PayLoadFiller
{
public:
    FillByCopy(BlobRef payload) : _payload(payload) { }
    void fill(FRT_Values & v) const override {
        v.AddData(_payload.data(), _payload.size());
    }
private:
    BlobRef _payload;
};

class FillByHandover : public PayLoadFiller
{
public:
    FillByHandover(Blob payload) : _payload(std::move(payload)) { }
    void fill(FRT_Values & v) const override {
        v.AddData(std::move(_payload.payload()), _payload.size());
    }
private:
    mutable Blob _payload;
};

}

void
RPCSendV1::send(RoutingNode &recipient, const vespalib::Version &version,
                BlobRef payload, uint64_t timeRemaining)
{
    send(recipient, version, FillByCopy(payload), timeRemaining);
}

void
RPCSendV1::sendByHandover(RoutingNode &recipient, const vespalib::Version &version,
                Blob payload, uint64_t timeRemaining)
{
    send(recipient, version, FillByHandover(std::move(payload)), timeRemaining);
}

void
RPCSendV1::send(RoutingNode &recipient, const vespalib::Version &version,
                const PayLoadFiller & payload, uint64_t timeRemaining)
{
    SendContext::UP ctx(new SendContext(recipient, timeRemaining));
    RPCServiceAddress &address = static_cast<RPCServiceAddress&>(recipient.getServiceAddress());
    const Message &msg = recipient.getMessage();
    Route route = recipient.getRoute();
    Hop hop = route.removeHop(0);

    FRT_RPCRequest *req = _net->allocRequest();
    FRT_Values &args = *req->GetParams();
    req->SetMethodName(METHOD_NAME);
    args.AddString(version.toString().c_str());
    args.AddString(route.toString().c_str());
    args.AddString(address.getSessionName().c_str());
    args.AddInt8(msg.getRetryEnabled() ? 1 : 0);
    args.AddInt32(msg.getRetry());
    args.AddInt64(timeRemaining);
    args.AddString(msg.getProtocol().c_str());
    payload.fill(args);
    args.AddInt32(recipient.getTrace().getLevel());

    if (ctx->getTrace().shouldTrace(TraceLevel::SEND_RECEIVE)) {
        ctx->getTrace().trace(TraceLevel::SEND_RECEIVE,
                              vespalib::make_vespa_string(
                                  "Sending message (version %s) from %s to '%s' with %.2f seconds timeout.",
                                  version.toString().c_str(), _clientIdent.c_str(),
                                  address.getServiceName().c_str(), ctx->getTimeout()));
    }

    if (hop.getIgnoreResult()) {
        address.getTarget().getFRTTarget().InvokeVoid(req);
        if (ctx->getTrace().shouldTrace(TraceLevel::SEND_RECEIVE)) {
            ctx->getTrace().trace(TraceLevel::SEND_RECEIVE,
                                  vespalib::make_vespa_string("Not waiting for a reply from '%s'.",
                                                        address.getServiceName().c_str()));
        }
        Reply::UP reply(new EmptyReply());
        reply->getTrace().swap(ctx->getTrace());
        _net->getOwner().deliverReply(std::move(reply), recipient);
    } else {
        SendContext *ptr = ctx.release();
        req->SetContext(FNET_Context(ptr));
        address.getTarget().getFRTTarget().InvokeAsync(req, ptr->getTimeout(), this);
    }
}

void
RPCSendV1::RequestDone(FRT_RPCRequest *req)
{
    SendContext::UP ctx(static_cast<SendContext*>(req->GetContext()._value.VOIDP));
    const string &serviceName = static_cast<RPCServiceAddress&>(
            ctx->getRecipient().getServiceAddress()).getServiceName();
    Reply::UP reply;
    Error error;
    if (!req->CheckReturnTypes(METHOD_RETURN)) {
        reply.reset(new EmptyReply());
        switch (req->GetErrorCode()) {
        case FRTE_RPC_TIMEOUT:
            error = Error(ErrorCode::TIMEOUT,
                          vespalib::make_vespa_string("A timeout occured while waiting for '%s' (%g seconds expired); %s",
                                                serviceName.c_str(), ctx->getTimeout(), req->GetErrorMessage()));
            break;
        case FRTE_RPC_CONNECTION:
            error = Error(ErrorCode::CONNECTION_ERROR,
                          vespalib::make_vespa_string("A connection error occured for '%s'; %s",
                                                serviceName.c_str(), req->GetErrorMessage()));
            break;
        default:
            error = Error(ErrorCode::NETWORK_ERROR,
                          vespalib::make_vespa_string("A network error occured for '%s'; %s",
                                                serviceName.c_str(), req->GetErrorMessage()));
        }
    } else {
        FRT_Values &ret = *req->GetReturn();

        vespalib::Version version          = vespalib::Version(ret[0]._string._str);
        double            retryDelay       = ret[1]._double;
        uint32_t         *errorCodes       = ret[2]._int32_array._pt;
        uint32_t          errorCodesLen    = ret[2]._int32_array._len;
        FRT_StringValue  *errorMessages    = ret[3]._string_array._pt;
        uint32_t          errorMessagesLen = ret[3]._string_array._len;
        FRT_StringValue  *errorServices    = ret[4]._string_array._pt;
        uint32_t          errorServicesLen = ret[4]._string_array._len;
        const char       *protocolName     = ret[5]._string._str;
        const char       *payload          = ret[6]._data._buf;
        uint32_t          payloadLen       = ret[6]._data._len;
        const char       *trace            = ret[7]._string._str;

        if (payloadLen > 0) {
            IProtocol::SP protocol = _net->getOwner().getProtocol(protocolName);
            if (protocol.get() != NULL) {
                Routable::UP routable = protocol->decode(version,
                                                         BlobRef(payload, payloadLen));
                if (routable.get() != NULL) {
                    if (routable->isReply()) {
                        reply.reset(static_cast<Reply*>(routable.release()));
                    } else {
                        error = Error(ErrorCode::DECODE_ERROR,
                                      "Payload decoded to a message when expecting a reply.");
                    }
                } else {
                    error = Error(ErrorCode::DECODE_ERROR,
                                  vespalib::make_vespa_string("Protocol '%s' failed to decode routable.",
                                                        protocolName));
                }

            } else {
                error = Error(ErrorCode::UNKNOWN_PROTOCOL,
                              vespalib::make_vespa_string("Protocol '%s' is not known by %s.",
                                                    protocolName, _serverIdent.c_str()));
            }
        }
        if (reply.get() == NULL) {
            reply.reset(new EmptyReply());
        }
        reply->setRetryDelay(retryDelay);
        for (uint32_t i = 0; i < errorCodesLen && i < errorMessagesLen && i < errorServicesLen; ++i) {
            reply->addError(Error(errorCodes[i],
                                  errorMessages[i]._str,
                                  errorServices[i]._len > 0 ? errorServices[i]._str : serviceName.c_str()));
        }
        ctx->getTrace().getRoot().addChild(TraceNode::decode(trace));
    }
    if (ctx->getTrace().shouldTrace(TraceLevel::SEND_RECEIVE)) {
        ctx->getTrace().trace(TraceLevel::SEND_RECEIVE,
                          vespalib::make_vespa_string("Reply (type %d) received at %s.",
                                                reply->getType(), _clientIdent.c_str()));
    }
    reply->getTrace().swap(ctx->getTrace());
    if (error.getCode() != ErrorCode::NONE) {
        reply->addError(error);
    }
    _net->getOwner().deliverReply(std::move(reply), ctx->getRecipient());
    req->SubRef();
}

void
RPCSendV1::invoke(FRT_RPCRequest *req)
{
    req->Detach();

    FRT_Values &args = *req->GetParams();
    vespalib::Version  version       = vespalib::Version(args[0]._string._str);
    const char        *route         = args[1]._string._str;
    const char        *session       = args[2]._string._str;
    bool               retryEnabled  = args[3]._intval8 != 0;
    uint32_t           retry         = args[4]._intval32;
    uint64_t           timeRemaining = args[5]._intval64;
    const char        *protocolName  = args[6]._string._str;
    const char        *payload       = args[7]._data._buf;
    uint32_t           payloadLen    = args[7]._data._len;
    uint32_t           traceLevel    = args[8]._intval32;

    IProtocol::SP protocol = _net->getOwner().getProtocol(protocolName);
    if (protocol.get() == NULL) {
        replyError(req, version, traceLevel,
                   Error(ErrorCode::UNKNOWN_PROTOCOL,
                         vespalib::make_vespa_string("Protocol '%s' is not known by %s.",
                                               protocolName, _serverIdent.c_str())));
        return;
    }
    Routable::UP routable = protocol->decode(version, BlobRef(payload, payloadLen));
    req->DiscardBlobs();
    if (routable.get() == NULL) {
        replyError(req, version, traceLevel,
                   Error(ErrorCode::DECODE_ERROR,
                         vespalib::make_vespa_string("Protocol '%s' failed to decode routable.",
                                               protocolName)));
        return;
    }
    if (routable->isReply()) {
        replyError(req, version, traceLevel,
                   Error(ErrorCode::DECODE_ERROR,
                         "Payload decoded to a reply when expecting a mesage."));
        return;
    }
    Message::UP msg(static_cast<Message*>(routable.release()));
    if (strlen(route) > 0) {
        msg->setRoute(Route::parse(route));
    }
    msg->setContext(Context(new ReplyContext(*req, version)));
    msg->pushHandler(*this, *this);
    msg->setRetryEnabled(retryEnabled);
    msg->setRetry(retry);
    msg->setTimeReceivedNow();
    msg->setTimeRemaining(timeRemaining);
    msg->getTrace().setLevel(traceLevel);
    if (msg->getTrace().shouldTrace(TraceLevel::SEND_RECEIVE)) {
        msg->getTrace().trace(TraceLevel::SEND_RECEIVE,
                              vespalib::make_vespa_string("Message (type %d) received at %s for session '%s'.",
                                                    msg->getType(), _serverIdent.c_str(), session));
    }
    _net->getOwner().deliverMessage(std::move(msg), session);
}

void
RPCSendV1::handleReply(Reply::UP reply)
{
    ReplyContext::UP ctx(static_cast<ReplyContext*>(reply->getContext().value.PTR));
    FRT_RPCRequest &req = ctx->getRequest();
    string version = ctx->getVersion().toString();
    if (reply->getTrace().shouldTrace(TraceLevel::SEND_RECEIVE)) {
        reply->getTrace().trace(TraceLevel::SEND_RECEIVE,
                                vespalib::make_vespa_string("Sending reply (version %s) from %s.",
                                                      version.c_str(), _serverIdent.c_str()));
    }
    Blob payload(0);
    if (reply->getType() != 0) {
        payload = _net->getOwner().getProtocol(reply->getProtocol())->encode(ctx->getVersion(), *reply);
        if (payload.size() == 0) {
            reply->addError(Error(ErrorCode::ENCODE_ERROR,
                                  "An error occured while encoding the reply, see log."));
        }
    }
    FRT_Values &ret = *req.GetReturn();
    ret.AddString(version.c_str());
    ret.AddDouble(reply->getRetryDelay());

    uint32_t         errorCount    = reply->getNumErrors();
    uint32_t        *errorCodes    = ret.AddInt32Array(errorCount);
    FRT_StringValue *errorMessages = ret.AddStringArray(errorCount);
    FRT_StringValue *errorServices = ret.AddStringArray(errorCount);
    for (uint32_t i = 0; i < errorCount; ++i) {
        errorCodes[i] = reply->getError(i).getCode();
        ret.SetString(errorMessages + i,
                      reply->getError(i).getMessage().c_str());
        ret.SetString(errorServices + i,
                      reply->getError(i).getService().c_str());
    }

    ret.AddString(reply->getProtocol().c_str());
    ret.AddData(std::move(payload.payload()), payload.size());
    if (reply->getTrace().getLevel() > 0) {
        ret.AddString(reply->getTrace().getRoot().encode().c_str());
    } else {
        ret.AddString("");
    }
    req.Return();
}

void
RPCSendV1::handleDiscard(Context ctx)
{
    ReplyContext::UP tmp(static_cast<ReplyContext*>(ctx.value.PTR));
    FRT_RPCRequest &req = tmp->getRequest();
    FNET_Channel *chn = req.GetContext()._value.CHANNEL;
    req.SubRef();
    chn->Free();
}

void
RPCSendV1::replyError(FRT_RPCRequest *req, const vespalib::Version &version,
                      uint32_t traceLevel, const Error &err)
{
    Reply::UP reply(new EmptyReply());
    reply->setContext(Context(new ReplyContext(*req, version)));
    reply->getTrace().setLevel(traceLevel);
    reply->addError(err);
    handleReply(std::move(reply));
}

} // namespace mbus
