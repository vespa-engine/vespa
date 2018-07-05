// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

/**
 * This class defines the {@link Trace} levels used by message bus.
 *
 * @author Simon Thoresen Hult
 */
public final class TraceLevel {

    /**
     * Traces whenever an Error is added to a Reply.
     */
    public static final int ERROR = 1;

    /**
     * Traces sending and receiving messages and replies on network level.
     */
    public static final int SEND_RECEIVE = 4;

    /**
     * Traces splitting messages and merging replies.
     */
    public static final int SPLIT_MERGE = 5;

    /**
     * Traces information about which internal components are processing a routable.
     */
    public static final int COMPONENT = 6;
}
