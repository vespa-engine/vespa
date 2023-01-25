// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.SendProxy;
import com.yahoo.messagebus.Trace;
import com.yahoo.messagebus.TraceLevel;
import com.yahoo.messagebus.TraceNode;
import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.network.ServiceAddress;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class represents a node in the routing tree that is created when a route is resolved. There will be one node per
 * modification of the route. For every {@link RoutingPolicy} there will be an instance of this that has its policy and
 * {@link RoutingContext} member set. A policy is oblivious to this class, it can only access the context object.
 *
 * @author Simon Thoresen Hult
 */
public class RoutingNode implements ReplyHandler {

    private final MessageBus mbus;
    private final Network net;
    private final Resender resender;
    private final RoutingNode parent;
    private final List<Route> recipients = new ArrayList<>();
    private final List<RoutingNode> children = new ArrayList<>();
    private final ReplyHandler handler;
    private final Trace trace;
    private final AtomicInteger pending = new AtomicInteger(0);
    private final Message msg;
    private Reply reply = null;
    private Route route;
    private RoutingPolicy policy = null;
    private RoutingContext routingContext = null;
    private ServiceAddress serviceAddress = null;
    private boolean isActive = true;
    private boolean shouldRetry = false;

    /**
     * Constructs a new instance of this class. This is the root node constructor, and will be used by the different
     * sessions for sending messages. Note that the {@link #discard()} functionality of this class is implemented so
     * that it passes a null reply to the handler to notify the discard.
     *
     * @param mbus     The message bus on which we are running.
     * @param net      The network layer we are to transmit through.
     * @param resender The resender to schedule with.
     * @param handler  The handler to receive the final reply.
     * @param msg      The message being sent.
     */
    public RoutingNode(MessageBus mbus, Network net, Resender resender, ReplyHandler handler, Message msg) {
        this.mbus = mbus;
        this.net = net;
        this.resender = resender;
        this.handler = handler;
        this.msg = msg;
        this.trace = new Trace(msg.getTrace().getLevel());
        this.route = msg.getRoute();
        this.parent = null;
    }

    /**
     * Constructs a new instance of this class. This is the child node constructor, and is the constructor used when
     * building the routing tree.
     *
     * @param parent The parent routing node.
     * @param route  The route to assign to this.
     */
    private RoutingNode(RoutingNode parent, Route route) {
        mbus = parent.mbus;
        net = parent.net;
        resender = parent.resender;
        handler = null;
        msg = parent.msg;
        trace = new Trace(parent.trace.getLevel());
        this.route = new Route(route);
        this.parent = parent;
        recipients.addAll(parent.recipients);
    }

    /**
     * Discards this routing node. Invoking this will notify the parent {@link SendProxy} to ensure that the
     * corresponding message is discarded. This is a required step to ensure safe shutdown if you need to destroy a
     * message bus instance while there are still routing nodes alive in your application.
     */
    public void discard() {
        if (handler != null) {
            handler.handleReply(null);
        } else if (parent != null) {
            parent.discard();
        }
    }

    /**
     * This is the single entry-point for sending a message along a route. This can only be invoked on the root node of
     * a routing tree. It runs all the necessary selection, verification and transmission logic. Once this has been
     * called, it guarantees that a reply is returned to the registered reply handler.
     */
    public void send() {
        if (!resolve(0)) {
            notifyAbort("Route resolution failed.");
        } else {
            String errors = getUnconsumedErrors();
            if (errors != null) {
                notifyAbort("Errors found while resolving route: " + errors);
            } else {
                notifyTransmit();
            }
        }
    }

    /**
     * This method assigns an error reply to all unsent leaf nodes, and invokes {@link #notifyParent()} on them. This
     * has the effect of ensuring that a reply will return to sender.
     *
     * @param msg The error message to assign.
     */
    private void notifyAbort(String msg) {
        Deque<RoutingNode> stack = new ArrayDeque<>();
        stack.push(this);
        while (!stack.isEmpty()) {
            RoutingNode node = stack.pop();
            if (!node.isActive) {
                // reply not pending
            } else if (node.reply != null) {
                node.notifyParent();
            } else if (node.children.isEmpty()) {
                node.setError(ErrorCode.SEND_ABORTED, msg);
                node.notifyParent();
            } else {
                for (RoutingNode child : node.children) {
                    stack.push(child);
                }
            }
        }
    }

