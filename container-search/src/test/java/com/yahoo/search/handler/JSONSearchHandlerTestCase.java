// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.container.Container;
import com.yahoo.container.core.config.testutil.HandlersConfigurerTestWrapper;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.container.protect.Error;
import com.yahoo.io.IOUtils;
import com.yahoo.net.HostName;
import com.yahoo.search.searchchain.config.test.SearchChainConfigurerTestCase;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.test.json.JsonTestHelper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests submitting the query as JSON.
 *
 * @author henrhoi
 */
public class JSONSearchHandlerTestCase {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private static final String testDir = "src/test/java/com/yahoo/search/handler/test/config";
    private static final String myHostnameHeader = "my-hostname-header";
    private static final String selfHostname = HostName.getLocalhost();

    private static String tempDir = "";
    private static final String uri = "http://localhost?";
    private static final String JSON_CONTENT_TYPE = "application/json";

    @TempDir
    public File tempfolder;

    private RequestHandlerTestDriver driver = null;
    private HandlersConfigurerTestWrapper configurer = null;
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
    void testBadJSON() {
        String json = "Not a valid JSON-string";
        RequestHandlerTestDriver.MockResponseHandler responseHandler = driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json, JSON_CONTENT_TYPE);
        String response = responseHandler.readAll();
        assertEquals(400, responseHandler.getStatus());
        assertTrue(response.contains("errors"));
        assertTrue(response.contains("\"code\":" + Error.ILLEGAL_QUERY.code));
    }

    @Test
    void testFailing() {
        ObjectNode json = jsonMapper.createObjectNode();
        json.put("query", "test");
        json.put("searchChain", "classLoadingError");
        assertTrue(driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE).readAll().contains("NoClassDefFoundError"));
    }


    @Test
    synchronized void testPluginError() {
        ObjectNode json = jsonMapper.createObjectNode();
        json.put("query", "test");
        json.put("searchChain", "exceptionInPlugin");
        assertTrue(driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE).readAll().contains("NullPointerException"));
    }

    @Test
    synchronized void testWorkingReconfiguration() throws IOException {
        ObjectNode json = jsonMapper.createObjectNode();
        json.put("query", "abc");
        assertJsonResult(json, driver);

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
            assertJsonResult(json, newDriver);
        }
    }

    @Test
    void testInvalidYqlQuery() throws IOException {
        IOUtils.copyDirectory(new File(testDir, "config_yql"), new File(tempDir), 1);
        generateComponentsConfigForActive();
        configurer.reloadConfig();

        SearchHandler newSearchHandler = fetchSearchHandler(configurer);
        assertTrue(searchHandler != newSearchHandler, "Do I have a new instance of the search handler?");
        try (RequestHandlerTestDriver newDriver = new RequestHandlerTestDriver(newSearchHandler)) {
            ObjectNode json = jsonMapper.createObjectNode();
            json.put("yql", "selectz * from foo where bar > 1453501295");
            RequestHandlerTestDriver.MockResponseHandler responseHandler = newDriver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE);
            responseHandler.readAll();
            assertEquals(400, responseHandler.getStatus());
        }
    }

    // Query handling takes a different code path when a query profile is active, so we test both paths.
    @Test
    void testInvalidQueryParamWithQueryProfile() throws IOException {
        try (RequestHandlerTestDriver newDriver = driverWithConfig("config_invalid_param")) {
            testInvalidQueryParam(newDriver);
        }
    }

    private void testInvalidQueryParam(final RequestHandlerTestDriver testDriver) {
        ObjectNode json = jsonMapper.createObjectNode();
        json.put("query", "status_code:0");
        json.put("hits", 20);
        json.put("offset", -20);
        RequestHandlerTestDriver.MockResponseHandler responseHandler =
                testDriver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE);
        String response = responseHandler.readAll();
        assertEquals(400, responseHandler.getStatus());
        assertTrue(response.contains("offset"));
        assertTrue(response.contains("\"code\":" + com.yahoo.container.protect.Error.ILLEGAL_QUERY.code));
    }

    @Test
    void testNormalResultJsonAliasRendering() {
        ObjectNode json = jsonMapper.createObjectNode();
        json.put("format", "json");
        json.put("query", "abc");
        assertJsonResult(json, driver);
    }

    @Test
    void testNullQuery() {
        ObjectNode json = jsonMapper.createObjectNode();
        json.put("format", "xml");

        assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<result total-hit-count=\"0\">\n" +
                "  <hit relevancy=\"1.0\">\n" +
                "    <field name=\"relevancy\">1.0</field>\n" +
                "    <field name=\"uri\">testHit</field>\n" +
                "  </hit>\n" +
                "</result>\n", driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE).readAll());
    }

    @Test
    void testWebServiceStatus() {
        ObjectNode json = jsonMapper.createObjectNode();
        json.put("query", "web_service_status_code");
        RequestHandlerTestDriver.MockResponseHandler responseHandler =
                driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE);
        String response = responseHandler.readAll();
        assertEquals(406, responseHandler.getStatus());
        assertTrue(response.contains("\"code\":" + 406));
    }

    @Test
    void testNormalResultImplicitDefaultRendering() {
        ObjectNode json = jsonMapper.createObjectNode();
        json.put("query", "abc");
        assertJsonResult(json, driver);
    }

    @Test
    void testNormalResultExplicitDefaultRendering() {
        ObjectNode json = jsonMapper.createObjectNode();
        json.put("query", "abc");
        json.put("format", "default");
        assertJsonResult(json, driver);
    }

    @Test
    void testNormalResultXmlAliasRendering() {
        ObjectNode json = jsonMapper.createObjectNode();
        json.put("query", "abc");
        json.put("format", "xml");
        assertXmlResult(json, driver);
    }

    @Test
    void testNormalResultExplicitDefaultRenderingFullRendererName1() {
        ObjectNode json = jsonMapper.createObjectNode();
        json.put("query", "abc");
        json.put("format", "XmlRenderer");
        assertXmlResult(json, driver);
    }

    @Test
    void testNormalResultExplicitDefaultRenderingFullRendererName2() {
        ObjectNode json = jsonMapper.createObjectNode();
        json.put("query", "abc");
        json.put("format", "JsonRenderer");
        assertJsonResult(json, driver);
    }

    private static final String xmlResult =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                    "<result total-hit-count=\"0\">\n" +
                    "  <hit relevancy=\"1.0\">\n" +
                    "    <field name=\"relevancy\">1.0</field>\n" +
                    "    <field name=\"uri\">testHit</field>\n" +
                    "  </hit>\n" +
                    "</result>\n";

    private void assertXmlResult(JsonNode json, RequestHandlerTestDriver driver) {
        assertOkResult(driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE), xmlResult);
    }

    private static final String jsonResult = "{\"root\":{"
            + "\"id\":\"toplevel\",\"relevance\":1.0,\"fields\":{\"totalCount\":0},"
            + "\"children\":["
            + "{\"id\":\"testHit\",\"relevance\":1.0,\"fields\":{\"uri\":\"testHit\"}}"
            + "]}}";

    private void assertJsonResult(JsonNode json, RequestHandlerTestDriver driver) {
        assertOkResult(driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE), jsonResult);

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

    private void assertOkResult(RequestHandlerTestDriver.MockResponseHandler response, String expected) {
        assertEquals(expected, response.readAll());
        assertEquals(200, response.getStatus());
        assertEquals(selfHostname, response.getResponse().headers().get(myHostnameHeader).get(0));
    }


    private RequestHandlerTestDriver driverWithConfig(String configDirectory) throws IOException {
        IOUtils.copyDirectory(new File(testDir, configDirectory), new File(tempDir), 1);
        generateComponentsConfigForActive();
        configurer.reloadConfig();

        SearchHandler newSearchHandler = fetchSearchHandler(configurer);
        assertTrue(searchHandler != newSearchHandler, "Do I have a new instance of the search handler?");
        return new RequestHandlerTestDriver(newSearchHandler);
    }

    @Test
    void testSelectParameters() throws IOException {
        ObjectNode json = jsonMapper.createObjectNode();

        ObjectNode select = jsonMapper.createObjectNode();

        ObjectNode where = jsonMapper.createObjectNode();
        where.put("where", "where");

        ObjectNode grouping = jsonMapper.createObjectNode();
        grouping.put("grouping", "grouping");

        select.set("where", where);
        select.set("grouping", grouping);

        json.set("select", select);

        Inspector inspector = SlimeUtils.jsonToSlime(json.toString().getBytes(StandardCharsets.UTF_8)).get();
        Map<String, String> map = new Json2SingleLevelMap(new ByteArrayInputStream(inspector.toString().getBytes(StandardCharsets.UTF_8))).parse();

        JsonNode processedWhere = jsonMapper.readTree(map.get("select.where"));
        JsonTestHelper.assertJsonEquals(where.toString(), processedWhere.toString());

        JsonNode processedGrouping = jsonMapper.readTree(map.get("select.grouping"));
        JsonTestHelper.assertJsonEquals(grouping.toString(), processedGrouping.toString());
    }

    @Test
    void testJsonQueryWithSelectWhere() {
        ObjectNode root = jsonMapper.createObjectNode();
        ObjectNode select = jsonMapper.createObjectNode();
        ObjectNode where = jsonMapper.createObjectNode();
        ArrayNode term = jsonMapper.createArrayNode();
        term.add("default");
        term.add("bad");
        where.set("contains", term);
        select.set("where", where);
        root.set("select", select);

        // Run query
        String result = driver.sendRequest(uri + "searchChain=echoingQuery", com.yahoo.jdisc.http.HttpRequest.Method.POST, root.toString(), JSON_CONTENT_TYPE).readAll();
        assertEquals("{\"root\":{\"id\":\"toplevel\",\"relevance\":1.0,\"fields\":{\"totalCount\":0},\"children\":[{\"id\":\"Query\",\"relevance\":1.0,\"fields\":{\"query\":\"select * from sources * where default contains \\\"bad\\\"\"}}]}}",
                result);
    }

    @Test
    void testJsonWithWhereAndGroupingUnderSelect() {
        String query = "{\n" +
                "  \"select\": {\n" +
                "    \"where\": {\n" +
                "      \"contains\": [\n" +
                "        \"field\",\n" +
                "        \"term\"\n" +
                "      ]\n" +
                "    },\n" +
                "    \"grouping\":[\n" +
                "      {\n" +
                "        \"all\": {\n" +
                "          \"output\": \"count()\"\n" +
                "        }\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}\n";
        String result = driver.sendRequest(uri + "searchChain=echoingQuery", com.yahoo.jdisc.http.HttpRequest.Method.POST, query, JSON_CONTENT_TYPE).readAll();

        String expected = "{\"root\":{\"id\":\"toplevel\",\"relevance\":1.0,\"fields\":{\"totalCount\":0},\"children\":[{\"id\":\"Query\",\"relevance\":1.0,\"fields\":{\"query\":\"select * from sources * where field contains \\\"term\\\" | all(output(count()))\"}}]}}";
        assertEquals(expected, result);
    }

    @Test
    void testJsonWithWhereAndGroupingSeparate() {
        String query = "{\n" +
                "  \"select.where\": {\n" +
                "    \"contains\": [\n" +
                "      \"field\",\n" +
                "      \"term\"\n" +
                "    ]\n" +
                "  },\n" +
                "  \"select.grouping\":[\n" +
                "    {\n" +
                "      \"all\": {\n" +
                "        \"output\": \"count()\"\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}\n";
        String result = driver.sendRequest(uri + "searchChain=echoingQuery", com.yahoo.jdisc.http.HttpRequest.Method.POST, query, JSON_CONTENT_TYPE).readAll();

        String expected = "{\"root\":{\"id\":\"toplevel\",\"relevance\":1.0,\"fields\":{\"totalCount\":0},\"children\":[{\"id\":\"Query\",\"relevance\":1.0,\"fields\":{\"query\":\"select * from sources * where field contains \\\"term\\\" | all(output(count()))\"}}]}}";
        assertEquals(expected, result);
    }

    @Test
    void testJsonQueryWithYQL() {
        ObjectNode root = jsonMapper.createObjectNode();
        root.put("yql", "select * from sources * where default contains 'bad';");

        // Run query
        String result = driver.sendRequest(uri + "searchChain=echoingQuery", com.yahoo.jdisc.http.HttpRequest.Method.POST, root.toString(), JSON_CONTENT_TYPE).readAll();
        assertEquals("{\"root\":{\"id\":\"toplevel\",\"relevance\":1.0,\"fields\":{\"totalCount\":0},\"children\":[{\"id\":\"Query\",\"relevance\":1.0,\"fields\":{\"query\":\"select * from sources * where default contains \\\"bad\\\"\"}}]}}",
                result);
    }

    @Test
    void testRequestMapping() {
        ObjectNode json = jsonMapper.createObjectNode();
        json.put("yql", "select * from sources * where sddocname contains \"blog_post\" limit 0 | all(group(date) max(3) order(-count())each(output(count())))");
        json.put("hits", 10);
        json.put("offset", 5);
        json.put("queryProfile", "foo");
        json.put("nocache", false);
        json.put("groupingSessionCache", false);
        json.put("searchChain", "exceptionInPlugin");
        json.put("timeout", 0);
        json.put("select", "_all");


        ObjectNode model = jsonMapper.createObjectNode();
        model.put("defaultIndex", 1);
        model.put("encoding", "json");
        model.put("filter", "default");
        model.put("language", "en");
        model.put("queryString", "abc");
        model.put("restrict", "_doc,json,xml");
        model.put("searchPath", "node1");
        model.put("sources", "source1,source2");
        model.put("type", "yql");
        json.set("model", model);

        ObjectNode ranking = jsonMapper.createObjectNode();
        ranking.put("location", "123789.89123N;128123W");
        ranking.put("features", "none");
        ranking.put("listFeatures", false);
        ranking.put("profile", "1");
        ranking.put("properties", "default");
        ranking.put("sorting", "desc");
        ranking.put("freshness", "0.05");
        ranking.put("queryCache", false);

        ObjectNode matchPhase = jsonMapper.createObjectNode();
        matchPhase.put("maxHits", "100");
        matchPhase.put("attribute", "title");
        matchPhase.put("ascending", true);

        ObjectNode diversity = jsonMapper.createObjectNode();
        diversity.put("attribute", "title");
        diversity.put("minGroups", 1);
        matchPhase.set("diversity", diversity);
        ranking.set("matchPhase", matchPhase);
        json.set("ranking", ranking);

        ObjectNode presentation = jsonMapper.createObjectNode();
        presentation.put("bolding", true);
        presentation.put("format", "json");
        presentation.put("summary", "none");
        presentation.put("template", "json");
        presentation.put("timing", false);
        json.set("presentation", presentation);

        ObjectNode collapse = jsonMapper.createObjectNode();
        collapse.put("field", "none");
        collapse.put("size", 2);
        collapse.put("summary", "default");
        json.set("collapse", collapse);

        ObjectNode trace = jsonMapper.createObjectNode();
        trace.put("level", 1);
        trace.put("timestamps", false);
        trace.put("rules", "none");
        json.set("trace", trace);

        ObjectNode pos = jsonMapper.createObjectNode();
        pos.put("ll", "1263123N;1231.9W");
        pos.put("radius", "71234m");
        pos.put("bb", "1237123W;123218N");
        pos.put("attribute", "default");
        json.set("pos", pos);

        ObjectNode streaming = jsonMapper.createObjectNode();
        streaming.put("userid", 123);
        streaming.put("groupname", "abc");
        streaming.put("selection", "none");
        streaming.put("priority", 10);
        streaming.put("maxbucketspervisitor", 5);
        json.set("streaming", streaming);

        ObjectNode rules = jsonMapper.createObjectNode();
        rules.put("off", false);
        rules.put("rulebase", "default");
        json.set("rules", rules);

        ObjectNode metrics = jsonMapper.createObjectNode();
        metrics.put("ignore", "_all");
        json.set("metrics", metrics);

        json.put("recall", "none");
        json.put("user", 123);
        json.put("nocachewrite", false);
        json.put("hitcountestimate", true);

        // Create mapping
        Inspector inspector = SlimeUtils.jsonToSlime(json.toString().getBytes(StandardCharsets.UTF_8)).get();
        Map<String, String> map = new Json2SingleLevelMap(new ByteArrayInputStream(inspector.toString().getBytes(StandardCharsets.UTF_8))).parse();

        // Create GET-request with same query
        String url = uri + "&model.sources=source1%2Csource2&select=_all&model.language=en&presentation.timing=false&pos.attribute=default&pos.radius=71234m&model.searchPath=node1&nocachewrite=false&ranking.matchPhase.maxHits=100&presentation.summary=none" +
                "&nocache=false&model.type=yql&collapse.summary=default&ranking.matchPhase.diversity.minGroups=1&ranking.location=123789.89123N%3B128123W&ranking.queryCache=false&offset=5&streaming.groupname=abc&groupingSessionCache=false" +
                "&presentation.template=json&trace.rules=none&rules.off=false&ranking.properties=default&searchChain=exceptionInPlugin&pos.ll=1263123N%3B1231.9W&ranking.sorting=desc&ranking.matchPhase.ascending=true&ranking.features=none&hitcountestimate=true" +
                "&model.filter=default&metrics.ignore=_all&collapse.field=none&ranking.profile=1&rules.rulebase=default&model.defaultIndex=1&trace.level=1&ranking.listFeatures=false&timeout=0&presentation.format=json" +
                "&yql=select+%2A+from+sources+%2A+where+sddocname+contains+%22blog_post%22+limit+0+%7C+all%28group%28date%29+max%283%29+order%28-count%28%29%29each%28output%28count%28%29%29%29%29&recall=none&streaming.maxbucketspervisitor=5" +
                "&queryProfile=foo&presentation.bolding=true&model.encoding=json&model.queryString=abc&streaming.selection=none&trace.timestamps=false&collapse.size=2&streaming.priority=10&ranking.matchPhase.diversity.attribute=title" +
                "&ranking.matchPhase.attribute=title&hits=10&streaming.userid=123&pos.bb=1237123W%3B123218N&model.restrict=_doc%2Cjson%2Cxml&ranking.freshness=0.05&user=123";

        HttpRequest request = HttpRequest.createTestRequest(url, GET);

        // Get mapping
        Map<String, String> propertyMap = request.propertyMap();
        Assertions.assertThat(propertyMap).isEqualTo(map);
    }

    @Test
    void testContentTypeParsing() {
        ObjectNode json = jsonMapper.createObjectNode();
        json.put("query", "abc");
        assertOkResult(driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), "Application/JSON; charset=utf-8"), jsonResult);
    }

    private static String createBenchmarkRequest(int num) {
        Random rand = new Random();
        StringBuilder sb = new StringBuilder("{\"yql\": \"select id from vectors where {targetHits:10, approximate:true}nearestNeighbor(vector,q);\", \"input.query(q)\":[");
        sb.append(rand.nextDouble());
        for (int i=1; i < num; i++) {
            sb.append(',');
            sb.append(rand.nextDouble());
        }
        sb.append("]}");
        return sb.toString();
    }
    @Disabled
    public void benchmarkJsonParsing() {
        String request = createBenchmarkRequest(768);
        for (int i=0; i < 10000; i++) {
            RequestHandlerTestDriver.MockResponseHandler responseHandler =
                    driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, request, JSON_CONTENT_TYPE);
            String response = responseHandler.readAll();
            assertEquals(200, responseHandler.getStatus());
            assertFalse(response.isEmpty());
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
