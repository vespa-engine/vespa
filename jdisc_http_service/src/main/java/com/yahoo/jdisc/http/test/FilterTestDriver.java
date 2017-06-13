// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.test;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.BindingRepository;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;

import java.io.IOException;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.yahoo.jdisc.http.test.ServerTestDriver.newFilterModule;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 *
 * TODO: dead code?
 */
public class FilterTestDriver {

    private final ServerTestDriver driver;
    private final MyRequestHandler requestHandler;

    private FilterTestDriver(ServerTestDriver driver, MyRequestHandler requestHandler) {
        this.driver = driver;
        this.requestHandler = requestHandler;
    }

    public boolean close() throws IOException {
        return driver.close();
    }

    public HttpRequest filterRequest(String request) throws IOException, TimeoutException, InterruptedException {
        driver.client().writeRequest(request);
        return (HttpRequest)requestHandler.exchanger.exchange(null, 60, TimeUnit.SECONDS);
    }

    public static FilterTestDriver newInstance(final BindingRepository<RequestFilter> requestFilters,
                                               final BindingRepository<ResponseFilter> responseFilters)
            throws IOException {
        MyRequestHandler handler = new MyRequestHandler();
        return new FilterTestDriver(ServerTestDriver.newInstance(handler,
                                                                 newFilterModule(requestFilters, responseFilters)),
                                    handler);
    }

    private static class MyRequestHandler extends AbstractRequestHandler {

        final Exchanger<Request> exchanger = new Exchanger<>();

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            ResponseDispatch.newInstance(Response.Status.OK).dispatch(handler);
            try {
                exchanger.exchange(request);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