    /**
     * This method collects all unsent leaf nodes and passes them to {@link Network#send(com.yahoo.messagebus.Message,
     * java.util.List)}. This is orthogonal to {@link #notifyAbort(String)} in that it ensures that a reply will return
     * to sender.
     */
    private void notifyTransmit() {
        List<RoutingNode> sendTo = new ArrayList<>();
        Deque<RoutingNode> stack = new ArrayDeque<>();
        stack.push(this);
        while (!stack.isEmpty()) {
            RoutingNode node = stack.pop();
            if (node.isActive) {
                if (node.children.isEmpty()) {
                    if (node.reply != null) {
                        node.notifyParent();
                    } else {
                        sendTo.add(node);
                    }
                } else {
                    for (RoutingNode child : node.children) {
                        stack.push(child);
                    }
                }
            }
        }
        if (!sendTo.isEmpty()) {
            net.send(msg, sendTo);
        }
    }

    /**
     * This method may only be invoked on a root node, as it passes the current reply to the member {@link
     * ReplyHandler}.
     */
    private void notifySender() {
        reply.getTrace().swap(trace);
        handler.handleReply(reply);
        reply = null;
    }

    /**
     * This method marks this node as ready for merge. If it has a parent routing node, its pending member is
     * decremented. If this causes the parent's pending count to reach zero, its {@link #notifyMerge()} method is
     * invoked. A special flag is used to make sure that failed resending avoids notifying parents of previously
     * resolved branches of the tree.
     */
    private void notifyParent() {
        if (serviceAddress != null) {
            net.freeServiceAddress(this);
        }
        tryIgnoreResult();
        if (parent != null) {
            parent.notifyMerge();
            return;
        }
        if (shouldRetry && resender.scheduleRetry(this)) {
            return;
        }
        notifySender();
    }

    /**
     * This method merges the content of all its children, and invokes itself on the parent node. If not all children
     * are ready for merge, this method does nothing. The rationale for this is that the last child to receive a reply
     * will propagate the merge upwards. Once this method reaches the root node, the reply is either scheduled for
     * resending or passed to the owning reply handler.
     */
    private void notifyMerge() {
        if (pending.decrementAndGet() != 0) {
            return; // not done yet
        }

        // Merges the trace information from all children into this. This method takes care not to spend cycles
        // manipulating the trace in case tracing is disabled.
        if (trace.getLevel() > 0) {
            TraceNode tail = new TraceNode();
            for (RoutingNode child : children) {
                TraceNode root = child.trace.getRoot();
                tail.addChild(root);
                root.clear();
            }
            tail.setStrict(false);
            trace.getRoot().addChild(tail);
        }

        // Execute the {@link RoutingPolicy#notifyMerge(RoutingContext)} method of the current routing policy. If a
        // policy fails to produce a reply, this attaches an error reply to this node.
        PolicyDirective dir = routingContext.getDirective();
        if (trace.shouldTrace(TraceLevel.SPLIT_MERGE)) {
            trace.trace(TraceLevel.SPLIT_MERGE, "Routing policy '" + dir.getName() + "' merging replies.");
        }
        try {
            policy.merge(routingContext);
        } catch (RuntimeException e) {
            setError(ErrorCode.POLICY_ERROR,
                     "Policy '" + dir.getName() + "' and route '" + route + "' threw an exception during merge; " + exceptionMessageWithTrace(e));
        }
        if (reply == null) {
            setError(ErrorCode.APP_FATAL_ERROR,
                     "Routing policy '" + routingContext.getDirective().getName() + "' failed to merge replies.");
        }

        // Notifies the parent node.
        notifyParent();
    }

    /**
     * This is a helper method to call {@link Hop#getIgnoreResult()} on the first Hop of the current Route.
     *
     * @return True to ignore the result.
     */
    private boolean shouldIgnoreResult() {
        return route != null && route.getNumHops() > 0 && route.getHop(0).getIgnoreResult();
    }

