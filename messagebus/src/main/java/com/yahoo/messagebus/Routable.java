// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.text.Utf8String;

/**
 * Superclass for objects that can be either explicitly (Message) or implicitly (Reply) routed. Note that protocol
 * implementors should never subclass this directly, but rather through the {@link Message} and {@link Reply} classes.
 *
 * A routable can be regarded as a protocol-defined value with additional message bus related state. The state is what
 * differentiates two Routables that carry the same value. This includes the application context attached to the
 * routable and the {@link CallStack} used to track the path of the routable within messagebus. When a routable is
 * copied (if the protocol supports it) only the value part is copied. The state must be explicitly transferred by
 * invoking the {@link #swapState(Routable)} method. That method is used to transfer the state from a message to the
 * corresponding reply, or to a different message if the application decides to replace it.
 *
 * @author Simon Thoresen Hult
 */
public abstract class Routable {

    private final CallStack callStack = new CallStack();
    private final Trace trace = new Trace();
    private Object context = null;

    /**
     * Discards this routable. Invoking this prevents the auto-generation of replies if you later discard the routable.
     * This is a required step to ensure safe shutdown if you need destroy a message bus instance while there are still
     * messages and replies alive in your application.
     */
    public void discard() {
        context = null;
        callStack.clear();
        trace.clear();
    }

    /**
     * Swaps the state that makes this routable unique to another routable. The state is what identifies a routable for
     * message bus, so only one message can ever have the same state. This function must be called explicitly when
     * cloning and copying messages.
     *
     * @param rhs The routable to swap state with.
     */
    public void swapState(Routable rhs) {
        Object context = this.context;
        this.context = rhs.context;
        rhs.context = context;

        callStack.swap(rhs.getCallStack());
        trace.swap(rhs.getTrace());
    }

    /**
     * Pushes the given reply handler onto the call stack of this routable, also storing the current context.
     *
     * @param handler The handler to push.
     */
    public void pushHandler(ReplyHandler handler) {
        callStack.push(handler, context);
    }

    /**
     * <p>This is a convenience method for calling {@link CallStack#pop(Routable)} on the {@link CallStack} of this
     * Routable. It equals calling <code>routable.getCallStack().pop(routable)</code>.</p>
     *
     * @return The handler that was popped.
     * @see CallStack#pop(Routable)
     */
    public ReplyHandler popHandler() {
        return callStack.pop(this);
    }

    /** Returns the context of this routable. */
    public Object getContext() {
        return context;
    }

    /**
     * Sets a new context for this routable. Please note that the context is <u>not</u> something that is passed along a
     * message, it is simply a user context for the handler currently manipulating a message. When the corresponding
     * reply reaches the registered reply handler, its content will be the same as that of the outgoing message. More
     * technically, this context is contained in the callstack of a routable.
     */
    public void setContext(Object context) {
        this.context = context;
    }

    /** Returns the callstack of this routable. */
    public CallStack getCallStack() {
        return callStack;
    }

    /** Returns the trace object of this routable. */
    public Trace getTrace() {
        return trace;
    }

    /**
     * Return the name of the protocol that defines this routable. This must be implemented by all inheriting classes,
     * and should then return the result of {@link com.yahoo.messagebus.Protocol#getName} of its protocol.
     *
     * @return the name of the protocol defining this message.
     */
    public abstract Utf8String getProtocol();

    /**
     * Returns the type of this routable. The id '0' is reserved for the EmptyReply class. Other ids must be defined by
     * the application protocol.
     */
    public abstract int getType();

}
