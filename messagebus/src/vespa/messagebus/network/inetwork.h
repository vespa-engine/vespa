// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <vespa/slobrok/imirrorapi.h>
#include "inetworkowner.h"

namespace mbus {

class RoutingNode;

/**
 * This interface is used to hide away the implementation details of the network
 * code from the rest of the messagebus implementation. The methods defined in
 * this interface is intended to be invoked by MessageBus and not by the
 * application. The only responsibility of the application is to instantiate an
 * INetwork implementing object, give it to the MessageBus constructor and make
 * sure it outlives the MessageBus object.
 */
class INetwork {
protected:
    INetwork() = default;
public:
    INetwork(const INetwork &) = delete;
    INetwork & operator = (const INetwork &) = delete;
    /**
     * Destructor. Frees any allocated resources.
     */
    virtual ~INetwork() { }

    /**
     * Attach the network layer to the given owner. This method should be
     * invoked before starting the network. This method is invoked by the
     * MessageBus constructor.
     *
     * @param owner owner of the network
     */
    virtual void attach(INetworkOwner &owner) = 0;

    /**
     * Returns a string that represents the connection specs of this network. It
     * is in not a complete address since it know nothing of the sessions that
     * run on it.
     *
     * @return The connection string.
     */
    virtual const string getConnectionSpec() const = 0;

    /**
     * Start this network. This method should be invoked after the attach method
     * and before starting to use the network. This method is invoked by the
     * MessageBus constructor.
     *
     * @return true if the network could be started
     */
    virtual bool start() = 0;

    /**
     * Waits for at most the given number of seconds for all dependencies to
     * become ready.
     *
     * @param seconds The timeout.
     * @return True if ready.
     */
    virtual bool waitUntilReady(seconds timeout) const = 0;

    /**
     * Register a session name with the network layer. This will make the
     * session visible to other nodes.
     *
     * @param session the session name
     */
    virtual void registerSession(const string &session) = 0;

    /**
     * Unregister a session name with the network layer. This will make the
     * session unavailable for other nodes.
     *
     * @param session session name
     */
    virtual void unregisterSession(const string &session) = 0;

    /**
     * Resolves the service address of the recipient referenced by the given
     * routing node. If a recipient can not be resolved, this method tags the
     * node with an error. If this method succeeds, you need to invoke {@link
     * #freeServiceAddress(RoutingNode)} once you are done with the service
     * address.
     *
     * @param recipient The node whose service address to allocate.
     * @return True if a service address was allocated.
     */
    virtual bool allocServiceAddress(RoutingNode &recipient) = 0;

    /**
     * Frees the service address from the given routing node. This allows the
     * network layer to track and close connections as required.
     *
     * @param recipient The node whose service address to free.
     */
    virtual void freeServiceAddress(RoutingNode &recipient) = 0;

    /**
     * Send a message to the given recipients. A {@link RoutingNode} contains
     * all the necessary context for sending.
     *
     * @param msg        The message to send.
     * @param recipients A list of routing leaf nodes resolved for the message.
     */
    virtual void send(const Message &msg, const std::vector<RoutingNode*> &recipients) = 0;

    /**
     * Synchronize with internal threads. This method will handshake with all
     * internal threads. This has the implicit effect of waiting for all active
     * callbacks. Note that this method should never be invoked from a callback
     * since that would make the thread wait for itself... forever. This method
     * is typically used to untangle during session destruction. If this method
     * is invoked after the shutdown method is invoked it will never return.
     */
    virtual void sync() = 0;

    /**
     * Shut down this network. This method will block until the network has been
     * properly shut down. After the network has been shut down, this method
     * will also flush out the ghosts in the system. In this case, the ghosts
     * are the replies waiting to be delivered in a separate thread context, but
     * having no real end-points since all sessions must be destructed before
     * destructing the MessageBus object. This method is invoked by the
     * MessageBus destructor.
     */
    virtual void shutdown() = 0;

    /**
     * If anything is posted to the network after {@link #shutdown()} is called,
     * there is no thread alive that can generate a reply. By calling this method
     * you are effectively flushing all ghosts from the network back to their
     * respective owners.
     */
    virtual void postShutdownHook() = 0;

    /**
     * Returns a reference to a name server mirror.
     *
     * @return The mirror object.
     */
    virtual const slobrok::api::IMirrorAPI &getMirror() const = 0;
};

} // namespace mbus

