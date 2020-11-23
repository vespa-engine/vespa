// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "callstack.h"
#include "trace.h"
#include "common.h"

namespace mbus {

/**
 * Superclass for objects that can be either explicitly (Message) or implicitly
 * (Reply) routed. Note that protocol implementors should never subclass this
 * directly, but rather through the {@link Message} and {@link Reply} classes.
 *
 * A routable can be regarded as a protocol-defined value with additional
 * message bus related state. The state is what differentiates two Routables
 * that carry the same value. This includes the application context attached to
 * the routable and the {@link CallStack} used to track the path of the routable
 * within messagebus. When a routable is copied (if the protocol supports it)
 * only the value part is copied. The state must be explicitly transfered by
 * invoking the {@link #swapState(Routable)} method. That method is used to
 * transfer the state from a message to the corresponding reply, or to a
 * different message if the application decides to replace it.
 */
class Routable {
private:
    Context    _context;
    CallStack  _stack;
    Trace      _trace;

public:
    /**
     * Convenience typedef for an auto pointer to a Routable object.
     */
    typedef std::unique_ptr<Routable> UP;
    Routable(const Routable &) = delete;
    Routable & operator = (const Routable &) = delete;

    /**
     * Constructs a new instance of this class.
     */
    Routable();

    /**
     * Required for inheritance.
     */
    virtual ~Routable();

    /**
     * Discards this routable. Invoking this prevents the auto-generation of
     * replies if you later discard the routable.  This is a required step to
     * ensure safe shutdown if you need destroy a message bus instance while
     * there are still messages and replies alive in your application.
     */
    void discard();

    /**
     * Access the CallStack of this routable. The CallStack is intended for
     * internal messagebus use.
     *
     * @return reference to internal CallStack
     */
    CallStack &getCallStack() { return _stack; }

    /**
     * Pushes the given reply handler onto the call stack of this routable, also
     * storing the current context.
     *
     * @param handler The handler to push.
     */
    void pushHandler(IReplyHandler &handler) { _stack.push(handler, _context); }

    /**
     * Pushes the given reply- and discard handler onto the call stack of this
     * routable, also storing the current context.
     *
     * @param replyHandler   The handler called if the reply arrives.
     * @param discardHandler The handler called if the reply is dicarded.
     */
    void pushHandler(IReplyHandler &replyHandler, IDiscardHandler &discardHandler) {
        _stack.push(replyHandler, _context, &discardHandler);
    }

    /**
     * Access the Trace object for this Routable. The Trace is part of the
     * object state and will not be copied along with the value part of a
     * Routable. The swapState method will transfer the Trace from one Routable
     * to another.
     *
     * @return Trace object
     */
    Trace &getTrace() { return _trace; }
    Trace && steal_trace() { return std::move(_trace); }

    /**
     * Access the Trace object for this Routable. The Trace is part of the
     * object state and will not be copied along with the value part of a
     * Routable. The swapState method will transfer the Trace from one Routable
     * to another.
     *
     * @return Trace object
     */
    const Trace &getTrace() const { return _trace; }

    /**
     * Sets the Trace object for this Routable.
     *
     * @param trace The trace to set.
     */
    void setTrace(Trace &&trace) { _trace = std::move(trace); }

    /**
     * Swaps the state that makes this routable unique to another routable. The
     * state is what identifies a routable for message bus, so only one message
     * can ever have the same state. This function must be called explicitly
     * when cloning and copying messages.
     *
     * @param rhs The routable to swap state with.
     */
    virtual void swapState(Routable &rhs);

    /**
     * Get the context of this routable.
     *
     * @return the context
     */
    Context getContext() const { return _context; }

    /**
     * Set the context of this routable. When setting a context on a Message,
     * the Reply for that Message will have the same context as the original
     * Message when received.
     *
     * @param ctx the context
     */
    void setContext(Context ctx) { _context = ctx; }

    /**
     * Check whether this routable is a reply.
     *
     * @return true if this is a reply
     */
    virtual bool isReply() const = 0;

    /**
     * Obtain the name of the protocol for this routable. This method must be
     * implemented by all routable classes part of a protocol.
     */
    virtual const string &getProtocol() const = 0;

    /**
     * Return the type of this routable. The type is protocol specific with the
     * exception that the value 0 is reserved for the EmptyReply class. This
     * value is typically intended to be used by the application to identify
     * what kind of routable object it is looking at.
     *
     * @return routable type id
     */
    virtual uint32_t getType() const = 0;

    /**
     * Returns the priority of this message. 0 is most highly
     * prioritized, and messages with higher priority will be sent
     * earlier than other messages.
     */
    virtual uint8_t priority() const = 0;

    /**
     * Returns a string representation of this message.
     */
    virtual string toString() const { return ""; }
};

} // namespace mbus