    /**
     * If a reply has been set containing an error, and {@link #shouldIgnoreResult()} returns <code>true</code>, this method
     * replaces that reply with one that has no error.
     *
     * @return Whether or not the reply was replaced.
     */
    private boolean tryIgnoreResult() {
        if (!shouldIgnoreResult()) {
            return false;
        }
        if (reply == null || !reply.hasErrors()) {
            return false;
        }
        setReply(new EmptyReply());
        trace.trace(TraceLevel.SPLIT_MERGE, "Ignoring errors in reply.");
        return true;
    }

    /**
     * This method is used to reset the internal state of routing nodes that will be resent. If a routing policy sets
     * {@link RoutingContext#setSelectOnRetry(boolean)} to true, this method will reroute everything from that node
     * onwards. If that flag is not set, scheduling recurses into any child that got a reply with only transient errors.
     * Finally, if neither this node or none of its children were scheduled for resending, force reroute from this.
     */
    void prepareForRetry() {
        shouldRetry = false;
        reply = null;
        if (routingContext != null && routingContext.getSelectOnRetry()) {
            children.clear();
        } else if (!children.isEmpty()) {
            boolean retryingSome = false;
            for (RoutingNode child : children) {
                if (child.shouldRetry || child.reply == null) {
                    child.prepareForRetry();
                    retryingSome = true;
                }
            }
            if (!retryingSome) {
                // Entering here means we have no children that should be resent even though this node reports a transient
                // error. The only thing we can do is to reselect from this.
                children.clear();
            }
        }
    }

    /**
     * Return any errors preventing transmitting along this routing tree to possibly succeed. This might happen if
     * either a) there are no leaf nodes to send to, or b) some leaf node contains a fatal error that is not masked by a
     * routing policy above it in the tree. If only transient errors would reach this, the resend flag is set to true.
     *
     * @return The errors concatenated or null.
     */
    private String getUnconsumedErrors() {
        StringBuilder errors = null;

        Deque<RoutingNode> stack = new ArrayDeque<>();
        stack.push(this);
        while (!stack.isEmpty()) {
            RoutingNode node = stack.pop();
            if (node.reply != null) {
                for (int i = 0; i < node.reply.getNumErrors(); ++i) {
                    Error error = node.reply.getError(i);
                    int errorCode = error.getCode();
                    RoutingNode it = node;
                    while (it != null) {
                        if (it.routingContext != null && it.routingContext.isConsumableError(errorCode)) {
                            errorCode = ErrorCode.NONE;
                            break;
                        }
                        it = it.parent;
                    }
                    if (errorCode != ErrorCode.NONE) {
                        if (errors == null) {
                            errors = new StringBuilder();
                        } else {
                            errors.append("\n");
                        }
                        errors.append(error.toString());
                        shouldRetry = resender != null && resender.canRetry(errorCode);
                        if (!shouldRetry) {
                            return errors.toString(); // no need to continue
                        }
                    }
                }
            } else {
                for (RoutingNode child : node.children) {
                    stack.push(child);
                }
            }
        }

        return errors != null ? errors.toString() : null;
    }

    /**
     * This method performs the necessary selection logic to resolve the next step of the current route. There is a hard
     * limit to how deep the routing tree may resolve to, and if that depth is ever exceeded, this method returns false.
     * This should only really happen if routing has been misconfigured.
     *
     * @param depth The current depth.
     * @return False if selection failed.
     */
    private boolean resolve(int depth) {
        if (route == null || !route.hasHops()) {
            setError(ErrorCode.ILLEGAL_ROUTE, "Route has no hops.");
            return false;
        }
        if (!children.isEmpty()) {
            return resolveChildren(depth + 1);
        }
        while (lookupHop() || lookupRoute()) {
            if (++depth > 64) {
                break;
            }
        }
        if (depth > 64) {
            setError(ErrorCode.ILLEGAL_ROUTE, "Depth limit exceeded.");
            return false;
        }
        if (findErrorDirective()) {
            return false;
        }
        if (findPolicyDirective()) {
            if (executePolicySelect()) {
                return resolveChildren(depth + 1);
            }
            return reply != null;
        }
        net.allocServiceAddress(this);
        return serviceAddress != null || reply != null;
    }

