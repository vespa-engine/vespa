// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus;

/**
 * This class defines a common interface for message bus sessions.
 *
 * @author Simon Thoresen Hult
 */
public interface MessageBusSession {

    /**
     * Returns the route to send all messages to when sending through this session.
     *
     * @return The route string.
     */
    public String getRoute();

    /**
     * Sets the route to send all messages to when sending through this session.
     *
     * @param route The route string.
     */
    public void setRoute(String route);

    /**
     * Returns the trace level used when sending messages through this session.
     *
     * @return The trace level.
     */
    public int getTraceLevel();

    /**
     * Sets the trace level used when sending messages through this session.
     *
     * @param traceLevel The trace level to set.
     */
    public void setTraceLevel(int traceLevel);
}
