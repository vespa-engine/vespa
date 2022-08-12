// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import com.yahoo.vespa.config.protocol.Payload;

import java.time.Duration;

/**
 * For unit testing
 *
 * @author hmusum
 */
public class MockConnection implements ConnectionPool, Connection {

    private Request lastRequest;
    private final ResponseHandler responseHandler;
    private int numberOfRequests = 0;

    private final int numSpecs;

    public MockConnection() {
        this(new OKResponseHandler());
    }

    public MockConnection(ResponseHandler responseHandler) {
        this(responseHandler, 1);
    }

    public MockConnection(ResponseHandler responseHandler, int numSpecs) {
        this.responseHandler = responseHandler;
        this.numSpecs = numSpecs;
    }

    @Override
    public void invokeAsync(Request request, Duration jrtTimeout, RequestWaiter requestWaiter) {
        numberOfRequests++;
        lastRequest = request;
        responseHandler.handle(request, requestWaiter);
    }

    @Override
    public void invokeSync(Request request, Duration jrtTimeout) {
        numberOfRequests++;
        lastRequest = request;
    }

    @Override
    public String getAddress() {
        return null;
    }

    @Override
    public void close() {}

    @Override
    public Connection getCurrent() {
        return this;
    }

    @Override
    public Connection switchConnection(Connection connection) { return this; }

    @Override
    public int getSize() {
        return numSpecs;
    }

    public int getNumberOfRequests() {
        return numberOfRequests;
    }

    public Request getRequest() {
        return lastRequest;
    }

    static class OKResponseHandler extends AbstractResponseHandler {

        long generation = 1;

        protected void createResponse(Request request) {
            JRTServerConfigRequestV3 jrtReq = JRTServerConfigRequestV3.createFromRequest(request);
            Payload payload = Payload.from(ConfigPayload.empty());
            jrtReq.addOkResponse(payload, generation, false, PayloadChecksums.fromPayload(payload));
            generation++;
        }

    }

    public interface ResponseHandler {

        void handle(Request request, RequestWaiter requestWaiter);

    }

    public abstract static class AbstractResponseHandler implements ResponseHandler {

        @Override
        public void handle(Request request, RequestWaiter requestWaiter) {
            createResponse(request);
            requestWaiter.handleRequestDone(request);
        }

        protected abstract void createResponse(Request request);

    }

}
