// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import com.yahoo.vespa.config.protocol.Payload;
import com.yahoo.vespa.config.util.ConfigUtils;

/**
 * For unit testing
 *
 * @author <a href="mailto:musum@yahoo-inc.com">Harald Musum</a>
 * @since 5.1.11
 */
public class MockConnection extends com.yahoo.config.subscription.impl.MockConnection {

    public MockConnection(MapBackedConfigSource configSource) {
        this(new ProxyResponseHandler(configSource));
    }

    public MockConnection(ResponseHandler responseHandler) {
        super(responseHandler);
    }

    static class ProxyResponseHandler implements ResponseHandler {
        private RequestWaiter requestWaiter;
        private Request request;
        private final MapBackedConfigSource configSource;

        protected ProxyResponseHandler(MapBackedConfigSource configSource) {
            this.configSource = configSource;
        }

        @Override
        public RequestWaiter requestWaiter() {
            return requestWaiter;
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public ResponseHandler requestWaiter(RequestWaiter requestWaiter) {
            this.requestWaiter = requestWaiter;
            return this;
        }

        @Override
        public ResponseHandler request(Request request) {
            this.request = request;
            return this;
        }

        @Override
        public void run() {
            if (request.isError()) {
                System.out.println("Returning error response");
                createErrorResponse();
            } else {
                System.out.println("Returning OK response");
                createOkResponse();
            }
            requestWaiter.handleRequestDone(request);
        }

        protected void createOkResponse() {
            JRTServerConfigRequestV3 jrtReq = JRTServerConfigRequestV3.createFromRequest(request);
            long generation = 1;
            RawConfig config = configSource.getConfig(jrtReq.getConfigKey());
            if (config == null || config.getPayload() == null) {
                throw new RuntimeException("No config for " + jrtReq.getConfigKey() + " found");
            }
            Payload payload = config.getPayload();
            jrtReq.addOkResponse(payload, generation, ConfigUtils.getMd5(payload.getData()));
        }

        protected void createErrorResponse() {
            JRTServerConfigRequestV3 jrtReq = JRTServerConfigRequestV3.createFromRequest(request);
            jrtReq.addErrorResponse(request.errorCode(), request.errorMessage());
        }
    }
}
