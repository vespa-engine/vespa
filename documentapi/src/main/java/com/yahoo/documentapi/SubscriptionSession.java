// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

/**
 * This class provides document <i>subscription</i> - accessing document changes to a
 * document repository.
 *
 * @author <a href="mailto:bratseth@yahoo-inc.com">Jon Bratseth</a>
 */
public interface SubscriptionSession extends Session {

    /**
     * Closes this subscription session without closing the subscription
     * registered on the document repository.
     * The same subscription can be accessed later by another subscription session.
     */
    public void close();

}
