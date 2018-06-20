// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rpcsendadapter.h"
#include <vespa/messagebus/idiscardhandler.h>
#include <vespa/messagebus/ireplyhandler.h>
#include <vespa/messagebus/common.h>
#include <vespa/fnet/frt/invokable.h>
#include <vespa/fnet/frt/invoker.h>

class FRT_ReflectionBuilder;

namespace vespalib::slime { class Cursor; }
namespace vespalib { class Memory; }
namespace vespalib { class TraceNode; }
namespace mbus {

class Error;
class Route;
class Message;
class RPCServiceAddress;
class IProtocol;

class PayLoadFiller
{
public:
    virtual ~PayLoadFiller() { }
    virtual void fill(FRT_Values & v) const = 0;
    virtual void fill(const vespalib::Memory & name, vespalib::slime::Cursor & v) const = 0;
};

class RPCSend : public RPCSendAdapter,
                public FRT_Invokable,
                public FRT_IRequestWait,
                public IDiscardHandler,
                public IReplyHandler
{
public:
    class Params {
    public:
        virtual ~Params() {}
        virtual vespalib::Version getVersion() const = 0;
        virtual vespalib::stringref getProtocol() const = 0;
        virtual uint32_t getTraceLevel() const = 0;
        virtual bool useRetry() const = 0;
        virtual uint32_t getRetries() const = 0;
        virtual uint64_t getRemainingTime() const = 0;
        virtual vespalib::stringref getRoute() const = 0;
        virtual vespalib::stringref getSession() const = 0;
        virtual BlobRef getPayload() const = 0;
    };
protected:
    RPCNetwork *_net;
    string _clientIdent;
    string _serverIdent;

    virtual void build(FRT_ReflectionBuilder & builder) = 0;
    virtual std::unique_ptr<Reply> createReply(const FRT_Values & response, const string & serviceName,
                                               Error & error, vespalib::TraceNode & rootTrace) const = 0;
    virtual void encodeRequest(FRT_RPCRequest &req, const vespalib::Version &version, const Route & route,
                               const RPCServiceAddress & address, const Message & msg, uint32_t traceLevel,
                               const PayLoadFiller &filler, uint64_t timeRemaining) const = 0;
    virtual const char * getReturnSpec() const = 0;
    virtual void createResponse(FRT_Values & ret, const string & version, Reply & reply, Blob payload) const = 0;
    virtual std::unique_ptr<Params> toParams(const FRT_Values &param) const = 0;

    void send(RoutingNode &recipient, const vespalib::Version &version,
              const PayLoadFiller & filler, uint64_t timeRemaining);
    std::unique_ptr<Reply> decode(vespalib::stringref protocol, const vespalib::Version & version,
                                  BlobRef payload, Error & error) const;
    /**
     * Send an error reply for a given request.
     *
     * @param request    The FRT request to reply to.
     * @param version    The version to serialize for.
     * @param traceLevel The trace level to set in the reply.
     * @param err        The error to reply with.
     */
    void replyError(FRT_RPCRequest *req, const vespalib::Version &version, uint32_t traceLevel, const Error &err);
public:
    RPCSend();
    ~RPCSend();

    void invoke(FRT_RPCRequest *req);
private:
    void doRequest(FRT_RPCRequest *req, const IProtocol * protocol, std::unique_ptr<Params> params);
    void doRequestDone(FRT_RPCRequest *req);
    void doHandleReply(const IProtocol * protocol, std::unique_ptr<Reply> reply);
    void attach(RPCNetwork &net) final override;
    void handleDiscard(Context ctx) final override;
    void sendByHandover(RoutingNode &recipient, const vespalib::Version &version,
                        Blob payload, uint64_t timeRemaining) final override;
    void send(RoutingNode &recipient, const vespalib::Version &version,
              BlobRef payload, uint64_t timeRemaining) final override;
    void RequestDone(FRT_RPCRequest *req) final override;
    void handleReply(std::unique_ptr<Reply> reply) final override;
};

} // namespace mbus
