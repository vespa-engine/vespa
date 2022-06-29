// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "destinationsession.h"
#include "iconfighandler.h"
#include "idiscardhandler.h"
#include "intermediatesession.h"
#include "messagebusparams.h"
#include "protocolset.h"
#include "sourcesession.h"
#include <vespa/messagebus/network/inetworkowner.h>
#include <vespa/messagebus/routing/routingspec.h>
#include <map>
#include <string>
#include <atomic>

namespace mbus {

class SendProxy;
class Messenger;
class Resender;
class INetwork;
class RoutingTable;
class ProtocolRepository;

/**
 * A MessageBus object combined with an INetwork implementation makes up the central part of a messagebus setup. It is
 * important that the application destructs all sessions before destructing the MessageBus object. Also, the INetwork
 * object should be destructed after the MessageBus object.
 */
class MessageBus : public IMessageHandler,
                   public IConfigHandler,
                   public IDiscardHandler,
                   public INetworkOwner,
                   public IReplyHandler
{
private:
    using RoutingTableSP = std::shared_ptr<RoutingTable>;
    INetwork                            &_network;
    std::mutex                           _lock;
    std::map<string, RoutingTableSP>     _routingTables;
    std::map<string, IMessageHandler*>   _sessions;
    std::unique_ptr<ProtocolRepository>  _protocolRepository;
    std::unique_ptr<Messenger>           _msn;
    std::unique_ptr<Resender>            _resender;
    std::atomic<uint32_t>                _maxPendingCount;
    std::atomic<uint32_t>                _maxPendingSize;
    std::atomic<uint32_t>                _pendingCount;
    std::atomic<uint32_t>                _pendingSize;

    /**
     * This method performs the common constructor tasks.
     *
     * @param params The parameters to base setup on.
     */
    void setup(const MessageBusParams &params);

    /**
     * This method handles choking input data so that message bus does not blindly accept everything. This prevents an
     * application running out-of-memory in case it fail to choke input data itself. If this method returns false, it
     * means that it should be rejected.
     *
     * @param msg The message to count.
     * @return True if the message was accepted.
     */
    bool checkPending(Message &msg);

    /**
     * Constructs and schedules a Reply containing an error to the handler of the given Message.
     *
     * @param msg     The message to reply to.
     * @param errCode The code of the error to set.
     * @param errMsg  The message of the error to set.
     */
    void deliverError(Message::UP msg, uint32_t errCode, const string &errMsg);

public:
    /**
     * Convenience constructor that proxies {@link this#MessageBus(Network, MessageBusParams)} by adding the given
     * protocols to a default {@link MessageBusParams} object.
     *
     * @param network   The network to associate with.
     * @param protocols An array of protocols to register.
     */
    MessageBus(INetwork &net, ProtocolSet protocols);

    /**
     * Constructs an instance of message bus. This requires a network object that it will associate with. This
     * assignment may not change during the lifetime of this message bus.
     *
     * @param network The network to associate with.
     * @param params  The parameters that controls this bus.
     */
    MessageBus(INetwork &net, const MessageBusParams &params);

    /**
     * Destruct. The destructor will shut down the underlying INetwork object.
     **/
    virtual ~MessageBus();

    /**
     * This is a convenience method to call {@link this#createSourceSession(SourceSessionParams)} with default
     * values for the {@link SourceSessionParams} object.
     *
     * @param handler The reply handler to receive the replies for the session.
     * @return The created session.
     */
    SourceSession::UP createSourceSession(IReplyHandler &handler);

    /**
     * This is a convenience method to call {@link this#createSourceSession(SourceSessionParams)} by first
     * assigning the reply handler to the parameter object.
     *
     * @param handler The reply handler to receive the replies for the session.
     * @param params  The parameters to control the session.
     * @return The created session.
     */
    SourceSession::UP createSourceSession(IReplyHandler &handler,
                                          const SourceSessionParams &params);

    /**
     * Creates a source session on top of this message bus.
     *
     * @param params The parameters to control the session.
     * @return The created session.
     */
    SourceSession::UP createSourceSession(const SourceSessionParams &params);

    /**
     * This is a convenience method to call {@link this#createIntermediateSession(IntermediateSessionParams)} with
     * default values for the {@link IntermediateSessionParams} object.
     *
     * @param name          The local unique name for the created session.
     * @param broadcastName Whether or not to broadcast this session's name on the network.
     * @param msgHandler    The handler to receive the messages for the session.
     * @param replyHandler  The handler to received the replies for the session.
     * @return The created session.
     */
    IntermediateSession::UP createIntermediateSession(const string &name,
                                                      bool broadcastName,
                                                      IMessageHandler &msgHandler,
                                                      IReplyHandler &replyHandler);

    /**
     * Creates an intermediate session on top of this message bus using the given handlers and parameter object.
     *
     * @param params The parameters to control the session.
     * @return The created session.
     */
    IntermediateSession::UP createIntermediateSession(const IntermediateSessionParams &params);

    /**
     * This is a convenience method to call {@link this#createDestinationSession(DestinationSessionParams)} with default
     * values for the {@link DestinationSessionParams} object.
     *
     * @param name          The local unique name for the created session.
     * @param broadcastName Whether or not to broadcast this session's name on the network.
     * @param handler       The handler to receive the messages for the session.
     * @return The created session.
     */
    DestinationSession::UP createDestinationSession(const string &name,
                                                    bool broadcastName,
                                                    IMessageHandler &handler);

    /**
     * Creates a destination session on top of this message bus using the given handlers and parameter object.
     *
     * @param params The parameters to control the session.
     * @return The created session.
     */
    DestinationSession::UP createDestinationSession(const DestinationSessionParams &params);

    /**
     * Unregister a session. This method is invoked by session destructors to ensure that no more Message objects are
     * delivered and that the session name is removed from the network naming service. The sync method can be invoked
     * after invoking this one to ensure that no callbacks are active.
     *
     * @param sessionName name of the session to unregister
     **/
    void unregisterSession(const string &sessionName);

    /**
     * Obtain the routing table for the given protocol. If the appropriate routing table could not be found, a shared
     * pointer to 0 is returned.
     *
     * @return shared pointer to routing table
     * @param protocol the protocol name
     **/
    RoutingTableSP getRoutingTable(const string &protocol);

    /**
     * Returns a routing policy that corresponds to the argument protocol name, policy name and policy parameter. This
     * will cache reuse all policies as soon as they are first requested.
     *
     * @param protocol    The name of the protocol to invoke {@link Protocol#createPolicy(String,String)} on.
     * @param policyName  The name of the routing policy to retrieve.
     * @param policyParam The parameter for the routing policy to retrieve.
     * @return A corresponding routing policy, or null.
     */
    IRoutingPolicy::SP getRoutingPolicy(const string &protocol, const string &policyName,
                                        const string &policyParam);

    /**
     * Synchronize with internal threads. This method will handshake with all internal threads. This has the implicit
     * effect of waiting for all active callbacks. Note that this method should never be invoked from a callback since
     * that would make the thread wait for itself... forever. This method is typically used to untangle during session
     * destruction.
     **/
    void sync();

    /**
     * Returns the resender that is running within this message bus.
     *
     * @return The resender.
     */
    Resender *getResender() { return _resender.get(); }

    /**
     * Returns the number of messages received that have not been replied to yet.
     *
     * @return The pending count.
     */
    uint32_t getPendingCount() const { return _pendingCount; }

    /**
     * Returns the size of messages received that have not been replied to yet.
     *
     * @return The pending size.
     */
    uint32_t getPendingSize() const { return _pendingSize; }

    /**
     * Sets the maximum number of messages that can be received without being replied to yet.
     *
     * @param maxCount The max count.
     */
    void setMaxPendingCount(uint32_t maxCount);

    /**
     * Gets maximum number of messages that can be received without being
     * replied to yet.
     */
    uint32_t getMaxPendingCount() const noexcept {
        return _maxPendingCount.load(std::memory_order_relaxed);
    }

    /**
     * Sets the maximum size of messages that can be received without being replied to yet.
     *
     * @param maxSize The max size.
     */
    void setMaxPendingSize(uint32_t maxSize);

    /**
     * Gets maximum combined size of messages that can be received without
     * being replied to yet.
     */
    uint32_t getMaxPendingSize() const noexcept {
        return _maxPendingSize.load(std::memory_order_relaxed);
    }

    /**
     * Adds a protocol to the internal repository of protocols, replacing any previous instance of the
     * protocol and clearing the associated routing policy cache.
     *
     * @param protocol The protocol to add.
     */
    IProtocol::SP putProtocol(const IProtocol::SP & protocol);

    /**
     * Returns the connection spec string for the network layer of this message bus. This is merely a proxy of
     * the same function in the network layer.
     *
     * @return The connection string.
     */
    string getConnectionSpec() const;

    /**
     * Provide access to the underlying {@link Messenger} object.
     *
     * @return The underlying {@link Messenger} object.
     */
    Messenger & getMessenger() { return *_msn; }

    // Implements IReplyHandler.
    void handleReply(Reply::UP reply) override;

    // Implements IDiscardHandler.
    void handleDiscard(Context ctx) override;

    // Implements IMessageHandler.
    void handleMessage(Message::UP msg) override;

    // Implements IConfigHandler.
    bool setupRouting(const RoutingSpec &spec) override;

    // Implements INetworkOwner.
    IProtocol * getProtocol(const string &name) override;

    // Implements INetworkOwner.
    void deliverMessage(Message::UP msg, const string &session) override;

    // Implements INetworkOwner.
    void deliverReply(Reply::UP reply, IReplyHandler &handler) override;
};

} // namespace mbus

