// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcsend.h"
#include "rpcsend_private.h"
#include "rpcserviceaddress.h"
#include <vespa/messagebus/network/rpcnetwork.h>
#include <vespa/messagebus/tracelevel.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/fnet/channel.h>
#include <vespa/fnet/frt/reflection.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/lambdatask.h>

#include <vespa/vespalib/data/slime/cursor.h>

using vespalib::make_string;
using vespalib::makeLambdaTask;

namespace mbus {

using network::internal::ReplyContext;
using network::internal::SendContext;

namespace {

class FillByCopy final : public PayLoadFiller
{
public:
    FillByCopy(BlobRef payload) : _payload(payload) { }
    void fill(FRT_Values & v) const override {
        v.AddData(_payload.data(), _payload.size());
    }
    void fill(const vespalib::Memory & name, vespalib::slime::Cursor & v) const override {
        v.setData(name, vespalib::Memory(_payload.data(), _payload.size()));
    }
private:
    BlobRef _payload;
};

class FillByHandover final : public PayLoadFiller
{
public:
    FillByHandover(Blob payload) : _payload(std::move(payload)) { }
    void fill(FRT_Values & v) const override {
        size_t sz = _payload.size();
        v.AddData(std::move(_payload.payload()), sz);
    }
    void fill(const vespalib::Memory & name, vespalib::slime::Cursor & v) const override {
        v.setData(name, vespalib::Memory(_payload.data(), _payload.size()));
    }
private:
    mutable Blob _payload;
};

}

RPCSend::RPCSend() :
    _net(nullptr),
    _clientIdent("client"),
    _serverIdent("server")
{ }

RPCSend::~RPCSend() = default;

void
RPCSend::attach(RPCNetwork &net)
{
    _net = &net;
    const string &prefix = _net->getIdentity().getServicePrefix();
    if (!prefix.empty()) {
        _clientIdent = make_string("'%s'", prefix.c_str());
        _serverIdent = _clientIdent;
    }

    FRT_ReflectionBuilder builder(&_net->getSupervisor());
    build(builder);
}

void
RPCSend::replyError(FRT_RPCRequest *req, const vespalib::Version &version, uint32_t traceLevel, const Error &err)
{
    Reply::UP reply(new EmptyReply());
    reply->setContext(Context(new ReplyContext(*req, version)));
    reply->getTrace().setLevel(traceLevel);
    reply->addError(err);
    handleReply(std::move(reply));
}

void
RPCSend::handleDiscard(Context ctx)
{
    ReplyContext::UP tmp(static_cast<ReplyContext*>(ctx.value.PTR));
    FRT_RPCRequest &req = tmp->getRequest();
    FNET_Channel *chn = req.GetContext()._value.CHANNEL;
    req.SubRef();
    chn->Free();
}

void
RPCSend::sendByHandover(RoutingNode &recipient, const vespalib::Version &version, Blob payload, uint64_t timeRemaining)
{
    send(recipient, version, FillByHandover(std::move(payload)), timeRemaining);
}

void
RPCSend::send(RoutingNode &recipient, const vespalib::Version &version, BlobRef payload, uint64_t timeRemaining)
{
    send(recipient, version, FillByCopy(payload), timeRemaining);
}

void
RPCSend::send(RoutingNode &recipient, const vespalib::Version &version,
              const PayLoadFiller & payload, uint64_t timeRemaining)
{
    SendContext::UP ctx(new SendContext(recipient, timeRemaining));
    RPCServiceAddress &address = static_cast<RPCServiceAddress&>(recipient.getServiceAddress());
    const Message &msg = recipient.getMessage();
    Route route = recipient.getRoute();
    Hop hop = route.removeHop(0);

    FRT_RPCRequest *req = _net->allocRequest();
    encodeRequest(*req, version, route, address, msg, recipient.getTrace().getLevel(),  payload, timeRemaining);

    if (ctx->getTrace().shouldTrace(TraceLevel::SEND_RECEIVE)) {
        ctx->getTrace().trace(TraceLevel::SEND_RECEIVE,
                              make_string("Sending message (version %s) from %s to '%s' with %.2f seconds timeout.",
                                          version.toString().c_str(), _clientIdent.c_str(),
                                          address.getServiceName().c_str(), ctx->getTimeout()));
    }

    if (hop.getIgnoreResult()) {
        address.getTarget().getFRTTarget().InvokeVoid(req);
        if (ctx->getTrace().shouldTrace(TraceLevel::SEND_RECEIVE)) {
            ctx->getTrace().trace(TraceLevel::SEND_RECEIVE,
                                  make_string("Not waiting for a reply from '%s'.", address.getServiceName().c_str()));
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
RPCSend::RequestDone(FRT_RPCRequest *req)
{
    doRequestDone(req);
}

void
RPCSend::doRequestDone(FRT_RPCRequest *req) {
    SendContext::UP ctx(static_cast<SendContext*>(req->GetContext()._value.VOIDP));
    const string &serviceName = static_cast<RPCServiceAddress&>(ctx->getRecipient().getServiceAddress()).getServiceName();
    Reply::UP reply;
    Error error;
    Trace & trace = ctx->getTrace();
    if (!req->CheckReturnTypes(getReturnSpec())) {
        reply.reset(new EmptyReply());
        switch (req->GetErrorCode()) {
            case FRTE_RPC_TIMEOUT:
                error = Error(ErrorCode::TIMEOUT,
                              make_string("A timeout occured while waiting for '%s' (%g seconds expired); %s",
                                          serviceName.c_str(), ctx->getTimeout(), req->GetErrorMessage()));
                break;
            case FRTE_RPC_CONNECTION:
                error = Error(ErrorCode::CONNECTION_ERROR,
                              make_string("A connection error occured for '%s'; %s",
                                          serviceName.c_str(), req->GetErrorMessage()));
                break;
            default:
                error = Error(ErrorCode::NETWORK_ERROR,
                              make_string("A network error occured for '%s'; %s",
                                          serviceName.c_str(), req->GetErrorMessage()));
        }
    } else {
        FRT_Values &ret = *req->GetReturn();
        reply = createReply(ret, serviceName, error, trace.getRoot());
    }
    if (trace.shouldTrace(TraceLevel::SEND_RECEIVE)) {
        trace.trace(TraceLevel::SEND_RECEIVE,
                    make_string("Reply (type %d) received at %s.", reply->getType(), _clientIdent.c_str()));
    }
    reply->getTrace().swap(trace);
    if (error.getCode() != ErrorCode::NONE) {
        reply->addError(error);
    }
    _net->getOwner().deliverReply(std::move(reply), ctx->getRecipient());
    req->SubRef();
}

std::unique_ptr<Reply>
RPCSend::decode(vespalib::stringref protocolName, const vespalib::Version & version, BlobRef payload, Error & error) const
{
    Reply::UP reply;
    IProtocol * protocol = _net->getOwner().getProtocol(protocolName);
    if (protocol != nullptr) {
        Routable::UP routable = protocol->decode(version, payload);
        if (routable) {
            if (routable->isReply()) {
                reply.reset(static_cast<Reply*>(routable.release()));
            } else {
                error = Error(ErrorCode::DECODE_ERROR, "Payload decoded to a message when expecting a reply.");
            }
        } else {
            error = Error(ErrorCode::DECODE_ERROR,
                          make_string("Protocol '%s' failed to decode routable.", protocolName.c_str()));
        }

    } else {
        error = Error(ErrorCode::UNKNOWN_PROTOCOL,
                      make_string("Protocol '%s' is not known by %s.", protocolName.c_str(), _serverIdent.c_str()));
    }
    return reply;
}

void
RPCSend::handleReply(Reply::UP reply)
{
    const IProtocol * protocol = _net->getOwner().getProtocol(reply->getProtocol());
    if (!protocol || protocol->requireSequencing()) {
        doHandleReply(protocol, std::move(reply));
    } else {
        auto rejected = _net->getExecutor().execute(makeLambdaTask([this, protocol, reply = std::move(reply)]() mutable {
            doHandleReply(protocol, std::move(reply));
        }));
        assert (!rejected);
    }
}

void
RPCSend::doHandleReply(const IProtocol * protocol, Reply::UP reply) {
    ReplyContext::UP ctx(static_cast<ReplyContext*>(reply->getContext().value.PTR));
    FRT_RPCRequest &req = ctx->getRequest();
    string version = ctx->getVersion().toString();
    if (reply->getTrace().shouldTrace(TraceLevel::SEND_RECEIVE)) {
        reply->getTrace().trace(TraceLevel::SEND_RECEIVE, make_string("Sending reply (version %s) from %s.",
                                                                      version.c_str(), _serverIdent.c_str()));
    }
    Blob payload(0);
    if (reply->getType() != 0) {
        payload = protocol->encode(ctx->getVersion(), *reply);
        if (payload.size() == 0) {
            reply->addError(Error(ErrorCode::ENCODE_ERROR, "An error occured while encoding the reply, see log."));
        }
    }
    FRT_Values &ret = *req.GetReturn();
    createResponse(ret, version, *reply, std::move(payload));
    req.Return();
}

void
RPCSend::invoke(FRT_RPCRequest *req)
{
    req->Detach();
    FRT_Values &args = *req->GetParams();

    std::unique_ptr<Params> params = toParams(args);
    IProtocol * protocol = _net->getOwner().getProtocol(params->getProtocol());
    if (protocol == nullptr) {
        replyError(req, params->getVersion(), params->getTraceLevel(),
                   Error(ErrorCode::UNKNOWN_PROTOCOL,
                         make_string("Protocol '%s' is not known by %s.", params->getProtocol().c_str(), _serverIdent.c_str())));
        return;
    }
    if (protocol->requireSequencing()) {
        doRequest(req, protocol, std::move(params));
    } else {
        auto rejected = _net->getExecutor().execute(makeLambdaTask([this, req, protocol, params = std::move(params)]() mutable {
            doRequest(req, protocol, std::move(params));
        }));
        assert (!rejected);
    }
}

void
RPCSend::doRequest(FRT_RPCRequest *req, const IProtocol * protocol, std::unique_ptr<Params> params)
{

    Routable::UP routable = protocol->decode(params->getVersion(), params->getPayload());
    req->DiscardBlobs();
    if ( ! routable ) {
        replyError(req, params->getVersion(), params->getTraceLevel(),
                   Error(ErrorCode::DECODE_ERROR,
                         make_string("Protocol '%s' failed to decode routable.", params->getProtocol().c_str())));
        return;
    }
    if (routable->isReply()) {
        replyError(req, params->getVersion(), params->getTraceLevel(),
                   Error(ErrorCode::DECODE_ERROR, "Payload decoded to a reply when expecting a mesage."));
        return;
    }
    Message::UP msg(static_cast<Message*>(routable.release()));
    vespalib::stringref route = params->getRoute();
    if (!route.empty()) {
        msg->setRoute(Route::parse(route));
    }
    msg->setContext(Context(new ReplyContext(*req, params->getVersion())));
    msg->pushHandler(*this, *this);
    msg->setRetryEnabled(params->useRetry());
    msg->setRetry(params->getRetries());
    msg->setTimeReceivedNow();
    msg->setTimeRemaining(params->getRemainingTime());
    msg->getTrace().setLevel(params->getTraceLevel());
    if (msg->getTrace().shouldTrace(TraceLevel::SEND_RECEIVE)) {
        msg->getTrace().trace(TraceLevel::SEND_RECEIVE,
                              make_string("Message (type %d) received at %s for session '%s'.",
                                          msg->getType(), _serverIdent.c_str(), string(params->getSession()).c_str()));
    }
    _net->getOwner().deliverMessage(std::move(msg), params->getSession());
}

} // namespace mbus
