// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.shared;

import com.yahoo.jdisc.SharedResource;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Result;

/**
 * @author Simon Thoresen Hult
 */
public interface ClientSession extends SharedResource {

    Result sendMessage(Message msg);
}
