// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.Supervisor;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import com.yahoo.vespa.config.protocol.Payload;
import com.yahoo.vespa.config.util.ConfigUtils;

/**
 * For unit testing
 *
 * @author hmusum
 * @since 5.1.11
 */
public class MockConnection implements ConnectionPool, com.yahoo.vespa.config.Connection {

    private Request lastRequest;
    private final ResponseHandler responseHandler;
    private int numberOfRequests = 0;

    public int getNumberOfFailovers() {
        return numberOfFailovers;
    }

    private int numberOfFailovers = 0;
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
    public void invokeAsync(Request request, double jrtTimeout, RequestWaiter requestWaiter) {
        numberOfRequests++;
        lastRequest = request;
        responseHandler.requestWaiter(requestWaiter).request(request);
        Thread t = new Thread(responseHandler);
        t.setDaemon(true);
        t.run();
    }

    @Override
    public void invokeSync(Request request, double jrtTimeout) {
        numberOfRequests++;
        lastRequest = request;
    }

    @Override
    public void setError(int errorCode) {
        numberOfFailovers++;
    }

    @Override
    public void setSuccess() {
        numberOfFailovers = 0;
    }

    @Override
    public String getAddress() {
        return null;
    }

    @Override
    public void close() {}

    @Override
    public void setError(Connection connection, int errorCode) {
        connection.setError(errorCode);
    }

    @Override
    public Connection getCurrent() {
        return this;
    }

    @Override
    public Connection setNewCurrentConnection() {
        return this;
    }

    @Override
    public int getSize() {
        return numSpecs;
    }

    @Override
    public Supervisor getSupervisor() {
        return null;
    }

    public int getNumberOfRequests() {
        return numberOfRequests;
    }

    public Request getRequest() {
        return lastRequest;
    }

    static class OKResponseHandler extends AbstractResponseHandler {

        protected void createResponse() {
            JRTServerConfigRequestV3 jrtReq = JRTServerConfigRequestV3.createFromRequest(request);
            Payload payload = Payload.from(ConfigPayload.empty());
            long generation = 1;
            jrtReq.addOkResponse(payload, generation, false, ConfigUtils.getMd5(payload.getData()));
        }

    }

    public interface ResponseHandler extends Runnable {

        RequestWaiter requestWaiter();

        Request request();

        ResponseHandler requestWaiter(RequestWaiter requestWaiter);

        ResponseHandler request(Request request);
    }

    public abstract static class AbstractResponseHandler implements ResponseHandler {

        private RequestWaiter requestWaiter;
        protected Request request;

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
            createResponse();
            requestWaiter.handleRequestDone(request);
        }

        protected abstract void createResponse();
    }

}
