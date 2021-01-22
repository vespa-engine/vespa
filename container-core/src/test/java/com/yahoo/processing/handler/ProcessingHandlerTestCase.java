// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.handler;

import com.google.common.util.concurrent.SettableFuture;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.container.protect.Error;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.processing.Processor;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.processors.RequestPropertyTracer;
import com.yahoo.processing.rendering.ProcessingRenderer;
import com.yahoo.processing.rendering.Renderer;
import com.yahoo.processing.request.ErrorMessage;
import com.yahoo.processing.response.Data;
import com.yahoo.processing.test.ProcessorLibrary;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.yahoo.jdisc.http.server.jetty.AccessLoggingRequestHandler.CONTEXT_KEY_ACCESS_LOG_ENTRY;
import static com.yahoo.processing.test.ProcessorLibrary.MapData;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Tests processing handler scenarios end to end.
 *
 * @author bratseth
 * @author gjoranv
 * @author Tony Vaagenes
 */
public class ProcessingHandlerTestCase {

    private static final String LOG_KEY = "Log-Key";
    private static final String LOG_VALUE = "Log-Value";

    private ProcessingTestDriver driver;

    private final Chain<Processor> defaultChain =
            new Chain<Processor>("default",
                    new ProcessorLibrary.StringDataListAdder("Item1", "Item2"),
                    new ProcessorLibrary.Trace("TraceMessage", 1),
                    new ProcessorLibrary.StringDataAdder("StringData.toString()"));

    private final Chain<Processor> simpleChain =
            new Chain<Processor>("simple",
                    new ProcessorLibrary.StringDataAdder("StringData.toString()"));

    private final Chain<Processor> logValueChain =
            new Chain<Processor>("log-value",
                    new ProcessorLibrary.LogValueAdder(LOG_KEY, LOG_VALUE));

    @After
    public void shutDown() {
        driver.close();
    }

    @Test
    public void processing_handler_stores_trace_log_values_in_the_access_log_entry() {
        driver = new ProcessingTestDriver(logValueChain);
        Request request = HttpRequest.newServerRequest(driver.jDiscDriver(), URI.create("http://localhost/?chain=log-value"), HttpRequest.Method.GET);
        AccessLogEntry entry = new AccessLogEntry();
        request.context().put(CONTEXT_KEY_ACCESS_LOG_ENTRY, entry);
        RequestHandlerTestDriver.MockResponseHandler responseHandler = new RequestHandlerTestDriver.MockResponseHandler();
        ContentChannel requestContent = request.connect(responseHandler);
        requestContent.write(ByteBuffer.allocate(0), null);
        requestContent.close(null);
        request.release();
        responseHandler.readAll();
        assertThat(entry.getKeyValues().get(LOG_KEY), is(List.of(LOG_VALUE)));
    }

    @Test
    public void testProcessingHandlerResolvesChains() throws Exception {
        List<Chain<Processor>> chains = new ArrayList<>();
        chains.add(defaultChain);
        chains.add(simpleChain);
        driver = new ProcessingTestDriver(chains);

        assertEquals(simpleChainResponse, driver.sendRequest("http://localhost/?chain=simple").readAll());
        assertEquals(defaultChainResponse, driver.sendRequest("http://localhost/?chain=default").readAll());
    }

    @Test
    public void testProcessingHandlerPropagatesRequestParametersAndContext() throws InterruptedException {
        List<Chain<Processor>> chains = new ArrayList<>();
        chains.add(new Chain<Processor>("default", new RequestPropertyTracer()));
        driver = new ProcessingTestDriver(chains);
        assertTrue("JDisc request context is propagated to properties()",
                driver.sendRequest("http://localhost/?chain=default&tracelevel=4").readAll().contains("context.contextVariable: '37'"));
    }

    @Test
    public void testProcessingHandlerOutputsTrace() throws Exception {
        List<Chain<Processor>> chains = new ArrayList<>();
        chains.add(defaultChain);
        driver = new ProcessingTestDriver(chains);

        assertEquals(trace1, driver.sendRequest("http://localhost/?tracelevel=1").readAll().substring(0, trace1.length()));
        assertEquals(trace1WithFullResult, driver.sendRequest("http://localhost/?tracelevel=1").readAll());
        assertEquals(trace4, driver.sendRequest("http://localhost/?tracelevel=4").readAll().substring(0, trace4.length()));
        assertEquals(trace5, driver.censorDigits(driver.sendRequest("http://localhost/?tracelevel=5").readAll().substring(0, trace5.length())));
        assertEquals(trace6, driver.censorDigits(driver.sendRequest("http://localhost/?tracelevel=6").readAll().substring(0, trace6.length())));
    }

