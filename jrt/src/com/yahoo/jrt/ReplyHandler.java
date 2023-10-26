// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


interface ReplyHandler {
    public Integer key();
    public void handleReply(Packet packet);
    public void handleConnectionDown();
}
