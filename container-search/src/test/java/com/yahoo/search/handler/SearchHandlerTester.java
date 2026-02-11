// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler;

import com.yahoo.container.Container;
import com.yahoo.container.core.config.testutil.HandlersConfigurerTestWrapper;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.net.HostName;
import com.yahoo.search.Result;
import com.yahoo.search.searchchain.config.test.SearchChainConfigurerTestCase;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;

import static com.yahoo.yolean.Exceptions.uncheckInterrupted;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 */
public class SearchHandlerTester implements AutoCloseable {

    private static final String testDir = "src/test/java/com/yahoo/search/handler/test/config";
    private static final String myHostnameHeader = "my-hostname-header";
    private static final String selfHostname = HostName.getLocalhost();

    private static String tempDir = "";

    @TempDir
    public File tempfolder;

    public RequestHandlerTestDriver       driver     = null;
    private HandlersConfigurerTestWrapper configurer = null;
    private MockMetric                    metric;
    public SearchHandler                  searchHandler;

    public SearchHandlerTester() {
        this(null);
    }

    public SearchHandlerTester(String configFileOverrides) {
        try {
            tempfolder = Files.createTempDirectory("").toFile();
            File cfgDir = newFolder(tempfolder, "SearchHandlerTestCase");
            tempDir = cfgDir.getAbsolutePath();
            String configId = "dir:" + tempDir;

            IOUtils.copyDirectory(new File(testDir), cfgDir, 1); // make configs active
            if (configFileOverrides != null)
                copyDirectory(configFileOverrides);
            generateComponentsConfigForActive();

            configurer = new HandlersConfigurerTestWrapper(new Container(), configId);
            searchHandler = (SearchHandler)configurer.getRequestHandlerRegistry().getComponent(SearchHandler.class.getName());
            metric = (MockMetric) searchHandler.metric();
            driver = new RequestHandlerTestDriver(searchHandler);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        if (configurer != null) configurer.shutdown();
        if (driver != null) driver.close();
    }

    public RequestHandlerTestDriver.MockResponseHandler sendRequest(String url) {
        return driver.sendRequest(url);
    }

    public void reconfigure(String configFileOverrides) {
        try {
            copyDirectory(configFileOverrides);
            generateComponentsConfigForActive();
            configurer.reloadConfig();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void copyDirectory(String dir) {
        try {
            IOUtils.copyDirectory(new File(testDir, dir), new File(tempDir), 1);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateComponentsConfigForActive() throws IOException {
        File activeConfig = new File(tempDir);
        SearchChainConfigurerTestCase.
                createComponentsConfig(new File(activeConfig, "chains.cfg").getPath(),
                                       new File(activeConfig, "handlers.cfg").getPath(),
                                       new File(activeConfig, "components.cfg").getPath());
    }

    public SearchHandler fetchSearchHandler() {
        return (SearchHandler) configurer.getRequestHandlerRegistry().getComponent(SearchHandler.class.getName());
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

    public static final String jsonResult = "{\"root\":{"
                                             + "\"id\":\"toplevel\",\"relevance\":1.0,\"fields\":{\"totalCount\":0},"
                                             + "\"children\":["
                                             + "{\"id\":\"testHit\",\"relevance\":1.0,\"fields\":{\"uri\":\"testHit\"}}"
                                             + "]}}";

    public void assertJsonResult(String request) {
        assertJsonResult(request, driver);
    }

    public void assertJsonResult(String request, RequestHandlerTestDriver driver) {
        assertOkResult(driver.sendRequest(request), jsonResult);
    }

    private static final String xmlResult =
            """
                    <?xml version="1.0" encoding="utf-8" ?>
                    <result total-hit-count="0">
                      <hit relevancy="1.0">
                        <field name="relevancy">1.0</field>
                        <field name="uri">testHit</field>
                      </hit>
                    </result>
                    """;

    public void assertXmlResult(String request) {
        assertXmlResult(request, driver);
    }

    public void assertXmlResult(String request, RequestHandlerTestDriver driver) {
        assertOkResult(driver.sendRequest(request), xmlResult);
    }

    public void assertMetricPresent(String key) {
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

    public void assertHandlerResponse(int status, String responseData, String handlerName) throws Exception {
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

    public RequestHandlerTestDriver driverWithConfig(String configDirectory) throws Exception {
        IOUtils.copyDirectory(new File(testDir, configDirectory), new File(tempDir), 1);
        generateComponentsConfigForActive();
        configurer.reloadConfig();

        SearchHandler newSearchHandler = fetchSearchHandler();
        assertNotSame(searchHandler, newSearchHandler, "Should have a new instance of the search handler");
        return new RequestHandlerTestDriver(newSearchHandler);
    }

    public int httpStatus(Result result) {
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

}
