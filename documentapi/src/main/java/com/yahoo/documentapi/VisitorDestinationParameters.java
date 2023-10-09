// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

/**
 * Parameters for creating or opening a visitor destination session.
 *
 * @author <a href="mailto:thomasg@yahoo-inc.com">Thomas Gundersen</a>
 */
public class VisitorDestinationParameters extends Parameters {
    private String sessionName;
    private VisitorDataHandler dataHandler;

    /**
     * Creates visitor destination parameters from the supplied parameters.
     *
     * @param sessionName The name of the destination session.
     * @param handler The data handler.
     */
    public VisitorDestinationParameters(String sessionName, VisitorDataHandler handler) {
        this.sessionName = sessionName;
        dataHandler = handler;
    }

    /** @return the name of the destination session */
    public String getSessionName() { return sessionName; };

    /** @return the data handler */
    public VisitorDataHandler getDataHandler() { return dataHandler; };
}
