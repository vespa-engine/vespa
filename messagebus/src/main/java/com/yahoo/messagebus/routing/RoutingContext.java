// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import com.yahoo.jrt.slobrok.api.IMirror;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.*;

import java.util.*;

/**
 * <p>This context object is what is seen by {@link RoutingPolicy} when doing
 * both select() and merge(). It contains the necessary accessors to everything
 * a policy is expected to need. An instance of this is created for every {@link
 * RoutingNode} that contains a policy.</p>
 *
 * @author Simon Thoresen Hult
 */
public class RoutingContext {

    private final RoutingNode node;
    private final int directive;
    private final Set<Integer> consumableErrors = new HashSet<Integer>();
    private boolean selectOnRetry = true;
    private Object context = null;

    /**
     * <p>Constructs a new routing context for a given routing node and hop.</p>
     *
     * @param node      The owning routing node.
     * @param directive The index to the policy directive of the hop.
     */
    RoutingContext(RoutingNode node, int directive) {
        this.node = node;
        this.directive = directive;
    }

    public String toString() {
        return "node : " + node + ", directive: " + directive + ", errors: " + consumableErrors +
                ", selectOnRetry: " + selectOnRetry + " context: " + context;
    }

    /**
     * <p>Returns whether or not this hop has any configured recipients.</p>
     *
     * @return True if there is at least one recipient.
     */
    public boolean hasRecipients() {
        return !node.getRecipients().isEmpty();
    }

    /**
     * <p>Returns the number of configured recipients for this hop.</p>
     *
     * @return The recipient count.
     */
    public int getNumRecipients() {
        return node.getRecipients().size();
    }

    /**
     * <p>Returns the configured recipient at the given index.</p>
     *
     * @param idx The index of the recipient to return.
     * @return The reipient at the given index.
     */
    public Route getRecipient(int idx) {
        return node.getRecipients().get(idx);
    }

    /**
     * <p>Returns all configured recipients for this hop.</p>
     *
     * @return An unmodifiable list of recipients.
     */
    public List<Route> getAllRecipients() {
        return Collections.unmodifiableList(node.getRecipients());
    }

    /**
     * <p>Returns a list of all configured recipients whose first hop matches
     * this.</p>
     *
     * @return A modifiable list of recipients.
     */
    public List<Route> getMatchedRecipients() {
        List<Route> ret = new ArrayList<>();
        Set<String> done = new HashSet<>();
        Hop hop = getHop();
        for (Route route : node.getRecipients()) {
            if (route.hasHops() && hop.matches(route.getHop(0))) {
                HopDirective dir = route.getHop(0).getDirective(directive);
                String key = dir.toString();
                if (!done.contains(key)) {
                    ret.add(new Route(route).setHop(0, new Hop(hop).setDirective(directive, dir)));
                    done.add(key);
                }
            }
        }
        return ret;
    }

    /**
     * <p>Returns whether or not the policy is required to reselect if resending
     * occurs.</p>
     *
     * @return True to invoke {@link RoutingPolicy#select(RoutingContext)} on
     *         resend.
     */
    public boolean getSelectOnRetry() {
        return selectOnRetry;
    }

    /**
     * <p>Sets whether or not the policy is required to reselect if resending
     * occurs.</p>
     *
     * @param selectOnRetry The value to set.
     * @return This, to allow chaining.
     */
    public RoutingContext setSelectOnRetry(boolean selectOnRetry) {
        this.selectOnRetry = selectOnRetry;
        return this;
    }

    /**
     * <p>Returns the route that contains the routing policy that spawned
     * this.</p>
     *
     * @return The route.
     */
    public Route getRoute() {
        return node.getRoute();
    }

    /**
     * <p>Returns the hop that contains the routing policy that spawned
     * this.</p>
     *
     * @return The hop.
     */
    public Hop getHop() {
        return node.getRoute().getHop(0);
    }

    /**
     * <p>Returns the index of the hop directive that spawned this.</p>
     *
     * @return The directive index.
     */
    public int getDirectiveIndex() {
        return directive;
    }

    /**
     * <p>Returns the policy directive that spawned this.</p>
     *
     * @return The directive object.
     */
    public PolicyDirective getDirective() {
        return (PolicyDirective)getHop().getDirective(directive);
    }

    /**
     * <p>Returns the part of the route string that precedes the active policy
     * directive. This is the same as calling {@link #getHop()}.getPrefix({@link
     * #getDirectiveIndex()}).</p>
     *
     * @return The hop prefix.
     */
    public String getHopPrefix() {
        return getHop().getPrefix(directive);
    }

    /**
     * <p>Returns the remainder of the route string immediately following the
     * active policy directive. This is the same as calling {@link
     * #getHop()}.getSuffix({@link #getDirectiveIndex()}).</p>
     *
     * @return The hop suffix.
     */
    public String getHopSuffix() {
        return getHop().getSuffix(directive);
    }

