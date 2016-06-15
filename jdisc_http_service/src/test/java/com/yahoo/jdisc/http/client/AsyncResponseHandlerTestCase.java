// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.AsyncHandler;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ReadableContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.test.NonWorkingRequest;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class AsyncResponseHandlerTestCase {

    @Test(enabled = false)
    public void requireThatOnThrowableAbortsHandler() throws Exception {
        AsyncResponseHandler handler = new AsyncResponseHandler(NonWorkingRequest.newInstance("http://localhost/"),
                                                                new MyResponseHandler(), new MyMetric(),
                                                                new Metric.Context() { });
        handler.onThrowable(new Throwable());
        assertEquals(AsyncHandler.STATE.ABORT, handler.onStatusReceived(null));
        assertEquals(AsyncHandler.STATE.ABORT, handler.onHeadersReceived(null));
        assertEquals(AsyncHandler.STATE.ABORT, handler.onBodyPartReceived(null));
        assertNull(handler.onCompleted());
    }

    private static class MyResponseHandler implements ResponseHandler {

        @Override
        public ContentChannel handleResponse(Response response) {
            return new ReadableContentChannel();
        }
    }

    private static class MyMetric implements Metric {

        @Override
        public void set(String key, Number val, Context ctx) {

        }

        @Override
        public void add(String key, Number val, Context ctx) {

        }

        @Override
        public Context createContext(Map<String, ?> properties) {
            return null;
        }
    }
}
