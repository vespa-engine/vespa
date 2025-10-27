// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.Request;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.RootId;
import com.yahoo.search.rendering.XmlRenderer;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class SearchHandlerTest {

    @Test
    void testNullQuery() {
        try (var tester = new SearchHandlerTester()) {
            assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                         "<result total-hit-count=\"0\">\n" +
                         "  <hit relevancy=\"1.0\">\n" +
                         "    <field name=\"relevancy\">1.0</field>\n" +
                         "    <field name=\"uri\">testHit</field>\n" +
                         "  </hit>\n" +
                         "</result>\n",
                         tester.sendRequest("http://localhost?format=xml").readAll()
            );
        }
    }

    @Test
    void testFailing() {
        try (var tester = new SearchHandlerTester()) {
            assertTrue(tester.sendRequest("http://localhost?query=test&searchChain=classLoadingError").readAll().contains("NoClassDefFoundError"));
        }
    }

    @Test
    void testTimeout() {
        try (var tester = new SearchHandlerTester()) {
            // 1µs is truncated to 0ms, so this will always time out.
            assertTrue(tester.sendRequest("http://localhost?query=test&timeout=1µs").readAll().contains("Timed out"));
        }
    }

    @Test
    synchronized void testPluginError() {
        try (var tester = new SearchHandlerTester()) {
            assertTrue(tester.sendRequest("http://localhost?query=test&searchChain=exceptionInPlugin").readAll().contains("NullPointerException"));
        }
    }

    @Test
    synchronized void testWorkingReconfiguration() throws Exception {
        try (var tester = new SearchHandlerTester()) {
            tester.assertJsonResult("http://localhost?query=abc");

            // reconfiguration
            tester.copyDirectory("handlers2");
            tester.generateComponentsConfigForActive();
            tester.configurer.reloadConfig();

            // ...and check the resulting config
            SearchHandler newSearchHandler = tester.fetchSearchHandler();
            assertNotSame(tester.searchHandler, newSearchHandler, "Have a new instance of the search handler");
            assertNotNull(tester.fetchSearchHandler().getSearchChainRegistry().getChain("hello"), "Have the new search chain");
            assertNull(tester.fetchSearchHandler().getSearchChainRegistry().getChain("classLoadingError"), "Don't have the new search chain");
            try (RequestHandlerTestDriver newDriver = new RequestHandlerTestDriver(newSearchHandler)) {
                tester.assertJsonResult("http://localhost?query=abc", newDriver);
            }
        }
    }

    @Test
    void testResponseBasics() {
        Query q = new Query("?query=dummy&tracelevel=3");
        q.trace("nalle", 1);
        Result r = new Result(q);
        r.hits().addError(ErrorMessage.createUnspecifiedError("bamse"));
        r.hits().add(new Hit("http://localhost/dummy", 0.5));
        HttpSearchResponse s = new HttpSearchResponse(200, r, q, new XmlRenderer());
        assertEquals("text/xml", s.getContentType());
        assertNull(s.getCoverage());
        assertEquals("query 'WEAKAND(100) dummy'", s.getParsedQuery());
        assertEquals(500, s.getTiming().getTimeout());
    }

    @Test
    void testInvalidYqlQuery() throws Exception {
        try (var tester = new SearchHandlerTester()) {
            tester.copyDirectory("config_yql");
            tester.generateComponentsConfigForActive();
            tester.configurer.reloadConfig();

            SearchHandler newSearchHandler = tester.fetchSearchHandler();
            assertNotSame(tester.searchHandler, newSearchHandler, "Have a new instance of the search handler");
            try (RequestHandlerTestDriver newDriver = new RequestHandlerTestDriver(newSearchHandler)) {
                RequestHandlerTestDriver.MockResponseHandler responseHandler = newDriver.sendRequest(
                        "http://localhost/search/?yql=select%20*%20from%20foo%20where%20bar%20%3E%201453501295%27%3B");
                responseHandler.readAll();
                assertEquals(400, responseHandler.getStatus());
                assertEquals(Request.RequestType.READ, responseHandler.getResponse().getRequestType());
            }
        }
    }

    @Test
    void testRequestType() throws Exception {
        try (var tester = new SearchHandlerTester()) {
            tester.copyDirectory("config_yql");
            tester.generateComponentsConfigForActive();
            tester.configurer.reloadConfig();

            SearchHandler newSearchHandler = tester.fetchSearchHandler();
            try (RequestHandlerTestDriver newDriver = new RequestHandlerTestDriver(newSearchHandler)) {
                RequestHandlerTestDriver.MockResponseHandler responseHandler = newDriver.sendRequest(
                        "http://localhost/search/?query=foo");
                responseHandler.readAll();
                assertEquals(Request.RequestType.READ, responseHandler.getResponse().getRequestType());
            }
        }
    }

    // Query handling takes a different code path when a query profile is active, so we test both paths.
    @Test
    void testInvalidQueryParamWithQueryProfile() throws Exception {
        try (var tester = new SearchHandlerTester()) {
            try (RequestHandlerTestDriver newDriver = tester.driverWithConfig("config_invalid_param")) {
                testInvalidQueryParam(newDriver);
            }
        }
    }

    @Test
    void testInvalidQueryParamWithoutQueryProfile() {
        try (var tester = new SearchHandlerTester()) {
            testInvalidQueryParam(tester.driver);
        }
    }

    private void testInvalidQueryParam(RequestHandlerTestDriver testDriver) {
        RequestHandlerTestDriver.MockResponseHandler responseHandler =
                testDriver.sendRequest("http://localhost/search/?query=status_code%3A0&hits=20&offset=-20");
        String response = responseHandler.readAll();
        assertEquals(400, responseHandler.getStatus());
        assertTrue(response.contains("offset"));
        assertTrue(response.contains("\"code\":" + com.yahoo.container.protect.Error.ILLEGAL_QUERY.code));
    }

    @Test
    void testResultStatus() {
        try (var tester = new SearchHandlerTester()) {
            assertEquals(200, tester.httpStatus(result().build()));
            assertEquals(200, tester.httpStatus(result().withHit().build()));
            assertEquals(200, tester.httpStatus(result().withGroups().build()));
            assertEquals(200, tester.httpStatus(result().withGroups().withHit().build()));

            assertEquals(500, tester.httpStatus(result().withError().build()));
            assertEquals(200, tester.httpStatus(result().withError().withHit().build()));
            assertEquals(200, tester.httpStatus(result().withError().withGroups().build()));
            assertEquals(200, tester.httpStatus(result().withError().withGroups().withHit().build()));
        }
    }

    @Test
    void testWebServiceStatus() {
        try (var tester = new SearchHandlerTester()) {
            RequestHandlerTestDriver.MockResponseHandler responseHandler =
                    tester.sendRequest("http://localhost/search/?query=web_service_status_code");
            String response = responseHandler.readAll();
            assertEquals(406, responseHandler.getStatus());
            assertTrue(response.contains("\"code\":" + 406));
        }
    }

    @Test
    void testNormalResultImplicitDefaultRendering() {
        try (var tester = new SearchHandlerTester()) {
            tester.assertJsonResult("http://localhost?query=abc");
        }
    }

    @Test
    void testNormalResultExplicitDefaultRendering() {
        try (var tester = new SearchHandlerTester()) {
            tester.assertJsonResult("http://localhost?query=abc&format=default");
        }
    }

    @Test
    void testNormalResultXmlAliasRendering() {
        try (var tester = new SearchHandlerTester()) {
            tester.assertXmlResult("http://localhost?query=abc&format=xml");
        }
    }

    @Test
    void testNormalResultJsonAliasRendering() {
        try (var tester = new SearchHandlerTester()) {
            tester.assertJsonResult("http://localhost?query=abc&format=json");
        }
    }

    @Test
    void testNormalResultExplicitDefaultRenderingFullRendererName1() {
        try (var tester = new SearchHandlerTester()) {
            tester.assertXmlResult("http://localhost?query=abc&format=XmlRenderer");
        }
    }

    @Test
    void testNormalResultExplicitDefaultRenderingFullRendererName2() {
        try (var tester = new SearchHandlerTester()) {
            tester.assertJsonResult("http://localhost?query=abc&format=JsonRenderer");
        }
    }

    @Test
    void testFaultyHandlers() throws Exception {
        try (var tester = new SearchHandlerTester()) {
            tester.assertHandlerResponse(500, null, "NullReturning");
            tester.assertHandlerResponse(500, null, "NullReturningAsync");
            tester.assertHandlerResponse(500, null, "Throwing");
            tester.assertHandlerResponse(500, null, "ThrowingAsync");
        }
    }

    @Test
    void testForwardingHandlers() throws Exception {
        try (var tester = new SearchHandlerTester()) {
            tester.assertHandlerResponse(200, tester.jsonResult, "ForwardingAsync");

            // Fails because we are forwarding from a sync to an async handler -
            // the sync handler will respond with status 500 because the async one has not produced a response yet.
            // Disabled because this fails due to a race and is therefore unstable
            // assertHandlerResponse(500, null, "Forwarding");
        }
    }

    /** Referenced from config */
    public static class TestSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Result result = new Result(query);
            Hit hit = new Hit("testHit");
            hit.setField("uri", "testHit");
            result.hits().add(hit);

            if (query.getModel().getQueryString().contains("web_service_status_code"))
                result.hits().addError(new ErrorMessage(406, "Test web service code"));

            return result;
        }

    }

    /** Referenced from config */
    public static class ClassLoadingErrorSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            throw new NoClassDefFoundError(); // Simulate typical OSGi problem
        }

        private static File newFolder(File root, String... subDirs) throws IOException {
            String subFolder = String.join("/", subDirs);
            File result = new File(root, subFolder);
            if (!result.mkdirs()) {
                throw new IOException("Couldn't create folders " + root);
            }
            return result;
        }
    }

    /** Referenced from config */
    public static class ExceptionInPluginSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Result result = execution.search(query);
            try {
                result.hits().add(null); // Trigger NullPointerException
            } catch (NullPointerException e) {
                throw new RuntimeException("Message", e);
            }
            return result;
        }

        private static File newFolder(File root, String... subDirs) throws IOException {
            String subFolder = String.join("/", subDirs);
            File result = new File(root, subFolder);
            if (!result.mkdirs()) {
                throw new IOException("Couldn't create folders " + root);
            }
            return result;
        }
    }

    /** Referenced from config */
    public static class HelloWorldSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Result result = execution.search(query);
            result.hits().add(new Hit("HelloWorld"));
            return result;
        }

        private static File newFolder(File root, String... subDirs) throws IOException {
            String subFolder = String.join("/", subDirs);
            File result = new File(root, subFolder);
            if (!result.mkdirs()) {
                throw new IOException("Couldn't create folders " + root);
            }
            return result;
        }
    }

    /** Referenced from config */
    public static class EchoingQuerySearcher extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Result result = execution.search(query);
            Hit hit = new Hit("Query");
            hit.setField("query", query.yqlRepresentation());
            result.hits().add(hit);
            return result;
        }

        private static File newFolder(File root, String... subDirs) throws IOException {
            String subFolder = String.join("/", subDirs);
            File result = new File(root, subFolder);
            if (!result.mkdirs()) {
                throw new IOException("Couldn't create folders " + root);
            }
            return result;
        }
    }

    /** Referenced from config */
    public static class ForwardingHandler extends ThreadedHttpRequestHandler {

        private final SearchHandler searchHandler;

        public ForwardingHandler(SearchHandler searchHandler) {
            super(Executors.newSingleThreadExecutor(), null, false);
            this.searchHandler = searchHandler;
        }

        @Override
        public HttpResponse handle(HttpRequest httpRequest) {
            try {
                HttpRequest forwardRequest =
                        new HttpRequest.Builder(httpRequest).uri(new URI("http://localhost/search/?query=test")).createDirectRequest();
                return searchHandler.handle(forwardRequest);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static File newFolder(File root, String... subDirs) throws IOException {
            String subFolder = String.join("/", subDirs);
            File result = new File(root, subFolder);
            if (!result.mkdirs()) {
                throw new IOException("Couldn't create folders " + root);
            }
            return result;
        }

    }

    /** Referenced from config */
    public static class ForwardingAsyncHandler extends ThreadedHttpRequestHandler {

        private final SearchHandler searchHandler;

        public ForwardingAsyncHandler(SearchHandler searchHandler) {
            super(Executors.newSingleThreadExecutor(), null, true);
            this.searchHandler = searchHandler;
        }

        @Override
        public HttpResponse handle(HttpRequest httpRequest) {
            try {
                HttpRequest forwardRequest =
                        new HttpRequest.Builder(httpRequest).uri(new URI("http://localhost/search/?query=test")).createDirectRequest();
                return searchHandler.handle(forwardRequest);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static File newFolder(File root, String... subDirs) throws IOException {
            String subFolder = String.join("/", subDirs);
            File result = new File(root, subFolder);
            if (!result.mkdirs()) {
                throw new IOException("Couldn't create folders " + root);
            }
            return result;
        }

    }

    /** Referenced from config */
    public static class NullReturningHandler extends ThreadedHttpRequestHandler {

        public NullReturningHandler() {
            super(Executors.newSingleThreadExecutor(), null, false);
        }

        @Override
        public HttpResponse handle(HttpRequest httpRequest) {
            return null;
        }

        private static File newFolder(File root, String... subDirs) throws IOException {
            String subFolder = String.join("/", subDirs);
            File result = new File(root, subFolder);
            if (!result.mkdirs()) {
                throw new IOException("Couldn't create folders " + root);
            }
            return result;
        }

    }

    /** Referenced from config */
    public static class NullReturningAsyncHandler extends ThreadedHttpRequestHandler {

        public NullReturningAsyncHandler() {
            super(Executors.newSingleThreadExecutor(), null, true);
        }

        @Override
        public HttpResponse handle(HttpRequest httpRequest) {
            return null;
        }

        private static File newFolder(File root, String... subDirs) throws IOException {
            String subFolder = String.join("/", subDirs);
            File result = new File(root, subFolder);
            if (!result.mkdirs()) {
                throw new IOException("Couldn't create folders " + root);
            }
            return result;
        }

    }

    /** Referenced from config */
    public static class ThrowingHandler extends ThreadedHttpRequestHandler {

        public ThrowingHandler() {
            super(Executors.newSingleThreadExecutor(), null, false);
        }

        @Override
        public HttpResponse handle(HttpRequest httpRequest) {
            throw new RuntimeException();
        }

        private static File newFolder(File root, String... subDirs) throws IOException {
            String subFolder = String.join("/", subDirs);
            File result = new File(root, subFolder);
            if (!result.mkdirs()) {
                throw new IOException("Couldn't create folders " + root);
            }
            return result;
        }

    }

    /** Referenced from config */
    public static class ThrowingAsyncHandler extends ThreadedHttpRequestHandler {

        public ThrowingAsyncHandler() {
            super(Executors.newSingleThreadExecutor(), null, true);
        }

        @Override
        public HttpResponse handle(HttpRequest httpRequest) {
            throw new RuntimeException();
        }

        private static File newFolder(File root, String... subDirs) throws IOException {
            String subFolder = String.join("/", subDirs);
            File result = new File(root, subFolder);
            if (!result.mkdirs()) {
                throw new IOException("Couldn't create folders " + root);
            }
            return result;
        }

    }

    private ResultBuilder result() { return new ResultBuilder(); }

    private static class ResultBuilder {

        Result result = new Result(new Query());

        public ResultBuilder withHit() {
            result.hits().add(new Hit("regularHit:1"));
            return this;
        }

        public ResultBuilder withGroups() {
            result.hits().add(new Group(new RootId(1), new Relevance(1.0)));
            return this;
        }

        public ResultBuilder withError() {
            result.hits().addError(ErrorMessage.createUnspecifiedError("Test error"));
            return this;
        }

        public Result build() { return result; }

        private static File newFolder(File root, String... subDirs) throws IOException {
            String subFolder = String.join("/", subDirs);
            File result = new File(root, subFolder);
            if (!result.mkdirs()) {
                throw new IOException("Couldn't create folders " + root);
            }
            return result;
        }

    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

}
