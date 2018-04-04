// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iroutingpolicy.h"
#include "resender.h"
#include "route.h"
#include "routingcontext.h"
#include "routingnodeiterator.h"
#include <vespa/messagebus/idiscardhandler.h>
#include <vespa/messagebus/ireplyhandler.h>
#include <vespa/messagebus/message.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/network/iserviceaddress.h>
#include <vespa/messagebus/reply.h>
#include <vespa/vespalib/util/sync.h>
#include <vector>
#include <map>

namespace mbus {

class HopBlueprint;
class INetwork;

/**
 * This class represents a node in the routing tree that is created when a route
 * is resolved. There will be one node per modification of the route. For every
 * {@link RoutingPolicy} there will be an instance of this that has its policy
 * and {@link RoutingContext} member set. A policy is oblivious to this class,
 * it can only access the context object.
 */
class RoutingNode : public IReplyHandler {
private:
    MessageBus               &_mbus;
    INetwork                 &_net;
    Resender                 *_resender;
    RoutingNode              *_parent;
    std::vector<Route>        _recipients;
    std::vector<RoutingNode*> _children;
    IReplyHandler            *_replyHandler;
    IDiscardHandler          *_discardHandler;
    Trace                     _trace;
    std::atomic<uint32_t>     _pending;
    Message                  &_msg;
    Reply::UP                 _reply;
    Route                     _route;
    IRoutingPolicy::SP        _policy;
    RoutingContext::UP        _routingContext;
    IServiceAddress::UP       _serviceAddress;
    bool                      _isActive;
    bool                      _shouldRetry;

    /**
     * Constructs a new instance of this class. This is the child node
     * constructor, and is the constructor used when building the routing tree.
     *
     * @param parent The parent routing node.
     * @param route  The route to assign to this.
     */
    RoutingNode(RoutingNode &parent, Route route);

    /**
     * Clears the list of child routing node objects, and frees the memory used
     * to allocate them.
     */
    void clearChildren();

    /**
     * This method collects all unsent leaf nodes and passes them to {@link
     * Network#send(com.yahoo.messagebus.Message, java.util.List)}. This is
     * orthogonal to {@link #notifyAbort(String)} in that it ensures that a
     * reply will return to sender.
     */
    void notifyTransmit();

    /**
     * This method merges the content of all its children, and invokes itself on
     * the parent node. If not all children are ready for merg, this method does
     * nothing. The rationale for this is that the last child to receive a reply
     * will propagate the merge upwards. Once this method reaches the root node,
     * the reply is either scheduled for resending or passed to the owning reply
     * handler.
     */
    void notifyMerge();

    /**
     * Returns whether or not transmitting along this routing tree can possibly
     * succeed. This evaluates to false if either a) there are no leaf nodes to
     * send to, or b) some leaf node contains a fatal error that is not masked
     * by a routing policy above it in the tree. If only transient errors would
     * reach this, the resend flag is set to true.
     *
     * @return True if no error reaches this.
     */
    bool hasUnconsumedErrors();

    /**
     * This method performs the necessary selection logic to resolve the next
     * step of the current route. There is a hard limit to how deep the routing
     * tree may resolve to, and if that depth is ever exceeded, this method
     * returns false.  This should only really happen if routing has been
     * misconfigured.
     *
     * @param depth The current depth.
     * @return False if selection failed.
     */
    bool resolve(uint32_t depth);

    /**
     * This method checks to see whether the string representation of the
     * current hop is actually the name of another.  If a hop is found, the
     * first hop of the current route is replaced by this.
     *
     * @return True if a hop was found and added.
     */
    bool lookupHop();

    /**
     * This method checks to see whether the current hop contains a {@link
     * RouteDirective}, or if its string representation is actually the name of
     * a configured route. If a route is found, the first hop of the current
     * route is replaced by expanding the named route. If a route directive
     * requests a non-existant route, this method creates an error-reply for
     * this node.
     *
     * @return True if a route was found and added.
     * @see #insertRoute(Route)
     */
    bool lookupRoute();

    /**
     * This method replaces the first hop of the current route with the given
     * route.
     *
     * @param ins The route to insert.
     */
    void insertRoute(Route ins);

    /**
     * This method traverses the current hop looking for an isntance of {@link
     * ErrorDirective}. If one is found, this method assigns a corresponding
     * error reply to this node.
     *
     * @return True if an error was found.
     */
    bool findErrorDirective();

