// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <string>
#include "reply.h"
#include "imessagehandler.h"
#include "intermediatesessionparams.h"

namespace mbus {

class MessageBus;
class ReplyGate;

/**
 * An IntermediateSession is used to process Message and Reply objects
 * on the way along a route.
 **/
class IntermediateSession : public IMessageHandler,
                            public IReplyHandler
{
private:
    friend class MessageBus;

    MessageBus      &_mbus;
    string           _name;
    IMessageHandler &_msgHandler;
    IReplyHandler   &_replyHandler;
    ReplyGate       *_gate;

    /**
     * This constructor is declared package private since only MessageBus is supposed to instantiate it.
     *
     * @param mbus   The message bus that created this instance.
     * @param params The parameter object for this session.
     */
    IntermediateSession(MessageBus &mbus, const IntermediateSessionParams &params);

public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<IntermediateSession> UP;

    /**
     * The destructor untangles from messagebus. After this method returns, messagebus will not invoke any
     * handlers associated with this session.
     */
    virtual ~IntermediateSession();

    /**
     * This method unregisters this session from message bus, effectively disabling any more messages from
     * being delivered to the message handler. After unregistering, this method calls {@link
     * com.yahoo.messagebus.MessageBus#sync()} as to ensure that there are no threads currently entangled in
     * the handler.
     *
     * This method will deadlock if you call it from the message or reply handler.
     */
    void close();

    /**
     * Forwards a routable to the next hop in its route. This method will never block.
     *
     * @param routable The routable to forward.
     */
    void forward(Routable::UP routable);

    /**
     * Convenience method to call {@link #forward(Routable)}.
     *
     * @param msg The message to forward.
     */
    void forward(Message::UP msg);

    /**
     * Convenience method to call {@link #forward(Routable)}.
     *
     * @param reply The reply to forward.
     */
    void forward(Reply::UP reply);

    /**
     * Returns the connection spec string for this session. This returns a combination of the owning message
     * bus' own spec string and the name of this session.
     *
     * @return The connection string.
     */
    const string getConnectionSpec() const;

    // Implements IMessageHandler.
    void handleMessage(Message::UP message) override;

    // Implements IReplyHandler.
    void handleReply(Reply::UP reply) override;
};

} // namespace mbus

