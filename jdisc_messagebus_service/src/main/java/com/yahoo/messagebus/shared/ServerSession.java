// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.shared;

import com.yahoo.jdisc.SharedResource;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.Reply;

/**
 * @author Simon Thoresen Hult
 */
public interface ServerSession extends SharedResource {

    public MessageHandler getMessageHandler();

    public void setMessageHandler(MessageHandler msgHandler);

    public void sendReply(Reply reply);

    public String connectionSpec();

    public String name();
}