    /**
     * This method traverses the current hop looking for an instance of {@link
     * PolicyDirective}. If one is found, this method creates and assigns a
     * routing context to this.
     *
     * @return True if a policy was found.
     */
    bool findPolicyDirective();

    /**
     * Creates the {@link RoutingPolicy} referenced by the current routing
     * context, and executes its {@link RoutingPolicy#select(RoutingContext)}
     * method.
     *
     * @return True if at least one child was added.
     */
    bool executePolicySelect();

    /**
     * This method invokes the {@link #resolve(int)} method of all the child
     * nodes of this. If any of these exceed the depth limit, this method
     * returns false.
     *
     * @param childDepth The depth of the children.
     * @return False if depth limit was exceeded.
     */
    bool resolveChildren(uint32_t childDepth);

    /**
     * Configures this node based on a hop blueprint. For each recipient in the
     * blueprint it creates a copy of the current route, and sets the first hop
     * of that route to be the configured recipient hop. In effect, this
     * replaces the current hop and retains the rest of the route.
     *
     * @param hop The blueprint to use for configuration.
     */
    void configureFromBlueprint(const HopBlueprint &hop);

    /**
     * This method mergs this node as ready for merge. If it has a parent
     * routing node, its pending member is decremented. If this causes the
     * parent's pending count to reach zero, its {@link #notifyMerge()} method
     * is invoked. A special flag is used to make sure that failed resending
     * avoids notifying parents of previously resolved branches of the tree.
     */
    void notifyParent();

    /**
     * If a reply has been set containing an error, and {@link 
     * #shouldIgnoreResult()} returns <tt>true</tt>, this method replaces that
     * reply with one that has no error.
     *
     * @return Whether or not the reply was replaced.
     */
    bool tryIgnoreResult();

    /**
     * Returns whether or not to ignore any errors that may occur on this node
     * or any of its children.
     *
     * @return True to ignore the result.
     */
    bool shouldIgnoreResult();

public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<RoutingNode> UP;

    /**
     * Constructs a new instance of this class. This is the root node
     * constructor, and will be used by the different sessions for sending
     * messages.
     *
     * @param mbus           The message bus on which we are running.
     * @param net            The network layer we are to transmit through.
     * @param resender       The resender to schedule with.
     * @param replyHandler   The handler to receive the final reply.
     * @param msg            The message being sent.
     * @param discardHandler The handler to notify when discarding this.
     */
    RoutingNode(MessageBus &mbus, INetwork &net, Resender *resender,
                IReplyHandler &replyHandler, Message &msg,
                IDiscardHandler *discardHandler = nullptr);

    /**
     * Destructor. Frees up any allocated resources, namely all child nodes of
     * this.
     */
    ~RoutingNode();

    /**
     * Discards this routing node. Invoking this will notify the parent {@link
     * SendProxy} to ensure that the corresponding message is discarded, and all
     * allocated memory is freed. This is a required step to ensure safe
     * shutdown if you need to destroy a message bus instance while there are
     * still routing nodes alive in your application.
     */
    void discard();

    /**
     * This is the single entry-point for sending a message along a route. This
     * can only be invoked on the root node of a routing tree. It runs all the
     * necessary selection, verification and transmission logic. Once this has
     * been called, it guarantees that a reply is returned to the registered
     * reply handler.
     */
    void send();

    /**
     * This method is used to reset the internal state of routing nodes that
     * will be resent. If a routing policy sets {@link
     * RoutingContext#setSelectOnResend(boolean)} to true, this method will
     * reroute everything from that node onwards. If that flag is not set,
     * scheduling recurses into any child that got a reply with only transient
     * errors.  Finally, if neither this node or none of its children were
     * scheduled for resending, force reroute from this.
     */
    void prepareForRetry();

    /**
     * This method may only be invoked on a root node, as it passes the current
     * reply to the member {@link ReplyHandler}. The actual call to the handler
     * is done in a separate thread to avoid deadlocks.
     */
    void notifySender();

    /**
     * This method assigns an error reply to all unsent leaf nodes, and invokes
     * {@link #notifyParent()} on them. This has the effect of ensuring that a
     * reply will return to sender.
     *
     * @param msg The error message to assign.
     */
    void notifyAbort(const string &msg);

    /**
     * Adds a child routing node to this based on a route. This is package
     * private because client code should only access it through a {@link
     * RoutingPolicy} and its {@link RoutingContext#addChild(Route)} method.
     *
     * @param route The route to store in the child node.
     */
    void addChild(Route route);

