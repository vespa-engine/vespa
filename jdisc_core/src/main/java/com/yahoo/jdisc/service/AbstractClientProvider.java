// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.service;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;

/**
 * <p>This is a convenient parent class for {@link ClientProvider} with default implementations for all but the
 * essential {@link #handleRequest(Request, ResponseHandler)} method.</p>
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public abstract class AbstractClientProvider extends AbstractRequestHandler implements ClientProvider {

    @Override
    public void start() {

    }
}
