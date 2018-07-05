// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Stack;

/**
 * An wrapper around a stack of frame objects that is aware of the message that owns it. It contains functionality to
 * move the content of itself to another, never to copy, since a callback is unique and might be counted by
 * implementations such as Resender.
 *
 * @author Simon Thoresen Hult
 */
public class CallStack {

    private Deque<StackFrame> stack = new ArrayDeque<>();

    /**
     * Push a handler onto the callstack of this message with a given context.
     *
     * @param handler The reply handler to store.
     * @param context The context to be associated with the message for that handler.
     */
    public void push(ReplyHandler handler, Object context) {
        stack.push(new StackFrame(handler, context));
    }

    /**
     * Pop a frame from this stack. The handler part of the frame will be returned and the context part will be set on
     * the given reply. Invoke this method on an empty stack and terrible things will happen.
     *
     * @param routable The routable that will have its context set.
     * @return The next handler on the stack.
     */
    public ReplyHandler pop(Routable routable) {
        StackFrame frame = stack.pop();
        routable.setContext(frame.context);
        return frame.handler;
    }

    /**
     * Swap the content of this and the argument stack.
     *
     * @param other The stack to swap content with.
     */
    public void swap(CallStack other) {
        Deque<StackFrame> tmp = stack;
        stack = other.stack;
        other.stack = tmp;
    }

    /**
     * Clear this call stack. This method should only be used when you are certain that it is safe to just throw away
     * the stack. It has similar effects to stopping a thread, you need to know where it is safe to do so.
     */
    public void clear() {
        stack.clear();
    }

    /**
     * Returns the number of elements of the callstack.
     *
     * @return The number of elements.
     */
    public int size() {
        return stack.size();
    }

    /**
     * Helper class that holds stack frame data.
     */
    private static class StackFrame {

        private final ReplyHandler handler;
        private final Object context;

        public StackFrame(ReplyHandler handler, Object context) {
            this.handler = handler;
            this.context = context;
        }
    }
}
