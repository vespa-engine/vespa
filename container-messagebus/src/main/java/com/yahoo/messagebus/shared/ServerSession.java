// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.shared;

import com.yahoo.jdisc.SharedResource;
import com.yahoo.messagebus.Connectable;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.Reply;

/**
 * @author Simon Thoresen Hult
 */
public interface ServerSession extends SharedResource, Connectable {

    MessageHandler getMessageHandler();

    void setMessageHandler(MessageHandler msgHandler);

    void sendReply(Reply reply);

    String connectionSpec();

    String name();

}
