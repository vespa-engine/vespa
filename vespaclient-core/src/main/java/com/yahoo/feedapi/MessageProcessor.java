// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.messagebus.Message;

public interface MessageProcessor {
    public void process(Message m);
}
