// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler;

import com.yahoo.container.Container;
import com.yahoo.container.core.config.testutil.HandlersConfigurerTestWrapper;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.net.HostName;
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
import com.yahoo.search.searchchain.config.test.SearchChainConfigurerTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Executors;

import static com.yahoo.yolean.Exceptions.uncheckInterrupted;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 */
public class SearchHandlerTest {

    private static final String testDir = "src/test/java/com/yahoo/search/handler/test/config";
    private static final String myHostnameHeader = "my-hostname-header";
    private static final String selfHostname = HostName.getLocalhost();

    private static String tempDir = "";

    @TempDir
    public File tempfolder;

    private RequestHandlerTestDriver driver = null;
    private HandlersConfigurerTestWrapper configurer = null;
    private MockMetric metric;
    private SearchHandler searchHandler;

    @BeforeEach
    public void startUp() throws IOException {
        File cfgDir = newFolder(tempfolder, "SearchHandlerTestCase");
        tempDir = cfgDir.getAbsolutePath();
        String configId = "dir:" + tempDir;

        IOUtils.copyDirectory(new File(testDir), cfgDir, 1); // make configs active
        generateComponentsConfigForActive();

        configurer = new HandlersConfigurerTestWrapper(new Container(), configId);
        searchHandler = (SearchHandler)configurer.getRequestHandlerRegistry().getComponent(SearchHandler.class.getName());
        metric = (MockMetric) searchHandler.metric();
        driver = new RequestHandlerTestDriver(searchHandler);
    }

