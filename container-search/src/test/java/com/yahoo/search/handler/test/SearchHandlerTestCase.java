// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler.test;

import com.yahoo.container.Container;
import com.yahoo.container.core.config.testutil.HandlersConfigurerTestWrapper;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.net.HostName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.handler.HttpSearchResponse;
import com.yahoo.search.handler.SearchHandler;
import com.yahoo.search.rendering.DefaultRenderer;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.config.test.SearchChainConfigurerTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Executors;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class SearchHandlerTestCase {

    private static final String testDir = "src/test/java/com/yahoo/search/handler/test/config";
    private static final String myHostnameHeader = "my-hostname-header";
    private static final String selfHostname = HostName.getLocalhost();

    private static String tempDir = "";
    private static String configId = null;

    @Rule
    public TemporaryFolder tempfolder = new TemporaryFolder();

    private RequestHandlerTestDriver driver = null;
    private HandlersConfigurerTestWrapper configurer = null;
    private SearchHandler searchHandler;

    @Before
    public void startUp() throws IOException {
        File cfgDir = tempfolder.newFolder("SearchHandlerTestCase");
        tempDir = cfgDir.getAbsolutePath();
        configId = "dir:" + tempDir;

        IOUtils.copyDirectory(new File(testDir), cfgDir, 1); // make configs active
        generateComponentsConfigForActive();

        configurer = new HandlersConfigurerTestWrapper(new Container(), configId);
        searchHandler = (SearchHandler)configurer.getRequestHandlerRegistry().getComponent(SearchHandler.class.getName());
        driver = new RequestHandlerTestDriver(searchHandler);
    }

    @After
    public void shutDown() {
        if (configurer != null) configurer.shutdown();
        if (driver != null) driver.close();
    }

    private void generateComponentsConfigForActive() throws IOException {
        File activeConfig = new File(tempDir);
        SearchChainConfigurerTestCase.
                createComponentsConfig(new File(activeConfig, "chains.cfg").getPath(),
                                       new File(activeConfig, "handlers.cfg").getPath(),
                                       new File(activeConfig, "components.cfg").getPath());
    }

    private SearchHandler fetchSearchHandler(HandlersConfigurerTestWrapper configurer) {
        return (SearchHandler) configurer.getRequestHandlerRegistry().getComponent(SearchHandler.class.getName());
    }

    @Test
    public void testNullQuery() throws Exception {
        assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                     "<result total-hit-count=\"0\">\n" +
                     "  <hit relevancy=\"1.0\">\n" +
                     "    <field name=\"relevancy\">1.0</field>\n" +
                     "    <field name=\"uri\">testHit</field>\n" +
                     "  </hit>\n" +
                     "</result>\n",
                     driver.sendRequest("http://localhost?format=xml").readAll()
            );
    }

    @Test
    public void testFailing() throws Exception {
         assertTrue(driver.sendRequest("http://localhost?query=test&searchChain=classLoadingError").readAll().contains("NoClassDefFoundError"));
    }

    @Test
    public synchronized void testPluginError() throws Exception {
        assertTrue(driver.sendRequest("http://localhost?query=test&searchChain=exceptionInPlugin").readAll().contains("NullPointerException"));
    }

    @Test
    public synchronized void testWorkingReconfiguration() throws Exception {
        assertJsonResult("http://localhost?query=abc", driver);

        // reconfiguration
        IOUtils.copyDirectory(new File(testDir, "handlers2"), new File(tempDir), 1);
        generateComponentsConfigForActive();
        configurer.reloadConfig();

        // ...and check the resulting config
        SearchHandler newSearchHandler = fetchSearchHandler(configurer);
        assertTrue("Have a new instance of the search handler", searchHandler != newSearchHandler);
        assertNotNull("Have the new search chain", fetchSearchHandler(configurer).getSearchChainRegistry().getChain("hello"));
        assertNull("Don't have the new search chain", fetchSearchHandler(configurer).getSearchChainRegistry().getChain("classLoadingError"));
        try (RequestHandlerTestDriver newDriver = new RequestHandlerTestDriver(searchHandler)) {
            assertJsonResult("http://localhost?query=abc", newDriver);
        }
    }

    @Test
    @Ignore //TODO: Must be done at the ConfiguredApplication level, not handlers configurer? Also, this must be rewritten as the above
    public synchronized void testFailedReconfiguration() throws Exception {
        assertXmlResult(driver);

        // attempt reconfiguration
        IOUtils.copyDirectory(new File(testDir, "handlersInvalid"), new File(tempDir), 1);
        generateComponentsConfigForActive();
        configurer.reloadConfig();
        SearchHandler newSearchHandler = fetchSearchHandler(configurer);
        RequestHandler newMockHandler = configurer.getRequestHandlerRegistry().getComponent("com.yahoo.search.handler.test.MockHandler");
        assertTrue("Reconfiguration failed: Kept the existing instance of the search handler", searchHandler == newSearchHandler);
        assertNull("Reconfiguration failed: No mock handler", newMockHandler);
        try (RequestHandlerTestDriver newDriver = new RequestHandlerTestDriver(searchHandler)) {
            assertXmlResult(newDriver);
        }
    }

    @Test
    public void testResponseBasics() {
        Query q = new Query("?query=dummy&tracelevel=3");
        q.trace("nalle", 1);
        Result r = new Result(q);
        r.hits().addError(ErrorMessage.createUnspecifiedError("bamse"));
        r.hits().add(new Hit("http://localhost/dummy", 0.5));
        HttpSearchResponse s = new HttpSearchResponse(200, r, q, new DefaultRenderer());
        assertEquals("text/xml", s.getContentType());
        assertNull(s.getCoverage());
        assertEquals("query 'dummy'", s.getParsedQuery());
        assertEquals(5000, s.getTiming().getTimeout());
    }

    @Test
    public void testInvalidYqlQuery() throws Exception {
        IOUtils.copyDirectory(new File(testDir, "config_yql"), new File(tempDir), 1);
        generateComponentsConfigForActive();
        configurer.reloadConfig();

        SearchHandler newSearchHandler = fetchSearchHandler(configurer);
        assertTrue("Have a new instance of the search handler", searchHandler != newSearchHandler);
        try (RequestHandlerTestDriver newDriver = new RequestHandlerTestDriver(newSearchHandler)) {
            RequestHandlerTestDriver.MockResponseHandler responseHandler = newDriver.sendRequest(
                    "http://localhost/search/?yql=select%20*%20from%20foo%20where%20bar%20%3E%201453501295%27%3B");
            responseHandler.readAll();
            assertThat(responseHandler.getStatus(), is(400));
        }
    }



    // Query handling takes a different code path when a query profile is active, so we test both paths.
    @Test
    public void testInvalidQueryParamWithQueryProfile() throws Exception {
        try (RequestHandlerTestDriver newDriver = driverWithConfig("config_invalid_param")) {
            testInvalidQueryParam(newDriver);
        }
    }
    @Test
    public void testInvalidQueryParamWithoutQueryProfile() throws Exception {
        testInvalidQueryParam(driver);
    }
    private void testInvalidQueryParam(final RequestHandlerTestDriver testDriver) {
        RequestHandlerTestDriver.MockResponseHandler responseHandler =
                testDriver.sendRequest("http://localhost/search/?query=status_code%3A0&hits=20&offset=-20");
        String response = responseHandler.readAll();
        assertThat(responseHandler.getStatus(), is(400));
        assertThat(response, containsString("offset"));
        assertThat(response, containsString("\"code\":" + com.yahoo.container.protect.Error.INVALID_QUERY_PARAMETER.code));
    }

    @Test
    public void testWebServiceStatus() {
        RequestHandlerTestDriver.MockResponseHandler responseHandler =
                driver.sendRequest("http://localhost/search/?query=web_service_status_code");
        String response = responseHandler.readAll();
        assertThat(responseHandler.getStatus(), is(406));
        assertThat(response, containsString("\"code\":" + 406));
    }

    @Test
    public void testNormalResultImplicitDefaultRendering() throws Exception {
        assertJsonResult("http://localhost?query=abc", driver);
    }

    @Test
    public void testNormalResultExplicitDefaultRendering() throws Exception {
        assertJsonResult("http://localhost?query=abc&format=default", driver);
    }

    @Test
    public void testNormalResultXmlAliasRendering() throws Exception {
        assertXmlResult("http://localhost?query=abc&format=xml", driver);
    }

    @Test
    public void testNormalResultJsonAliasRendering() throws Exception {
        assertJsonResult("http://localhost?query=abc&format=json", driver);
    }

    @Test
    public void testNormalResultExplicitDefaultRenderingFullRendererName1() throws Exception {
        assertXmlResult("http://localhost?query=abc&format=DefaultRenderer", driver);
    }

    @Test
    public void testNormalResultExplicitDefaultRenderingFullRendererName2() throws Exception {
        assertJsonResult("http://localhost?query=abc&format=JsonRenderer", driver);
    }

    @Test
    public void testResultLegacyTiledFormat() throws Exception {
        assertTiledResult("http://localhost?query=abc&format=tiled", driver);
    }

    @Test
    public void testResultLegacyPageFormat() throws Exception {
        assertPageResult("http://localhost?query=abc&format=page", driver);
    }

    private static final String xmlResult =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
            "<result total-hit-count=\"0\">\n" +
            "  <hit relevancy=\"1.0\">\n" +
            "    <field name=\"relevancy\">1.0</field>\n" +
            "    <field name=\"uri\">testHit</field>\n" +
            "  </hit>\n" +
            "</result>\n";
    private void assertXmlResult(String request, RequestHandlerTestDriver driver) throws Exception {
        assertOkResult(driver.sendRequest(request), xmlResult);
    }
    private void assertXmlResult(RequestHandlerTestDriver driver) throws Exception {
        assertXmlResult("http://localhost?query=abc", driver);
    }

    private static final String jsonResult = "{\"root\":{"
            + "\"id\":\"toplevel\",\"relevance\":1.0,\"fields\":{\"totalCount\":0},"
            + "\"children\":["
            + "{\"id\":\"testHit\",\"relevance\":1.0,\"fields\":{\"uri\":\"testHit\"}}"
            + "]}}";
    private void assertJsonResult(String request, RequestHandlerTestDriver driver) throws Exception {
        assertOkResult(driver.sendRequest(request), jsonResult);
    }

    private static final String tiledResult =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
            "<result version=\"1.0\">\n" +
            "\n" +
            "  <hit relevance=\"1.0\">\n" +
            "    <id>testHit</id>\n" +
            "    <uri>testHit</uri>\n" +
            "  </hit>\n" +
            "\n" +
            "</result>\n";
    private void assertTiledResult(String request, RequestHandlerTestDriver driver) throws Exception {
        assertOkResult(driver.sendRequest(request), tiledResult);
    }

    private static final String pageResult =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
            "<page version=\"1.0\">\n" +
            "\n" +
            "  <content>\n" +
            "    <hit relevance=\"1.0\">\n" +
            "      <id>testHit</id>\n" +
            "      <uri>testHit</uri>\n" +
            "    </hit>\n" +
            "  </content>\n" +
            "\n" +
            "</page>\n";
    private void assertPageResult(String request, RequestHandlerTestDriver driver) throws Exception {
        assertOkResult(driver.sendRequest(request), pageResult);
    }

    private void assertOkResult(RequestHandlerTestDriver.MockResponseHandler response, String expected) {
        assertEquals(expected, response.readAll());
        assertEquals(200, response.getStatus());
        assertEquals(selfHostname, response.getResponse().headers().get(myHostnameHeader).get(0));
    }

    @Test
    public void testFaultyHandlers() throws Exception {
        assertHandlerResponse(500, null, "NullReturning");
        assertHandlerResponse(500, null, "NullReturningAsync");
        assertHandlerResponse(500, null, "Throwing");
        assertHandlerResponse(500, null, "ThrowingAsync");
    }

    @Test
    public void testForwardingHandlers() throws Exception {
        assertHandlerResponse(200, jsonResult, "ForwardingAsync");

        // Fails because we are forwarding from a sync to an async handler -
        // the sync handler will respond with status 500 because the async one has not produced a response yet.
        // Disabled because this fails due to a race and is therefore unstable
        // assertHandlerResponse(500, null, "Forwarding");
    }

    private void assertHandlerResponse(int status, String responseData, String handlerName) throws Exception {
        RequestHandler forwardingHandler = configurer.getRequestHandlerRegistry().getComponent("com.yahoo.search.handler.test.SearchHandlerTestCase$" + handlerName + "Handler");
        try (RequestHandlerTestDriver forwardingDriver = new RequestHandlerTestDriver(forwardingHandler)) {
            RequestHandlerTestDriver.MockResponseHandler response = forwardingDriver.sendRequest("http://localhost/" + handlerName + "?query=test");
            response.awaitResponse();
            assertEquals("Expected HTTP status", status, response.getStatus());
            if (responseData == null)
                assertEquals("Connection closed with no data", null, response.read());
            else
                assertEquals(responseData, response.readAll());
        }
    }

    private RequestHandlerTestDriver driverWithConfig(String configDirectory) throws Exception {
        IOUtils.copyDirectory(new File(testDir, configDirectory), new File(tempDir), 1);
        generateComponentsConfigForActive();
        configurer.reloadConfig();

        SearchHandler newSearchHandler = fetchSearchHandler(configurer);
        assertTrue("Should have a new instance of the search handler", searchHandler != newSearchHandler);
        return new RequestHandlerTestDriver(newSearchHandler);
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
    }

    /** Referenced from config */
    public static class HelloWorldSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Result result = execution.search(query);
            result.hits().add(new Hit("HelloWorld"));
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

    }

}