    /**
     * This method checks to see whether the string representation of the current hop is actually the name of another.
     * If a hop is found, the first hop of the current route is replaced by this.
     *
     * @return True if a hop was found and added.
     */
    private boolean lookupHop() {
        RoutingTable table = mbus.getRoutingTable(msg.getProtocol());
        if (table != null) {
            String name = route.getHop(0).getServiceName();
            if (table.hasHop(name)) {
                HopBlueprint hop = table.getHop(name);
                configureFromBlueprint(hop);
                if (trace.shouldTrace(TraceLevel.SPLIT_MERGE)) {
                    trace.trace(TraceLevel.SPLIT_MERGE, "Recognized '" + name + "' as " + hop + ".");
                }
                return true;
            }
        }
        return false;
    }

    /**
     * This method checks to see whether the current hop contains a {@link RouteDirective}, or if its string
     * representation is actually the name of a configured route. If a route is found, the first hop of the current
     * route is replaced by expanding the named route. If a route directive requests a non-existant route, this method
     * creates an error-reply for this node.
     *
     * @return True if a route was found and added.
     * @see #insertRoute(Route)
     */
    private boolean lookupRoute() {
        RoutingTable table = mbus.getRoutingTable(msg.getProtocol());
        Hop hop = route.getHop(0);
        if (hop.getDirective(0) instanceof RouteDirective dir) {
            if (table == null || !table.hasRoute(dir.getName())) {
                setError(ErrorCode.ILLEGAL_ROUTE, "Route '" + dir.getName() + "' does not exist.");
                return false;
            }
            insertRoute(table.getRoute(dir.getName()));
            if (trace.shouldTrace(TraceLevel.SPLIT_MERGE)) {
                trace.trace(TraceLevel.SPLIT_MERGE,
                            "Route '" + dir.getName() + "' retrieved by directive; new route is '" + route + "'.");
            }
            return true;
        }
        if (table != null) {
            String name = hop.getServiceName();
            if (table.hasRoute(name)) {
                insertRoute(table.getRoute(name));
                if (trace.shouldTrace(TraceLevel.SPLIT_MERGE)) {
                    trace.trace(TraceLevel.SPLIT_MERGE, "Recognized '" + name + "' as route '" + route + "'.");
                }
                return true;
            }
        }
        return false;
    }

    /**
     * This method replaces the first hop of the current route with the given route.
     *
     * @param ins The route to insert.
     */
    private void insertRoute(Route ins) {
        Route route = new Route(ins);
        if (shouldIgnoreResult()) {
            route.getHop(0).setIgnoreResult(true);
        }
        for (int i = 1; i < this.route.getNumHops(); ++i) {
            route.addHop(this.route.getHop(i));
        }
        this.route = route;
    }

    /**
     * This method traverses the current hop looking for an instance of {@link ErrorDirective}. If one is found, this
     * method assigns a corresponding error reply to this node.
     *
     * @return True if an error was found.
     */
    private boolean findErrorDirective() {
        Hop hop = route.getHop(0);
        for (int i = 0; i < hop.getNumDirectives(); ++i) {
            HopDirective dir = hop.getDirective(i);
            if (dir instanceof ErrorDirective) {
                setError(ErrorCode.ILLEGAL_ROUTE, ((ErrorDirective)dir).getMessage());
                return true;
            }
        }
        return false;
    }

    /**
     * This method traverses the current hop looking for an instance of {@link PolicyDirective}. If one is found, this
     * method creates and assigns a routing context to this.
     *
     * @return True if a policy was found.
     */
    private boolean findPolicyDirective() {
        Hop hop = route.getHop(0);
        for (int i = 0; i < hop.getNumDirectives(); ++i) {
            HopDirective dir = hop.getDirective(i);
            if (dir instanceof PolicyDirective) {
                routingContext = new RoutingContext(this, i);
                return true;
            }
        }
        return false;
    }