    @AfterEach
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
    void testNullQuery() {
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
    void testFailing() {
        assertTrue(driver.sendRequest("http://localhost?query=test&searchChain=classLoadingError").readAll().contains("NoClassDefFoundError"));
    }

    @Test
    synchronized void testPluginError() {
        assertTrue(driver.sendRequest("http://localhost?query=test&searchChain=exceptionInPlugin").readAll().contains("NullPointerException"));
    }

    @Test
    synchronized void testWorkingReconfiguration() throws Exception {
        assertJsonResult("http://localhost?query=abc", driver);

        // reconfiguration
        IOUtils.copyDirectory(new File(testDir, "handlers2"), new File(tempDir), 1);
        generateComponentsConfigForActive();
        configurer.reloadConfig();

        // ...and check the resulting config
        SearchHandler newSearchHandler = fetchSearchHandler(configurer);
        assertNotSame(searchHandler, newSearchHandler, "Have a new instance of the search handler");
        assertNotNull(fetchSearchHandler(configurer).getSearchChainRegistry().getChain("hello"), "Have the new search chain");
        assertNull(fetchSearchHandler(configurer).getSearchChainRegistry().getChain("classLoadingError"), "Don't have the new search chain");
        try (RequestHandlerTestDriver newDriver = new RequestHandlerTestDriver(newSearchHandler)) {
            assertJsonResult("http://localhost?query=abc", newDriver);
        }
    }

    @Test
    @Disabled //TODO: Must be done at the ConfiguredApplication level, not handlers configurer? Also, this must be rewritten as the above
    synchronized void testFailedReconfiguration() throws Exception {
        assertXmlResult(driver);

        // attempt reconfiguration
        IOUtils.copyDirectory(new File(testDir, "handlersInvalid"), new File(tempDir), 1);
        generateComponentsConfigForActive();
        configurer.reloadConfig();
        SearchHandler newSearchHandler = fetchSearchHandler(configurer);
        RequestHandler newMockHandler = configurer.getRequestHandlerRegistry().getComponent("com.yahoo.search.handler.test.MockHandler");
        assertSame(searchHandler, newSearchHandler, "Reconfiguration failed: Kept the existing instance of the search handler");
        assertNull(newMockHandler, "Reconfiguration failed: No mock handler");
        try (RequestHandlerTestDriver newDriver = new RequestHandlerTestDriver(searchHandler)) {
            assertXmlResult(newDriver);
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
        IOUtils.copyDirectory(new File(testDir, "config_yql"), new File(tempDir), 1);
        generateComponentsConfigForActive();
        configurer.reloadConfig();

        SearchHandler newSearchHandler = fetchSearchHandler(configurer);
        assertNotSame(searchHandler, newSearchHandler, "Have a new instance of the search handler");
        try (RequestHandlerTestDriver newDriver = new RequestHandlerTestDriver(newSearchHandler)) {
            RequestHandlerTestDriver.MockResponseHandler responseHandler = newDriver.sendRequest(
                    "http://localhost/search/?yql=select%20*%20from%20foo%20where%20bar%20%3E%201453501295%27%3B");
            responseHandler.readAll();
            assertEquals(400, responseHandler.getStatus());
            assertEquals(Request.RequestType.READ, responseHandler.getResponse().getRequestType());
        }
    }

    @Test
    void testRequestType() throws Exception {
        IOUtils.copyDirectory(new File(testDir, "config_yql"), new File(tempDir), 1);
        generateComponentsConfigForActive();
        configurer.reloadConfig();

        SearchHandler newSearchHandler = fetchSearchHandler(configurer);
        try (RequestHandlerTestDriver newDriver = new RequestHandlerTestDriver(newSearchHandler)) {
            RequestHandlerTestDriver.MockResponseHandler responseHandler = newDriver.sendRequest(
                    "http://localhost/search/?query=foo");
            responseHandler.readAll();
            assertEquals(Request.RequestType.READ, responseHandler.getResponse().getRequestType());
        }
    }

    // Query handling takes a different code path when a query profile is active, so we test both paths.
    @Test
    void testInvalidQueryParamWithQueryProfile() throws Exception {
        try (RequestHandlerTestDriver newDriver = driverWithConfig("config_invalid_param")) {
            testInvalidQueryParam(newDriver);
        }
    }

    @Test
    void testInvalidQueryParamWithoutQueryProfile() {
        testInvalidQueryParam(driver);
    }
    private void testInvalidQueryParam(final RequestHandlerTestDriver testDriver) {
        RequestHandlerTestDriver.MockResponseHandler responseHandler =
                testDriver.sendRequest("http://localhost/search/?query=status_code%3A0&hits=20&offset=-20");
        String response = responseHandler.readAll();
        assertEquals(400, responseHandler.getStatus());
        assertTrue(response.contains("offset"));
        assertTrue(response.contains("\"code\":" + com.yahoo.container.protect.Error.ILLEGAL_QUERY.code));
    }

    @Test
    void testResultStatus() {
        assertEquals(200, httpStatus(result().build()));
        assertEquals(200, httpStatus(result().withHit().build()));
        assertEquals(200, httpStatus(result().withGroups().build()));
        assertEquals(200, httpStatus(result().withGroups().withHit().build()));

        assertEquals(500, httpStatus(result().withError().build()));
        assertEquals(200, httpStatus(result().withError().withHit().build()));
        assertEquals(200, httpStatus(result().withError().withGroups().build()));
        assertEquals(200, httpStatus(result().withError().withGroups().withHit().build()));
    }

    @Test
    void testWebServiceStatus() {
        RequestHandlerTestDriver.MockResponseHandler responseHandler =
                driver.sendRequest("http://localhost/search/?query=web_service_status_code");
        String response = responseHandler.readAll();
        assertEquals(406, responseHandler.getStatus());
        assertTrue(response.contains("\"code\":" + 406));
    }

    @Test
    void testNormalResultImplicitDefaultRendering() {
        assertJsonResult("http://localhost?query=abc", driver);
    }

    @Test
    void testNormalResultExplicitDefaultRendering() {
        assertJsonResult("http://localhost?query=abc&format=default", driver);
    }

    @Test
    void testNormalResultXmlAliasRendering() {
        assertXmlResult("http://localhost?query=abc&format=xml", driver);
    }

    @Test
    void testNormalResultJsonAliasRendering() {
        assertJsonResult("http://localhost?query=abc&format=json", driver);
    }

    @Test
    void testNormalResultExplicitDefaultRenderingFullRendererName1() {
        assertXmlResult("http://localhost?query=abc&format=XmlRenderer", driver);
    }

    @Test
    void testNormalResultExplicitDefaultRenderingFullRendererName2() {
        assertJsonResult("http://localhost?query=abc&format=JsonRenderer", driver);
    }

    private static final String xmlResult =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
            "<result total-hit-count=\"0\">\n" +
            "  <hit relevancy=\"1.0\">\n" +
            "    <field name=\"relevancy\">1.0</field>\n" +
            "    <field name=\"uri\">testHit</field>\n" +
            "  </hit>\n" +
            "</result>\n";

    private void assertXmlResult(String request, RequestHandlerTestDriver driver) {
        assertOkResult(driver.sendRequest(request), xmlResult);
    }

    private void assertXmlResult(RequestHandlerTestDriver driver) {
        assertXmlResult("http://localhost?query=abc", driver);
    }

    private static final String jsonResult = "{\"root\":{"
            + "\"id\":\"toplevel\",\"relevance\":1.0,\"fields\":{\"totalCount\":0},"
            + "\"children\":["
            + "{\"id\":\"testHit\",\"relevance\":1.0,\"fields\":{\"uri\":\"testHit\"}}"
            + "]}}";

    private void assertJsonResult(String request, RequestHandlerTestDriver driver) {
        assertOkResult(driver.sendRequest(request), jsonResult);
    }

    private void assertMetricPresent(String key) {
        for (int i = 0; i < 200; i++) {
            if (metric.metrics().containsKey(key)) return;
            uncheckInterrupted(() -> Thread.sleep(1));
        }
        fail(String.format("Could not find metric with key '%s' in '%s'", key, metric));
    }

    private void assertOkResult(RequestHandlerTestDriver.MockResponseHandler response, String expected) {
        assertEquals(expected, response.readAll());
        assertEquals(200, response.getStatus());
        assertEquals(selfHostname, response.getResponse().headers().get(myHostnameHeader).get(0));
        assertMetricPresent(SearchHandler.RENDER_LATENCY_METRIC);
    }

    @Test
    void testFaultyHandlers() throws Exception {
        assertHandlerResponse(500, null, "NullReturning");
        assertHandlerResponse(500, null, "NullReturningAsync");
        assertHandlerResponse(500, null, "Throwing");
        assertHandlerResponse(500, null, "ThrowingAsync");
    }

    @Test
    void testForwardingHandlers() throws Exception {
        assertHandlerResponse(200, jsonResult, "ForwardingAsync");

        // Fails because we are forwarding from a sync to an async handler -
        // the sync handler will respond with status 500 because the async one has not produced a response yet.
        // Disabled because this fails due to a race and is therefore unstable
        // assertHandlerResponse(500, null, "Forwarding");
    }

    private void assertHandlerResponse(int status, String responseData, String handlerName) throws Exception {
        RequestHandler forwardingHandler = configurer.getRequestHandlerRegistry().getComponent("com.yahoo.search.handler.SearchHandlerTest$" + handlerName + "Handler");
        try (RequestHandlerTestDriver forwardingDriver = new RequestHandlerTestDriver(forwardingHandler)) {
            RequestHandlerTestDriver.MockResponseHandler response = forwardingDriver.sendRequest("http://localhost/" + handlerName + "?query=test");
            response.awaitResponse();
            assertEquals(status, response.getStatus(), "Expected HTTP status");
            if (responseData == null)
                assertNull(response.read(), "Connection closed with no data");
            else
                assertEquals(responseData, response.readAll());
        }
    }

    private RequestHandlerTestDriver driverWithConfig(String configDirectory) throws Exception {
        IOUtils.copyDirectory(new File(testDir, configDirectory), new File(tempDir), 1);
        generateComponentsConfigForActive();
        configurer.reloadConfig();

        SearchHandler newSearchHandler = fetchSearchHandler(configurer);
        assertNotSame(searchHandler, newSearchHandler, "Should have a new instance of the search handler");
        return new RequestHandlerTestDriver(newSearchHandler);
    }

    private int httpStatus(Result result) {
        var jDiscRequest = com.yahoo.jdisc.http.HttpRequest.newServerRequest(driver.jDiscDriver(),
                                                                             URI.create("ignored"),
                                                                             com.yahoo.jdisc.http.HttpRequest.Method.GET);
        try {
            var request = new HttpRequest(jDiscRequest, new ByteArrayInputStream(new byte[0]));
            return SearchHandler.getHttpResponseStatus(request, result);
        }
        finally {
            jDiscRequest.release();
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
