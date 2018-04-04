// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "routingnodeiterator.h"
#include <vespa/messagebus/context.h>
#include <vespa/slobrok/imirrorapi.h>
#include <set>

namespace mbus {

class RoutingNode;
class PolicyDirective;
class MessageBus;
class Message;
class Error;

/**
 * This context object is what is seen by {@link RoutingPolicy} when doing both select() and merge(). It
 * contains the necessary accessors to everything a policy is expected to need. An instance of this is created
 * for every {@link RoutingNode} that contains a policy.
 */
class RoutingContext {
private:
    RoutingNode       &_node;
    uint32_t           _directive;
    std::set<uint32_t> _consumableErrors;
    bool               _selectOnRetry;
    Context            _context;

public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<RoutingContext> UP;

    /**
     * Constructs a new routing context for a given routing node and hop.
     *
     * @param node      The owning routing node.
     * @param directive The index to the policy directive of the hop.
     */
    RoutingContext(RoutingNode &node, uint32_t directive);

    /**
     * Returns whether or not this hop has any configured recipients.
     *
     * @return True if there is at least one recipient.
     */
    bool hasRecipients() const;

    /**
     * Returns the number of configured recipients for this hop.
     *
     * @return The recipient count.
     */
    uint32_t getNumRecipients() const;

    /**
     * Returns the configured recipient at the given index.
     *
     * @param idx The index of the recipient to return.
     * @return The reipient at the given index.
     */
    const Route &getRecipient(uint32_t idx) const;

    /**
     * Returns all configured recipients for this hop.
     *
     * @return An unmodifiable list of recipients.
     */
    const std::vector<Route> &getAllRecipients() const;

    /**
     * Returns a list of all configured recipients whose first hop matches this.
     *
     * @param ret The list to add matched recipients to.
     */
    void getMatchedRecipients(std::vector<Route> &ret) const;

    /**
     * Returns whether or not the policy is required to reselect if resending occurs.
     *
     * @return True to invoke {@link RoutingPolicy#select(RoutingContext)} on resend.
     */
    bool getSelectOnRetry() const;

    /**
     * Sets whether or not the policy is required to reselect if resending occurs.
     *
     * @param selectOnRetry The value to set.
     * @return This, to allow chaining.
     */
    RoutingContext &setSelectOnRetry(bool selectOnRetry);

    /**
     * Returns the route that contains the routing policy that spawned this.
     *
     * @return The route.
     */
    const Route &getRoute() const;

    /**
     * Returns the hop that contains the routing policy that spawned this.
     *
     * @return The hop.
     */
    const Hop &getHop() const;

    /**
     * Returns the index of the hop directive that spawned this.
     *
     * @return The directive index.
     */
    uint32_t getDirectiveIndex() const;

    /**
     * Returns the policy directive that spawned this.
     *
     * @return The directive object.
     */
    const PolicyDirective &getDirective() const;

    /**
     * Returns the part of the route string that precedes the active policy directive. This is the same as calling
     * {@link this#getHop()}.getPrefix({@link this#getDirectiveIndex()}).
     *
     * @return The hop prefix.
     */
    string getHopPrefix() const;

    /**
     * Returns the remainder of the route string immediately following the active policy directive. This is the same as
     * calling {@link this#getHop()}.getSuffix({@link this#getDirectiveIndex()}).
     *
     * @return The hop suffix.
     */
    string getHopSuffix() const;

    /**
     * Returns the routing specific context object.
     *
     * @return The context.
     */
    Context &getContext();

    /**
     * Returns a const reference to the routing specific context object.
     *
     * @return The context.
     */
    const Context &getContext() const;

    /**
     * Sets a routing specific context object that will be available at merge().
     *
     * @param context An arbitrary object.
     * @return This, to allow chaining.
     */
    RoutingContext &setContext(const Context &ctx);

    /**
     * Returns the message being routed.
     *
     * @return The message.
     */
    const Message &getMessage() const;

    /**
     * Adds a string to the trace of the message being routed.
     *
     * @param level The level of the trace note.
     * @param note  The note to add.
     */
    void trace(uint32_t level, const string &note);

    /**
     * Returns whether or not a reply is available.
     *
     * @return True if a reply is set.
     */
    bool hasReply() const;

    /**
     * Returns the reply generated by the associated routing policy.
     *
     * @return The reply.
     */
    const Reply &getReply() const;

    /**
     * Sets the reply generated by the associated routing policy.
     *
     * @param reply The reply to set.
     * @return This, to allow chaining.
     */
    RoutingContext &setReply(std::unique_ptr<Reply> reply);

    /**
     * This is a convenience method to call {@link #setError(Error)}.
     *
     * @param code The code of the error to set.
     * @param msg  The message of the error to set.
     * @return This, to allow chaining.
     */
    RoutingContext &setError(uint32_t code, const string &msg);

    /**
     * This is a convenience method to assign an {@link EmptyReply} containing a single error to this. This also fiddles
     * with the trace object so that the error gets written to it.
     *
     * @param err The error to set.
     * @return This, to allow chaining.
     * @see #setReply(Reply)
     */
    RoutingContext &setError(const Error &err);

    /**
     * Returns the message bus instance on which this is running.
     *
     * @return The message bus.
     */
    MessageBus &getMessageBus();

    /**
     * Returns whether or not the owning routing node has any child nodes.
     *
     * @return True if there is at least one child.
     */
    bool hasChildren() const;

    /**
     * Returns the number of children the owning routing node has.
     *
     * @return The child count.
     */
    uint32_t getNumChildren() const;

    /**
     * Returns an iterator for the child routing nodes.
     *
     * @return The iterator.
     */
    RoutingNodeIterator getChildIterator();

    /**
     * Adds a child routing context to this based on a given route. This is the typical entry point a policy will use to
     * select recipients during a {@link RoutingPolicy#select(RoutingContext)} invokation.
     *
     * @param route The route to contain in the child context.
     */
    void addChild(Route route);

    /**
     * This is a convenience method to more easily add a list of children to this. It will simply call the {@link
     * this#addChild} method for each element in the list.
     *
     * @param routes A list of routes to add as children.
     */
    void addChildren(std::vector<Route> routes);

    /**
     * Returns the local mirror of the system's name server.
     *
     * @return The mirror api.
     */
    const slobrok::api::IMirrorAPI &getMirror() const;

    /**
     * Adds the given error code to the list of codes that the associated routing policy <u>may</u> consume. This is
     * used to verify whether or not a resolved routing tree can succeed if sent. Because verification is only done
     * before sending, the error types that must be added here are only those that can be generated by message bus
     * itself.
     *
     * @param errorCode The code that might be consumed.
     * @see RoutingNode#hasUnconsumedErrors()
     * @see com.yahoo.messagebus.ErrorCode
     */
    void addConsumableError(uint32_t errorCode);

    /**
     * Returns whether or not the given error code <u>may</u> be consumed by the associated routing policy.
     *
     * @param errorCode The code to check.
     * @return True if the code may be consumed.
     * @see this#addConsumableError(int)
     */
    bool isConsumableError(uint32_t errorCode);
};

} // namespace mbus

