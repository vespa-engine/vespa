// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

/**
 * This class provides document <i>subscription</i> - accessing document changes to a
 * document repository.
 *
 * @author bratseth
 */
public interface SubscriptionSession extends Session {

    /**
     * Closes this subscription session without closing the subscription
     * registered on the document repository.
     * The same subscription can be accessed later by another subscription session.
     */
    public void close();

}
