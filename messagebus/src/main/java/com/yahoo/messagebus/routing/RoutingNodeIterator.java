// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import com.yahoo.messagebus.Reply;

import java.util.Iterator;
import java.util.List;

/**
 * Implements an iterator for routing nodes. Use {@link RoutingContext#getChildIterator()} to retrieve an instance of
 * this.
 *
 * @author Simon Thoresen Hult
 */
public class RoutingNodeIterator {

    // The underlying iterator.
    private Iterator<RoutingNode> it;

    // The current child entry.
    private RoutingNode entry;

    /**
     * Constructs a new iterator based on a given list.
     *
     * @param children The list to iterate through.
     */
    public RoutingNodeIterator(List<RoutingNode> children) {
        it = children.iterator();
        next();
    }

    /**
     * Steps to the next child in the map.
     *
     * @return This, to allow chaining.
     */
    public RoutingNodeIterator next() {
        entry = it.hasNext() ? it.next() : null;
        return this;
    }

    /**
     * Skips the given number of children.
     *
     * @param num The number of children to skip.
     * @return This, to allow chaining.
     */
    public RoutingNodeIterator skip(int num) {
        for (int i = 0; i < num && isValid(); ++i) {
            next();
        }
        return this;
    }

    /**
     * Returns whether or not this iterator is valid.
     *
     * @return True if we are still pointing to a valid entry.
     */
    public boolean isValid() {
        return entry != null;
    }

    /**
     * Returns the route of the current child.
     *
     * @return The route.
     */
    public Route getRoute() {
        return entry.getRoute();
    }

    /**
     * Returns whether or not a reply is set in the current child.
     *
     * @return True if a reply is available.
     */
    public boolean hasReply() {
        return entry.hasReply();
    }

    /**
     * Removes and returns the reply of the current child. This is the correct way of reusing a reply of a child node,
     * the {@link #getReplyRef()} should be used when just inspecting a child reply.
     *
     * @return The reply.
     */
    public Reply removeReply() {
        Reply ret = entry.getReply();
        ret.getTrace().setLevel(entry.getTrace().getLevel());
        ret.getTrace().swap(entry.getTrace());
        entry.setReply(null);
        return ret;
    }

    /**
     * Returns the reply of the current child. It is VERY important that the reply returned by this function is not
     * reused anywhere. This is a reference to another node's reply, do NOT use it for anything but inspection. If you
     * want to retrieve and reuse it, call {@link #removeReply()} instead.
     *
     * @return The reply.
     */
    public Reply getReplyRef() {
        return entry.getReply();
    }
}