    @Test
    public void testProcessingHandlerTransfersErrorsToHttpStatusCodesNoData() throws Exception {
        List<Chain<Processor>> chains = new ArrayList<>();
        chains.add(simpleChain);
        chains.add(new Chain<Processor>("moved_permanently", new ProcessorLibrary.ErrorAdder(new ErrorMessage(301,"Message"))));
        chains.add(new Chain<Processor>("unauthorized", new ProcessorLibrary.ErrorAdder(new ErrorMessage(401,"Message"))));
        chains.add(new Chain<Processor>("unauthorized_mapped", new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.UNAUTHORIZED.code,"Message"))));
        chains.add(new Chain<Processor>("forbidden", new ProcessorLibrary.ErrorAdder(new ErrorMessage(403,"Message"))));
        chains.add(new Chain<Processor>("forbidden_mapped", new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.FORBIDDEN.code,"Message"))));
        chains.add(new Chain<Processor>("not_found", new ProcessorLibrary.ErrorAdder(new ErrorMessage(404,"Message"))));
        chains.add(new Chain<Processor>("not_found_mapped", new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.NOT_FOUND.code,"Message"))));
        chains.add(new Chain<Processor>("too_many_requests", new ProcessorLibrary.ErrorAdder(new ErrorMessage(429,"Message"))));
        chains.add(new Chain<Processor>("bad_request", new ProcessorLibrary.ErrorAdder(new ErrorMessage(400,"Message"))));
        chains.add(new Chain<Processor>("bad_request_mapped", new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.BAD_REQUEST.code,"Message"))));
        chains.add(new Chain<Processor>("internal_server_error", new ProcessorLibrary.ErrorAdder(new ErrorMessage(500,"Message"))));
        chains.add(new Chain<Processor>("internal_server_error_mapped", new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.INTERNAL_SERVER_ERROR.code,"Message"))));
        chains.add(new Chain<Processor>("service_unavailable", new ProcessorLibrary.ErrorAdder(new ErrorMessage(503,"Message"))));
        chains.add(new Chain<Processor>("service_unavailable_mapped", new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.NO_BACKENDS_IN_SERVICE.code,"Message"))));
        chains.add(new Chain<Processor>("gateway_timeout", new ProcessorLibrary.ErrorAdder(new ErrorMessage(504,"Message"))));
        chains.add(new Chain<Processor>("gateway_timeout_mapped", new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.TIMEOUT.code,"Message"))));
        chains.add(new Chain<Processor>("bad_gateway", new ProcessorLibrary.ErrorAdder(new ErrorMessage(502,"Message"))));
        chains.add(new Chain<Processor>("bad_gateway_mapped", new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.BACKEND_COMMUNICATION_ERROR.code,"Message"))));
        chains.add(new Chain<Processor>("unknown_code", new ProcessorLibrary.ErrorAdder(new ErrorMessage(1234567,"Message"))));
        driver = new ProcessingTestDriver(chains);
        assertEqualStatus(200, "http://localhost/?chain=simple");
        assertEqualStatus(301, "http://localhost/?chain=moved_permanently");
        assertEqualStatus(401, "http://localhost/?chain=unauthorized");
        assertEqualStatus(401, "http://localhost/?chain=unauthorized_mapped");
        assertEqualStatus(403, "http://localhost/?chain=forbidden");
        assertEqualStatus(403, "http://localhost/?chain=forbidden_mapped");
        assertEqualStatus(404, "http://localhost/?chain=not_found");
        assertEqualStatus(404, "http://localhost/?chain=not_found_mapped");
        assertEqualStatus(429, "http://localhost/?chain=too_many_requests");
        assertEqualStatus(400, "http://localhost/?chain=bad_request");
        assertEqualStatus(400, "http://localhost/?chain=bad_request_mapped");
        assertEqualStatus(500, "http://localhost/?chain=internal_server_error");
        assertEqualStatus(500, "http://localhost/?chain=internal_server_error_mapped");
        assertEqualStatus(503, "http://localhost/?chain=service_unavailable");
        assertEqualStatus(503, "http://localhost/?chain=service_unavailable_mapped");
        assertEqualStatus(504, "http://localhost/?chain=gateway_timeout");
        assertEqualStatus(504, "http://localhost/?chain=gateway_timeout_mapped");
        assertEqualStatus(502, "http://localhost/?chain=bad_gateway");
        assertEqualStatus(503, "http://localhost/?chain=bad_gateway_mapped");
        assertEqualStatus(500, "http://localhost/?chain=unknown_code");
    }

    @Test
    public void testProcessingHandlerTransfersErrorsToHttpStatusCodesWithData() throws Exception {
        List<Chain<Processor>> chains = new ArrayList<>();
        chains.add(simpleChain);
        chains.add(new Chain<Processor>("moved_permanently", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(301,"Message"))));
        chains.add(new Chain<Processor>("unauthorized", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(401,"Message"))));
        chains.add(new Chain<Processor>("unauthorized_mapped", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.UNAUTHORIZED.code,"Message"))));
        chains.add(new Chain<Processor>("forbidden", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(403,"Message"))));
        chains.add(new Chain<Processor>("forbidden_mapped", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.FORBIDDEN.code,"Message"))));
        chains.add(new Chain<Processor>("not_found", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(404,"Message"))));
        chains.add(new Chain<Processor>("not_found_mapped", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.NOT_FOUND.code,"Message"))));
        chains.add(new Chain<Processor>("too_many_requests", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(429,"Message"))));
        chains.add(new Chain<Processor>("bad_request", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(400,"Message"))));
        chains.add(new Chain<Processor>("bad_request_mapped", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.BAD_REQUEST.code,"Message"))));
        chains.add(new Chain<Processor>("internal_server_error", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(500,"Message"))));
        chains.add(new Chain<Processor>("internal_server_error_mapped", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.INTERNAL_SERVER_ERROR.code,"Message"))));
        chains.add(new Chain<Processor>("service_unavailable", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(503,"Message"))));
        chains.add(new Chain<Processor>("service_unavailable_mapped", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.NO_BACKENDS_IN_SERVICE.code,"Message"))));
        chains.add(new Chain<Processor>("gateway_timeout", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(504,"Message"))));
        chains.add(new Chain<Processor>("gateway_timeout_mapped", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.TIMEOUT.code,"Message"))));
        chains.add(new Chain<Processor>("bad_gateway", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(502,"Message"))));
        chains.add(new Chain<Processor>("bad_gateway_mapped", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.BACKEND_COMMUNICATION_ERROR.code,"Message"))));
        chains.add(new Chain<Processor>("unknown_code", new ProcessorLibrary.StringDataAdder("Hello"), new ProcessorLibrary.ErrorAdder(new ErrorMessage(1234567,"Message"))));
        driver = new ProcessingTestDriver(chains);
        assertEqualStatus(200, "http://localhost/?chain=simple");
        assertEqualStatus(301, "http://localhost/?chain=moved_permanently");
        assertEqualStatus(401, "http://localhost/?chain=unauthorized");
        assertEqualStatus(401, "http://localhost/?chain=unauthorized_mapped");
        assertEqualStatus(403, "http://localhost/?chain=forbidden");
        assertEqualStatus(403, "http://localhost/?chain=forbidden_mapped");
        assertEqualStatus(404, "http://localhost/?chain=not_found");
        assertEqualStatus(404, "http://localhost/?chain=not_found_mapped");
        assertEqualStatus(429, "http://localhost/?chain=too_many_requests");
        assertEqualStatus(400, "http://localhost/?chain=bad_request");
        assertEqualStatus(400, "http://localhost/?chain=bad_request_mapped");
        assertEqualStatus(500, "http://localhost/?chain=internal_server_error");
        assertEqualStatus(500, "http://localhost/?chain=internal_server_error_mapped");
        assertEqualStatus(503, "http://localhost/?chain=service_unavailable");
        assertEqualStatus(200, "http://localhost/?chain=service_unavailable_mapped"); // as this didn't fail and this isn't a web service mapped code
        assertEqualStatus(504, "http://localhost/?chain=gateway_timeout");
        assertEqualStatus(200, "http://localhost/?chain=gateway_timeout_mapped");     // as this didn't fail and this isn't a web service mapped code
        assertEqualStatus(502, "http://localhost/?chain=bad_gateway");
        assertEqualStatus(200, "http://localhost/?chain=bad_gateway_mapped");         // as this didn't fail and this isn't a web service mapped code
        assertEqualStatus(200, "http://localhost/?chain=unknown_code");               // as this didn't fail and this isn't a web service mapped code
    }

    @Test
    public void testProcessorSetsResponseHeaders() throws InterruptedException {
        ProcessingTestDriver.MockResponseHandler responseHandler = null;
        try {
            Map<String,List<String>> responseHeaders = new HashMap<>();
            responseHeaders.put("foo", Collections.singletonList("fooValue"));
            responseHeaders.put("bar", Arrays.asList(new String[] { "barValue", "bazValue"}));

            Map<String,List<String>> otherResponseHeaders = new HashMap<>();
            otherResponseHeaders.put("foo", Collections.singletonList("fooValue2"));
            otherResponseHeaders.put("bax", Collections.singletonList("baxValue"));

            List<Chain<Processor>> chains = new ArrayList<>();
            chains.add(new Chain<Processor>("default",new ResponseHeaderSetter(responseHeaders),
                                                      new ResponseHeaderSetter(otherResponseHeaders)));
            driver = new ProcessingTestDriver(chains);
            responseHandler = driver.sendRequest("http://localhost/?chain=default").awaitResponse();
            Response response = responseHandler.getResponse();
            assertEquals("[fooValue2, fooValue]",response.headers().get("foo").toString());
            assertEquals("[barValue, bazValue]", response.headers().get("bar").toString());
            assertEquals("[baxValue]", response.headers().get("bax").toString());
            assertEquals("ResponseHeaders are not rendered", "{\"datalist\":[]}", responseHandler.read());
        }
        finally {
            if (responseHandler != null)
                responseHandler.readAll();
        }
    }

    @Test
    public void testResponseDataStatus() throws InterruptedException {
        ProcessingTestDriver.MockResponseHandler responseHandler = null;
        try {
            List<Chain<Processor>> chains = new ArrayList<>();
            chains.add(new Chain<Processor>("default", new ResponseStatusSetter(429)));
            driver = new ProcessingTestDriver(chains);
            responseHandler = driver.sendRequest("http://localhost/?chain=default").awaitResponse();
            Response response = responseHandler.getResponse();
            assertEquals(429, response.getStatus());
            assertEquals("ResponseHeaders are not rendered", "{\"datalist\":[]}", responseHandler.read());
        }
        finally {
            if (responseHandler != null)
                responseHandler.readAll();
        }
    }

    /** Tests that the ResponseStatus takes precedence over errors */
    @Test
    public void testResponseDataStatusOverridesErrors() throws InterruptedException {
        ProcessingTestDriver.MockResponseHandler responseHandler = null;
        try {
            List<Chain<Processor>> chains = new ArrayList<>();
            chains.add(new Chain<Processor>("default", new ResponseStatusSetter(200),
                                                       new ProcessorLibrary.StringDataAdder("Hello"),
                                                       new ProcessorLibrary.ErrorAdder(new ErrorMessage(Error.FORBIDDEN.code,"Message"))));
            driver = new ProcessingTestDriver(chains);
            responseHandler = driver.sendRequest("http://localhost/?chain=default").awaitResponse();
            Response response = responseHandler.getResponse();
            assertEquals(200, response.getStatus());
        }
        finally {
            if (responseHandler != null)
                responseHandler.readAll();
        }
    }

    private void assertEqualStatus(int statusCode,String uri) {
        ProcessingTestDriver.MockResponseHandler response = null;
        try {
            response = driver.sendRequest(uri).awaitResponse();
            assertEquals(statusCode, response.getStatus());
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        finally {
            if (response != null) {
                response.readAll();
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testProcessingHandlerSupportsAsyncRendering() throws Exception {
        // Set up
        ProcessorLibrary.FutureDataSource futureDataSource = new ProcessorLibrary.FutureDataSource();
        Chain<Processor> asyncCompletionChain = new Chain<Processor>("asyncCompletion", new ProcessorLibrary.DataCounter("async"));
        Chain<Processor> chain =
                new Chain<Processor>("federation", new ProcessorLibrary.DataCounter("sync"),
                                                   new ProcessorLibrary.Federator(new Chain<Processor>(new ProcessorLibrary.DataSource()),
                                                                                  new Chain<Processor>(new ProcessorLibrary.AsyncDataProcessingInitiator(asyncCompletionChain),futureDataSource)));
        List<Chain<Processor>> chains = new ArrayList<>();
        chains.add(chain);
        driver = new ProcessingTestDriver(chains);

        ProcessingTestDriver.MockResponseHandler responseHandler = driver.sendRequest("http://localhost/?chain=federation");
        String synchronousResponse = responseHandler.read();
        assertEquals(
                "{\"datalist\":[" +
                  "{\"datalist\":[" +
                    "{\"data\":\"first.null\"}," +
                    "{\"data\":\"second.null\"}," +
                    "{\"data\":\"third.null\"}" +
                  "]}",
                synchronousResponse);
        assertEquals("No more data is available at this point", 0, responseHandler.available());

        // Now, complete async data
        futureDataSource.incomingData.get(0).add(new ProcessorLibrary.StringData(null, "d1"));
        assertEquals(
                            "," +
                            "{\"datalist\":[" +
                            "{\"data\":\"d1\"}",
                            responseHandler.read());
        futureDataSource.incomingData.get(0).addLast(new ProcessorLibrary.StringData(null, "d2"));

        // ... which leads to the rest of the response becoming available
        assertEquals(
                    "," +
                    "{\"data\":\"d2\"}," +
                    "{\"data\":\"[async] Data count: 2\"}" +
                  "]}",
                responseHandler.read());
        assertEquals(",{\"data\":\"[sync] Data count: 3\"}" + // Async items not counted as they arrive after chain completion
                     "]}",
                     responseHandler.read());
        assertTrue("Transmission completed", null == responseHandler.read());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testProcessingHandlerSupportsAsyncUnorderedRendering() throws Exception {
        // Set up
        ProcessorLibrary.FutureDataSource futureDataSource1 = new ProcessorLibrary.FutureDataSource();
        ProcessorLibrary.FutureDataSource futureDataSource2 = new ProcessorLibrary.FutureDataSource();
        Chain<Processor> chain =
                new Chain<Processor>("federation",
                                   new ProcessorLibrary.Federator(false,new Chain<Processor>(futureDataSource1),
                                                                        new Chain<Processor>(futureDataSource2)));
        List<Chain<Processor>> chains = new ArrayList<>();
        chains.add(chain);
        driver = new ProcessingTestDriver(chains);

        ProcessingTestDriver.MockResponseHandler responseHandler = driver.sendRequest("http://localhost/?chain=federation");
        assertEquals(
                "{\"datalist\":[",
                responseHandler.read());
        assertEquals("No more data is available at this point", 0, responseHandler.available());

        // Complete second async data first
        futureDataSource2.incomingData.get(0).addLast(new ProcessorLibrary.StringData(null, "d2"));
        assertEquals(
                                    "{\"datalist\":[" +
                                    "{\"data\":\"d2\"}"+
                                    "]}",
                            responseHandler.read());

        // Now complete first async data (which is therefore rendered last)
        futureDataSource1.incomingData.get(0).addLast(new ProcessorLibrary.StringData(null, "d1"));
        assertEquals(
                            "," +
                                    "{\"datalist\":[" +
                                    "{\"data\":\"d1\"}"+
                                    "]}",
                            responseHandler.read());
        assertEquals(
                            "]}",
                            responseHandler.read());

        assertTrue("Transmission completed", responseHandler.read()==null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAsyncOnlyRendering() throws Exception {
        // Set up
        ProcessorLibrary.ListenableFutureDataSource futureDataSource = new ProcessorLibrary.ListenableFutureDataSource();
        Chain<Processor> chain = new Chain<>("main", Collections.<Processor>singletonList(futureDataSource));
        driver = new ProcessingTestDriver(chain);

        ProcessingTestDriver.MockResponseHandler responseHandler = driver.sendRequest("http://localhost/?chain=main");
        assertEquals("No data is available at this point", 0, responseHandler.available());

        futureDataSource.incomingData.get().add(new ProcessorLibrary.StringData(null, "d1"));
        assertEquals(
                            "{\"datalist\":[" +
                            "{\"data\":\"d1\"}",
                            responseHandler.read());
        futureDataSource.incomingData.get().addLast(new ProcessorLibrary.StringData(null, "d2"));

        assertEquals(
                "," +
                "{\"data\":\"d2\"}" +
                "]}",
                responseHandler.read());

        assertEquals(200, responseHandler.getStatus());
        assertTrue("Transmission completed", null == responseHandler.read());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAsyncRenderingWithClientClose() throws Exception {
        // Set up
        ProcessorLibrary.ListenableFutureDataSource futureDataSource = new ProcessorLibrary.ListenableFutureDataSource();
        Chain<Processor> chain = new Chain<>("main", Collections.<Processor>singletonList(futureDataSource));
        driver = new ProcessingTestDriver(chain);

        ProcessingTestDriver.MockResponseHandler responseHandler = driver.sendRequest("http://localhost/?chain=main");
        assertEquals("No data is available at this point", 0, responseHandler.available());

        futureDataSource.incomingData.get().add(new ProcessorLibrary.StringData(null, "d1"));
        assertEquals(
                "{\"datalist\":[" +
                        "{\"data\":\"d1\"}",
                responseHandler.read());
        responseHandler.clientClose();
        futureDataSource.incomingData.get().addLast(new ProcessorLibrary.StringData(null, "d2"));

        assertNull(responseHandler.read());

        assertEquals(200, responseHandler.getStatus());
        assertTrue("Transmission completed", null == responseHandler.read());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAsyncOnlyRenderingWithAsyncPostData() throws Exception {
        // Set up
        ProcessorLibrary.ListenableFutureDataSource futureDataSource = new ProcessorLibrary.ListenableFutureDataSource();
        PostReader postReader = new PostReader();
        Chain<Processor> chain = new Chain<>("main",
                                             new ProcessorLibrary.AsyncDataProcessingInitiator(new Chain<>(postReader)),
                                             futureDataSource);
        driver = new ProcessingTestDriver(chain);
        RequestHandlerTestDriver.MockResponseHandler responseHandler =
                driver.sendRequest("http://localhost/?chain=main", HttpRequest.Method.POST, "Hello, world!");

        assertFalse("Post data is read later, on async completion", postReader.bodyDataFuture.isDone());
        assertEquals("No data is available at this point", 0, responseHandler.available());

        futureDataSource.incomingData.get().add(new ProcessorLibrary.StringData(null, "d1"));
        assertEquals(
                "{\"datalist\":[" +
                        "{\"data\":\"d1\"}",
                responseHandler.read()
        );
        futureDataSource.incomingData.get().addLast(new ProcessorLibrary.StringData(null, "d2"));

        assertEquals(
                "," +
                        "{\"data\":\"d2\"}" +
                        "]}",
                responseHandler.read()
        );
        assertEquals("Data is completed, so post data is read", "Hello, world!", postReader.bodyDataFuture.get().trim());

        assertEquals(200, responseHandler.getStatus());
        assertTrue("Transmission completed", null == responseHandler.read());
    }

    private static class PostReader extends Processor {

        SettableFuture<String> bodyDataFuture = SettableFuture.create();

        @Override
        public com.yahoo.processing.Response process(com.yahoo.processing.Request request, Execution execution) {
            try {
                InputStream stream = ((com.yahoo.container.jdisc.HttpRequest)request.properties().get(com.yahoo.processing.Request.JDISC_REQUEST)).getData();
                StringBuilder b = new StringBuilder();
                int nextRead;
                while (-1 != (nextRead = stream.read()))
                    b.appendCodePoint(nextRead);
                bodyDataFuture.set(b.toString());
                return execution.process(request);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStatusAndHeadersCanBeSetAsynchronously() throws Exception {
        Map<String,List<String>> responseHeaders = new HashMap<>();
        responseHeaders.put("foo", Collections.singletonList("fooValue"));
        responseHeaders.put("bar", Arrays.asList(new String[] { "barValue", "bazValue"}));

        // Set up
        ProcessorLibrary.ListenableFutureDataSource futureDataSource = new ProcessorLibrary.ListenableFutureDataSource(true, false);
        Chain<Processor> chain = new Chain<Processor>("main", new ProcessorLibrary.AsyncDataProcessingInitiator(new Chain<Processor>("async", new ProcessorLibrary.StatusSetter(500), new ResponseHeaderSetter(responseHeaders))), futureDataSource);
        driver = new ProcessingTestDriver(chain);

        ProcessingTestDriver.MockResponseHandler responseHandler = driver.sendRequest("http://localhost/?chain=main");
        assertEquals("No data is available at this point", 0, responseHandler.available());

        com.yahoo.processing.Request request = futureDataSource.incomingData.get().getOwner().request();
        futureDataSource.incomingData.get().addLast(new ProcessorLibrary.StringData(request, "d1"));
        //assertEquals("{\"datalist\":[{\"data\":\"d1\"}]}", consumeFrom(responseHandler.content));
        assertEquals("{\"errors\":[\"500: \"],\"datalist\":[{\"data\":\"d1\"}]}", responseHandler.read());

        assertEquals(500, responseHandler.getStatus());
        assertEquals("[fooValue]", responseHandler.getResponse().headers().get("foo").toString());
        assertEquals("[barValue, bazValue]", responseHandler.getResponse().headers().get("bar").toString());
        assertTrue("Transmission completed", null == responseHandler.read());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAsyncRenderingDoesNotHoldThreads() throws Exception {
        // Set up
        ProcessorLibrary.FutureDataSource futureDataSource = new ProcessorLibrary.FutureDataSource();
        // Add some sync data as well to cause rendering to start before async data is added.
        // This allows us to wait on return data rather than having to wait for the 100 requests
        // to be done, which is cumbersome.
        Chain<Processor> chain = new Chain<Processor>("main", new ProcessorLibrary.Federator(new Chain<Processor>(new ProcessorLibrary.DataSource()), new Chain<Processor>(futureDataSource)));
        driver = new ProcessingTestDriver(chain);

        int requestCount = 1000;
        ProcessingTestDriver.MockResponseHandler[] responseHandler = new ProcessingTestDriver.MockResponseHandler[requestCount];
        for (int i = 0; i < requestCount; i++) {
            responseHandler[i] = driver.sendRequest("http://localhost/?chain=main");
            assertEquals("Sync data is available",
                         "{\"datalist\":[{\"datalist\":[{\"data\":\"first.null\"},{\"data\":\"second.null\"},{\"data\":\"third.null\"}]}",
                         responseHandler[i].read());
        }
        assertEquals("All requests was processed", requestCount, futureDataSource.incomingData.size());

        // Complete all
        for (int i = 0; i < requestCount; i++) {
            futureDataSource.incomingData.get(i).add(new ProcessorLibrary.StringData(null, "d1"));
            assertEquals(",{\"datalist\":[{\"data\":\"d1\"}", responseHandler[i].read());
            futureDataSource.incomingData.get(i).addLast(new ProcessorLibrary.StringData(null, "d2"));
            assertEquals(",{\"data\":\"d2\"}]}", responseHandler[i].read());
            assertEquals("]}", responseHandler[i].read());
            assertTrue("Transmission completed", null == responseHandler[i].read());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStreamedRendering() throws Exception {
        // Set up
        Chain<Processor> streamChain = new Chain<Processor>(new StreamProcessor());

        ProcessorLibrary.ListenableFutureDataSource futureDataSource = new ProcessorLibrary.ListenableFutureDataSource();
        Chain<Processor> mainChain = new Chain<Processor>("main", new ProcessorLibrary.StreamProcessingInitiator(streamChain), futureDataSource);
        driver = new ProcessingTestDriver(mainChain);

        ProcessingTestDriver.MockResponseHandler responseHandler = driver.sendRequest("http://localhost/?chain=main");

        // Add one data element
        futureDataSource.incomingData.get().add(new MapData(null));
        assertEquals(
                "{\"datalist\":[" +
                        "{\"data\":\"map data: {streamProcessed=true}\"}",
                responseHandler.read()
        );
        // add another
        futureDataSource.incomingData.get().add(new MapData(null));
        assertEquals(
                ",{\"data\":\"map data: {streamProcessed=true}\"}",
                responseHandler.read());

        // add last
        futureDataSource.incomingData.get().addLast(new MapData(null));
        assertEquals(
                ",{\"data\":\"map data: {streamProcessed=true}\"}]}",
                responseHandler.read());

        assertTrue("Transmission completed", null == responseHandler.read());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEagerStreamedRenderingOnFreeze() throws Exception {
        FreezingDataSource source = new FreezingDataSource();
        Chain<Processor> mainChain = new Chain<Processor>("main", source);
        driver = new ProcessingTestDriver(mainChain);
        ProcessingTestDriver.MockResponseHandler responseHandler = driver.sendRequest("http://localhost/?chain=main");
        assertEquals("No data is available at this point", 0, responseHandler.available());
        source.freeze.set(true);
        assertEquals("{\"datalist\":[{\"data\":\"d1\"}", responseHandler.read());
        source.addLastData.set(true); // signal completion
        assertEquals(",{\"data\":\"d2\"}]}", responseHandler.read());
        assertTrue("Transmission completed", null == responseHandler.read());
    }

    @SuppressWarnings("unchecked")
    @Test
    @Ignore // TODO
    public void testNestedEagerStreamedRenderingOnFreeze() throws Exception {
        try {
            FreezingDataSource source1 = new FreezingDataSource("s1");
            FreezingDataSource source2 = new FreezingDataSource("s2");
            FreezingDataSource source3 = new FreezingDataSource("s3");
            Chain<Processor> mainChain = new Chain<Processor>("main",
                    new ProcessorLibrary.StringDataAdder("main-data"),
                    new ProcessorLibrary.EagerReturnFederator(true,
                            new Chain<Processor>(source1),
                            new Chain<Processor>(source2),
                            new Chain<Processor>(source3)));
            driver = new ProcessingTestDriver(mainChain);
            ProcessingTestDriver.MockResponseHandler responseHandler = driver.sendRequest("http://localhost/?chain=main");
            assertEquals("No data is available at this point", 0, responseHandler.available());
            source1.freeze.set(true);
            assertEquals("Received because the parent list and source1 list is frozen",
                    "{\"datalist\":[{\"datalist\":[{\"data\":\"s1d1\"}", responseHandler.read());

            source2.addLastData.set(true); // No effect as we are working on source1, which is not completed yet
            assertEquals("{\"datalist\":[{\"data\":\"s1d1\"}", responseHandler.read());
            source1.addLastData.set(true); // Make source 1 and 2 available
            assertEquals(",{\"data\":\"d2\"}]}", responseHandler.read());
            assertTrue("Transmission completed", null == responseHandler.read());
        }
        catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRetrievingNonExistingRendererThrows() throws Exception {
        driver = new ProcessingTestDriver(Collections.<Chain<Processor>>emptyList());
        driver.processingHandler().getRendererCopy(ComponentSpecification.fromString("non-existent"));
    }

    @Test
    public void testDefaultRendererIsAddedToRegistryWhenNoneIsGivenByUser() throws Exception {
        String defaultId = AbstractProcessingHandler.DEFAULT_RENDERER_ID;

        driver = new ProcessingTestDriver(Collections.<Chain<Processor>>emptyList());
        Renderer defaultRenderer = driver.processingHandler().getRenderers().getComponent(defaultId);
        assertThat(defaultRenderer, notNullValue());

    }

    @Test
    public void testUserSpecifiedDefaultRendererIsNotReplacedInRegistry() throws Exception {
        String defaultId = AbstractProcessingHandler.DEFAULT_RENDERER_ID;
        Renderer myDefaultRenderer = new ProcessingRenderer();
        ComponentRegistry<Renderer> renderers = new ComponentRegistry<>();
        renderers.register(ComponentId.fromString(defaultId), myDefaultRenderer);

        driver = new ProcessingTestDriver(Collections.<Chain<Processor>>emptyList(), renderers);
        Renderer defaultRenderer = driver.processingHandler().getRenderers().getComponent(defaultId);
        assertThat(defaultRenderer, sameInstance(myDefaultRenderer));

    }

    private static class FreezingDataSource extends Processor {

        final SettableFuture<Boolean> freeze = SettableFuture.create();
        final SettableFuture<Boolean> addLastData = SettableFuture.create();

        private final String stringDataPrefix;

        public FreezingDataSource() {
            this("");
        }

        public FreezingDataSource(String stringDataPrefix) {
            this.stringDataPrefix = stringDataPrefix;
        }

        @Override
        public com.yahoo.processing.Response process(com.yahoo.processing.Request request, Execution execution) {
            try {
                com.yahoo.processing.Response response = execution.process(request);
                response.data().add(new ProcessorLibrary.StringData(request, stringDataPrefix + "d1"));
                freeze.get();
                response.data().freeze();
                // wait for permission from test driver to add more data
                addLastData.get();
                response.data().add(new ProcessorLibrary.StringData(request, stringDataPrefix + "d2"));
                return response;
            }
            catch (InterruptedException | ConcurrentModificationException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

    }

    @SuppressWarnings("unchecked")
    private static class StreamProcessor extends Processor {

        @Override
        public com.yahoo.processing.Response process(com.yahoo.processing.Request request, Execution execution) {
            com.yahoo.processing.Response response = execution.process(request);
            List<Data> dataList = response.data().asList();
            for (Data data : dataList) {
                if ( ! (data instanceof MapData)) continue;
                MapData mapData = (MapData)data;
                mapData.map().put("streamProcessed",Boolean.TRUE);
            }
            return response;
        }

    }

    private String defaultChainResponse =
        "{\"datalist\":[" +
          "{\"data\":\"StringData.toString()\"}," +
          "{\"datalist\":[" +
            "{\"data\":\"Item1\"}," +
            "{\"data\":\"Item2\"}]" +
          "}]" +
        "}";

    private String simpleChainResponse =
            "{\"datalist\":[" +
              "{\"data\":\"StringData.toString()\"}]" +
            "}";

    private String trace1 =
        "{\"trace\":[" +
          "\"TraceMessage\"" +
        "],";

    private String trace1WithFullResult =
            "{\"trace\":[" +
              "\"TraceMessage\"" +
            "]," +
            "\"datalist\":[" +
              "{\"data\":\"StringData.toString()\"}," +
              "{\"datalist\":[" +
                "{\"data\":\"Item1\"}," +
                "{\"data\":\"Item2\"}" +
              "]}" +
            "]}";

    private String trace4 =
        "{\"trace\":[" +
          "\"Invoke '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$StringDataListAdder'\"," +
          "\"Invoke '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$Trace'\"," +
          "\"TraceMessage\"," +
          "\"Invoke '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$StringDataAdder'\"," +
          "\"Return '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$StringDataAdder'\"," +
          "\"Return '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$Trace'\"," +
          "\"Return '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$StringDataListAdder'\"" +
        "],";

    private String trace5 =
            "{\"trace\":[" +
              "\"Invoke '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$StringDataListAdder'\"," +
              "\"Invoke '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$Trace'\"," +
              "\"TraceMessage\"," +
              "\"Invoke '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$StringDataAdder'\"," +
              "\"Return '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$StringDataAdder'\"," +
              "\"Return '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$Trace'\"," +
              "\"Return '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$StringDataListAdder'\"" +
            "],";

    private String trace6 =
            "{\"trace\":[" +
               "{\"timestamp\":ddddddddddddd,\"message\":\"Invoke '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$StringDataListAdder'\"}," +
               "{\"timestamp\":ddddddddddddd,\"message\":\"Invoke '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$Trace'\"}," +
               "\"TraceMessage\"," +
               "{\"timestamp\":ddddddddddddd,\"message\":\"Invoke '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$StringDataAdder'\"}," +
               "{\"timestamp\":ddddddddddddd,\"message\":\"Return '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$StringDataAdder'\"}," +
               "{\"timestamp\":ddddddddddddd,\"message\":\"Return '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$Trace'\"}," +
               "{\"timestamp\":ddddddddddddd,\"message\":\"Return '(anonymous)' of class 'com.yahoo.processing.test.ProcessorLibrary$StringDataListAdder'\"}" +
            "],";

    /** Adds a set of headers to every passing response */
    @SuppressWarnings("unchecked")
    public static class ResponseHeaderSetter extends Processor {

        private final Map<String,List<String>> responseHeaders;

        public ResponseHeaderSetter(Map<String,List<String>> responseHeaders) {
            this.responseHeaders = Collections.unmodifiableMap(responseHeaders);
        }

        @Override
        public com.yahoo.processing.Response process(com.yahoo.processing.Request request, Execution execution) {
            com.yahoo.processing.Response response = execution.process(request);
            response.data().add(new ResponseHeaders(responseHeaders, request));
            return response;
        }

    }

    /** Adds a HTTP status to every passing response */
    @SuppressWarnings("unchecked")
    public static class ResponseStatusSetter extends Processor {

        private final int code;

        public ResponseStatusSetter(int code) {
            this.code = code;
        }

        @Override
        public com.yahoo.processing.Response process(com.yahoo.processing.Request request, Execution execution) {
            com.yahoo.processing.Response response = execution.process(request);
            response.data().add(new ResponseStatus(code, request));
            return response;
        }

    }

}