    private static String exceptionMessageWithTrace(Exception e) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            e.printStackTrace(pw);
            pw.flush();
        }
        return sw.toString();
    }

    /**
     * Creates the {@link RoutingPolicy} referenced by the current routing context, and executes its {@link
     * RoutingPolicy#select(RoutingContext)} method.
     *
     * @return True if at least one child was added.
     */
    private boolean executePolicySelect() {
        PolicyDirective dir = routingContext.getDirective();
        policy = mbus.getRoutingPolicy(msg.getProtocol(), dir.getName(), dir.getParam());
        if (policy == null) {
            setError(ErrorCode.UNKNOWN_POLICY,
                     "Protocol '" + msg.getProtocol() + "' could not create routing policy '" +
                     dir.getName() + "' with parameter '" + dir.getParam() + "'.");
            return false;
        }
        if (trace.shouldTrace(TraceLevel.SPLIT_MERGE)) {
            trace.trace(TraceLevel.SPLIT_MERGE, "Running routing policy '" + dir.getName() + "'.");
        }
        try {
            policy.select(routingContext);
        } catch (RuntimeException e) {
            setError(ErrorCode.POLICY_ERROR,
                     "Policy '" + dir.getName() + "' and route '" +route + "' threw an exception during select; " + exceptionMessageWithTrace(e));
            return false;
        }
        if (children.isEmpty()) {
            if (reply == null) {
                setError(ErrorCode.NO_SERVICES_FOR_ROUTE,
                         "Policy '" + dir.getName() + "' selected no recipients for route '" + route + "'.");
            } else {
                if (trace.shouldTrace(TraceLevel.SPLIT_MERGE)) {
                    trace.trace(TraceLevel.SPLIT_MERGE,
                                "Policy '" + dir.getName() + "' assigned a reply to this branch.");
                }
            }
            return false;
        }
        for (RoutingNode child : children) {
            if (child.trace.shouldTrace(TraceLevel.SPLIT_MERGE)) {
                Hop hop = child.route.getHop(0);
                child.trace.trace(TraceLevel.SPLIT_MERGE,
                                  "Component '" + hop + "' selected by policy '" + dir.getName() + "'.");
            }
        }
        return true;
    }

    /**
     * This method invokes the {@link #resolve(int)} method of all the child nodes of this. If any of these exceed the
     * depth limit, this method returns false.
     *
     * @param childDepth The depth of the children.
     * @return False if depth limit was exceeded.
     */
    private boolean resolveChildren(int childDepth) {
        int numActiveChildren = 0;
        boolean ret = true;
        for (RoutingNode child : children) {
            if (child.trace.shouldTrace(TraceLevel.SPLIT_MERGE)) {
                child.trace.trace(TraceLevel.SPLIT_MERGE, "Resolving '" + child.route + "'.");
            }
            child.isActive = (child.reply == null);
            if (child.isActive) {
                ++numActiveChildren;
                if (!child.resolve(childDepth)) {
                    ret = false;
                    break;
                }
            } else {
                if (child.trace.shouldTrace(TraceLevel.SPLIT_MERGE)) {
                    child.trace.trace(TraceLevel.SPLIT_MERGE, "Already completed.");
                }
            }
        }
        pending.set(numActiveChildren);
        return ret;
    }

    /**
     * Adds a child routing node to this based on a route. This is package private because client code should only
     * access it through a {@link RoutingPolicy} and its {@link RoutingContext#addChild(Route)} method.
     *
     * @param route The route to store in the child node.
     */
    void addChild(Route route) {
        RoutingNode child = new RoutingNode(this, route);
        if (shouldIgnoreResult()) {
            child.route.getHop(0).setIgnoreResult(true);
        }
        children.add(child);
    }

    /**
     * Configures this node based on a hop blueprint. For each recipient in the blueprint it creates a copy of the
     * current route, and sets the first hop of that route to be the configured recipient hop. In effect, this replaces
     * the current hop and retains the rest of the route.
     *
     * @param hop The blueprint to use for configuration.
     */
    private void configureFromBlueprint(HopBlueprint hop) {
        boolean ignoreResult = shouldIgnoreResult();
        route.setHop(0, hop.create());
        if (ignoreResult) {
            route.getHop(0).setIgnoreResult(true);
        }
        recipients.clear();
        for (int r = 0; r < hop.getNumRecipients(); ++r) {
            Route recipient = new Route();
            recipient.addHop(hop.getRecipient(r));
            for (int h = 1; h < route.getNumHops(); ++h) {
                recipient.addHop(route.getHop(h));
            }
            recipients.add(recipient);
        }
    }

    /**
     * This is a convenience method to call {@link #setError(Error)}.
     *
     * @param code The code of the error to set.
     * @param msg  The message of the error to set.
     */
    public void setError(int code, String msg) {
        setError(new Error(code, msg));
    }

    /**
     * This is a convenience method to assign an {@link EmptyReply} containing a single error to this. This also fiddles
     * with the trace object so that the error gets written to it.
     *
     * @param err The error to set.
     * @see #setReply(Reply)
     */
    public void setError(Error err) {
        Reply reply = new EmptyReply();
        reply.getTrace().setLevel(trace.getLevel());
        reply.addError(err);
        setReply(reply);
    }

    /**
     * This is a convenience method to call {@link #addError(Error)}.
     *
     * @param code The code of the error to add.
     * @param msg  The message of the error to add.
     */
    public void addError(int code, String msg) {
        addError(new Error(code, msg));
    }

    /**
     * This is a convenience method to add an error to this. If a reply has already been set, this method will add the
     * error to it. If no reply is set, this method calls {@link #setError(Error)}. This method also fiddles with the
     * trace object so that the error gets written to it.
     *
     * @param err The error to add.
     */
    public void addError(Error err) {
        if (reply != null) {
            reply.getTrace().swap(trace);
            reply.addError(err);
            reply.getTrace().swap(trace);
        } else {
            setError(err);
        }
    }

    /**
     * Returns the message bus being used to send the message.
     *
     * @return The message bus.
     */
    MessageBus getMessageBus() {
        return mbus;
    }

    /**
     * Returns the network being used to send the message.
     *
     * @return The network layer.
     */
    Network getNetwork() {
        return net;
    }

    /**
     * Returns the message being routed. You should NEVER modify a message that is retrieved from a routing node or
     * context, as the result of doing so is undefined.
     *
     * @return The message being routed.
     */
    public Message getMessage() {
        return msg;
    }

    /**
     * Returns the trace object for this node. Each node has a separate trace object so that merging can be done
     * correctly.
     *
     * @return The trace object.
     */
    public Trace getTrace() {
        return trace;
    }

    /**
     * Returns the route object as it exists at this point of the tree.
     *
     * @return The route at this point.
     */
    public Route getRoute() {
        return route;
    }

    /**
     * Returns whether or not this node contains a reply.
     *
     * @return True if this node has a reply.
     */
    boolean hasReply() {
        return reply != null;
    }

    /**
     * Returns the reply of this node.
     *
     * @return The reply assigned to this node.
     */
    Reply getReply() {
        return reply;
    }

    /**
     * Sets the reply of this routing node. This method also updates the internal state of this node; it is tagged for
     * resending if the reply has only transient errors, and the reply's {@link Trace} is copied. This method <u>does
     * not</u> call the parent node's {@link #notifyMerge()}.
     *
     * @param reply The reply to set.
     */
    public void setReply(Reply reply) {
        if (reply != null) {
            shouldRetry = resender != null && resender.shouldRetry(reply);
            trace.getRoot().addChild(reply.getTrace().getRoot());
            reply.getTrace().clear();
        }
        this.reply = reply;
    }

    /**
     * Returns the list of configured recipient {@link Route routes}. This is accessed by client code through a more
     * strict api in {@link RoutingContext}.
     *
     * @return The list of recipients.
     */
    List<Route> getRecipients() {
        return recipients;
    }

    /**
     * Returns the list of current child nodes. This is accessed by client code through a more strict api in {@link
     * RoutingContext}.
     *
     * @return The list of children.
     */
    List<RoutingNode> getChildren() {
        return children;
    }

    /**
     * Returns the service address of this node. This is attached by the network layer, and should only ever be present
     * in leaf nodes.
     *
     * @return The recipient address.
     */
    public ServiceAddress getServiceAddress() {
        return serviceAddress;
    }

    /**
     * Sets the service address of this node. This is called by the network layer as this calls its {@link
     * Network#allocServiceAddress(RoutingNode)} method.
     *
     * @param serviceAddress The recipient address.
     */
    public void setServiceAddress(ServiceAddress serviceAddress) {
        this.serviceAddress = serviceAddress;
    }

    /** Proxy through message bus in case it was destroyed in the meantime. */
    @Override
    public void handleReply(Reply reply) {
        mbus.deliverReply(reply, r -> {
            setReply(reply);
            notifyParent();
        });
    }

}
