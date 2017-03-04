// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fnet/frt/frt.h>
#include <vespa/messagebus/idiscardhandler.h>
#include <vespa/messagebus/ireplyhandler.h>
#include "rpcsendadapter.h"

namespace mbus {

class PayLoadFiller
{
public:
    virtual ~PayLoadFiller() { }
    virtual void fill(FRT_Values & v) const = 0;
};

/**
 * Implements the send adapter for method "mbus.send".
 */
class RPCSendV1 : public RPCSendAdapter,
                  public FRT_Invokable,
                  public FRT_IRequestWait,
                  public IDiscardHandler,
                  public IReplyHandler {
private:
    RPCNetwork *_net;
    string _clientIdent;
    string _serverIdent;

    /**
     * Send an error reply for a given request.
     *
     * @param request    The FRT request to reply to.
     * @param version    The version to serialize for.
     * @param traceLevel The trace level to set in the reply.
     * @param err        The error to reply with.
     */
    void replyError(FRT_RPCRequest *req, const vespalib::Version &version,
                    uint32_t traceLevel, const Error &err);

    void send(RoutingNode &recipient, const vespalib::Version &version,
              const PayLoadFiller & filler, uint64_t timeRemaining);
public:
    /** The name of the rpc method that this adapter registers. */
    static const char *METHOD_NAME;

    /** The parameter string of the rpc method. */
    static const char *METHOD_PARAMS;

    /** The return string of the rpc method. */
    static const char *METHOD_RETURN;

    /**
     * Constructs a new instance of this adapter. This object is unusable until
     * its attach() method has been called.
     */
    RPCSendV1();
    ~RPCSendV1();

    // Implements RPCSendAdapter.
    void attach(RPCNetwork &net) override;

    // Implements RPCSendAdapter.
    void send(RoutingNode &recipient, const vespalib::Version &version,
              BlobRef payload, uint64_t timeRemaining) override;
    void sendByHandover(RoutingNode &recipient, const vespalib::Version &version,
              Blob payload, uint64_t timeRemaining) override;

    // Implements IReplyHandler.
    void handleReply(Reply::UP reply) override;

    // Implements IDiscardHandler.
    void handleDiscard(Context ctx) override;

    // Implements FRT_Invokable.
    void invoke(FRT_RPCRequest *req);

    // Implements FRT_IRequestWait.
    void RequestDone(FRT_RPCRequest *req) override;
};

} // namespace mbus

