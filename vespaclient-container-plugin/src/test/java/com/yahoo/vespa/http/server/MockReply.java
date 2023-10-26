// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.messagebus.Reply;
import com.yahoo.text.Utf8String;

/**
 * Minimal reply simulator.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
class MockReply extends Reply {

    Object context;

    public MockReply(Object context) {
        this.context = context;
    }

    @Override
    public Utf8String getProtocol() {
        return null;
    }

    @Override
    public int getType() {
        return 0;
    }

    @Override
    public Object getContext() {
        return context;
    }

}