    /**
     * This is a convenience method to call {@link #setError(Error)}.
     *
     * @param code The code of the error to set.
     * @param msg  The message of the error to set.
     */
    void setError(uint32_t code, const string &msg);

    /**
     * This is a convenience method to assign an {@link EmptyReply} containing a
     * single error to this. This also fiddles with the trace object so that the
     * error gets written to it.
     *
     * @param err The error to set.
     * @see #setReply(Reply)
     */
    void setError(const Error &err);

    /**
     * This is a convenience method to call {@link #addError(Error)}.
     *
     * @param code The code of the error to add.
     * @param msg  The message of the error to add.
     */
    void addError(uint32_t code, const string &msg);

    /**
     * This is a convenience method to add an error to this. If a reply has
     * already been set, this method will add the error to it. If no reply is
     * set, this method calls {@link #setError(Error)}. This method also fiddles
     * with the trace object so that the error gets written to it.
     *
     * @param err The error to add.
     */
    void addError(const Error &err);

    /**
     * Returns the message bus being used to send the message.
     *
     * @return The message bus.
     */
    MessageBus &getMessageBus() { return _mbus; }

    /**
     * Returns the network being used to send the message.
     *
     * @return The network layer.
     */
    INetwork &getNetwork() { return _net; }

    /**
     * Returns the message being routed. You should NEVER modify a message that
     * is retrieved from a routing node or context, as the result of doing so is
     * undefined.
     *
     * @return The message being routed.
     */
    Message &getMessage() { return _msg; }
    const Message & getMessage() const { return _msg; }

    /**
     * Returns the trace object for this node. Each node has a separate trace
     * object so that merging can be done correctly.
     *
     * @return The trace object.
     */
    Trace &getTrace() { return _trace; }
    const Trace &getTrace() const { return _trace; }

    /**
     * Returns the route object as it exists at this point of the tree.
     *
     * @return The route at this point.
     */
    const Route &getRoute() const { return _route; }

    /**
     * Returns whether or not this node contains a reply.
     *
     * @return True if this node has a reply.
     */
    bool hasReply() const { return _reply.get() != nullptr; }

    /**
     * Returns the reply of this node.
     *
     * @return The reply assigned to this node.
     */
    Reply::UP getReply() { return std::move(_reply); }

    /**
     * Returns a reference to the reply of this node. This should never be
     * called unless {@link #hasReply()} is true, because you will be deref'ing
     * null.
     *
     * @return The reply assigned to this node.
     */
    Reply &getReplyRef() { return *_reply; }

    /**
     * Sets the reply of this routing node. This method also updates the
     * internal state of this node; it is tagged for resending if the reply has
     * only transient errors, and the reply's {@link Trace} is copied. This
     * method <u>does not</u> call the parent node's {@link #notifyMerge()}.
     *
     * @param reply The reply to set.
     */
    void setReply(Reply::UP reply);

    /**
     * Returns the list of configured recipient {@link Route routes}. This is
     * accessed by client code through a more strict api in {@link
     * RoutingContext}.
     *
     * @return The list of recipients.
     */
    std::vector<Route> &getRecipients() { return _recipients; }

    /**
     * Returns the list of current child nodes. This is accessed by client code
     * through a more strict api in {@link RoutingContext}.
     *
     * @return The list of children.
     */
    std::vector<RoutingNode*> &getChildren() { return _children; }

    /**
     * Returns whether or not the service address of this node has been set.
     *
     * @return True if an address is set.
     */
    bool hasServiceAddress() { return _serviceAddress.get() != nullptr; }

    /**
     * Returns the service address of this node. This is attached by the network
     * layer, and should only ever be present in leaf nodes. Do not invoke this
     * unless {@link #hasServiceAddress()} is true, or you will deref null.
     *
     * @return The recipient address.
     */
    IServiceAddress &getServiceAddress() { return *_serviceAddress; }
    const IServiceAddress &getServiceAddress() const { return *_serviceAddress; }

    /**
     * Sets the service address of this node. This is called by the network
     * layer as this calls its {@link Network#resolveRecipient(RoutingNode)}
     * method.
     *
     * @param serviceAddress The recipient address.
     */
    void setServiceAddress(IServiceAddress::UP serviceAddress) { _serviceAddress = std::move(serviceAddress); }

    void handleReply(Reply::UP reply) override;
};

} // namespace mbus

