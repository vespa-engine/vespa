// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network;

import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Protocol;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.text.Utf8Array;
import com.yahoo.text.Utf8String;

/**
 * A network owner is the object that instantiates and uses a network. The API to send messages
 * across the network is part of the Network interface, whereas this interface exposes the required
 * functionality of a network owner to be able to decode and deliver incoming messages.
 *
 * @author havardpe
 */
public interface NetworkOwner {

    /**
     * All messages are sent across the network with its accompanying protocol name so that it can be decoded at the
     * receiving end. The network queries its owner through this function to resolve the protocol from its name.
     *
     * @param name The name of the protocol to return.
     * @return The named protocol.
     */
    public Protocol getProtocol(Utf8Array name);

    /**
     * All messages that arrive in the network layer is passed to its owner through this function.
     *
     * @param message The message that just arrived from the network.
     * @param session The name of the session that is the recipient of the request.
     */
    public void deliverMessage(Message message, String session);

    /**
     * All replies that arrive in the network layer is passed through this to unentangle it from the network thread.
     *
     * @param reply   The reply that just arrived from the network.
     * @param handler The handler that is to receive the reply.
     */
    public void deliverReply(Reply reply, ReplyHandler handler);
}