    /**
     * <p>Returns the policy specific context object.</p>
     *
     * @return The context.
     */
    public Object getContext() {
        return context;
    }

    /**
     * <p>Sets a policy specific context object that will be available at
     * merge().</p>
     *
     * @param context An arbitrary object.
     * @return This, to allow chaining.
     */
    public RoutingContext setContext(Object context) {
        this.context = context;
        return this;
    }

    /**
     * <p>Returns the message being routed.</p>
     *
     * @return The message.
     */
    public Message getMessage() {
        return node.getMessage();
    }

    /**
     * <p>Adds a string to the trace of the message being routed.</p>
     *
     * @param level The level of the trace note.
     * @param note  The note to add.
     */
    public void trace(int level, String note) {
        node.getTrace().trace(level, note);
    }

    /**
     * Indicates if tracing is enabled at this level.
     * @param level the level
     * @return  true if tracing is enabled at this level
     */
    public boolean shouldTrace(int level) {
        return node.getTrace().shouldTrace(level);
    }

    /**
     * <p>Returns whether or not a reply is available.</p>
     *
     * @return True if a reply is set.
     */
    public boolean hasReply() {
        return node.hasReply();
    }

    /**
     * <p>Returns the reply generated by the associated routing policy.</p>
     *
     * @return The reply.
     */
    public Reply getReply() {
        return node.getReply();
    }

    /**
     * <p>Sets the reply generated by the associated routing policy.</p>
     *
     * @param reply The reply to set.
     * @return This, to allow chaining.
     */
    public RoutingContext setReply(Reply reply) {
        node.setReply(reply);
        return this;
    }

    /**
     * <p>This is a convenience method to call {@link #setError(Error)}.</p>
     *
     * @param code The code of the error to set.
     * @param msg  The message of the error to set.
     * @return This, to allow chaining.
     */
    public RoutingContext setError(int code, String msg) {
        node.setError(code, msg);
        return this;
    }

    /**
     * <p>This is a convenience method to assign an {@link EmptyReply}
     * containing a single error to this. This also fiddles with the trace
     * object so that the error gets written to it.</p>
     *
     * @param err The error to set.
     * @return This, to allow chaining.
     * @see #setReply(Reply)
     */
    public RoutingContext setError(Error err) {
        node.setError(err);
        return this;
    }

    /**
     * <p>Returns the message bus instance on which this is running.</p>
     *
     * @return The message bus.
     */
    public MessageBus getMessageBus() {
        return node.getMessageBus();
    }

    /**
     * <p>Returns whether or not the owning routing node has any child
     * nodes.</p>
     *
     * @return True if there is at least one child.
     */
    public boolean hasChildren() {
        return !node.getChildren().isEmpty();
    }

    /**
     * <p>Returns the number of children the owning routing node has.</p>
     *
     * @return The child count.
     */
    public int getNumChildren() {
        return node.getChildren().size();
    }

    /**
     * <p>Returns an iterator for the child routing nodes of the owning
     * node.</p>
     *
     * @return The iterator.
     */
    public RoutingNodeIterator getChildIterator() {
        return new RoutingNodeIterator(node.getChildren());
    }

    /**
     * <p>Adds a child routing context to this based on a given route. This is
     * the typical entry point a policy will use to select recipients during a
     * {@link RoutingPolicy#select(RoutingContext)} invocation.</p>
     *
     * @param route The route to contain in the child context.
     */
    public void addChild(Route route) {
        node.addChild(route);
    }

    /**
     * <p>This is a convenience method to more easily add a list of children to
     * this. It will simply call the {@link #addChild} method for each element
     * in the list.</p>
     *
     * @param routes A list of routes to add as children.
     */
    public void addChildren(List<Route> routes) {
        if (routes != null) {
            for (Route route : routes) {
                addChild(route);
            }
        }
    }

    /**
     * <p>Returns the local mirror of the system's name server.</p>
     *
     * @return The mirror api.
     */
    public IMirror getMirror() {
        return node.getNetwork().getMirror();
    }

    /**
     * <p>Adds the given error code to the list of codes that the associated
     * routing policy <u>may</u> consume. This is used to verify whether or not
     * a resolved routing tree can succeed if sent. Because verification is only
     * done before sending, the error types that must be added here are only
     * those that can be generated by message bus itself.</p>
     *
     * @param errorCode The code that might be consumed.
     * @see RoutingNode#getUnconsumedErrors()
     * @see com.yahoo.messagebus.ErrorCode
     */
    public void addConsumableError(int errorCode) {
        consumableErrors.add(errorCode);
    }

    /**
     * <p>Returns whether or not the given error code <u>may</u> be consumed by
     * the associated routing policy.</p>
     *
     * @param errorCode The code to check.
     * @return True if the code may be consumed.
     * @see #addConsumableError(int)
     */
    public boolean isConsumableError(int errorCode) {
        return consumableErrors.contains(errorCode);
    }
}
