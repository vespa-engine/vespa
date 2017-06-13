// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncUtils;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpRequest;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpResult;
import com.yahoo.vespa.clustercontroller.utils.communication.http.JsonHttpResult;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.*;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.server.RestApiHandler;
import com.yahoo.vespa.clustercontroller.utils.test.TestTransport;
import junit.framework.TestCase;
import org.codehaus.jettison.json.JSONObject;

public class StateRestAPITest extends TestCase {

    private static void populateDummyBackend(DummyBackend backend) {
        backend.addCluster(new DummyBackend.Cluster("foo")
                .addNode(new DummyBackend.Node("1")
                        .setState("initializing")
                        .setDocCount(5)
                )
                .addNode(new DummyBackend.Node("3")
                        .setDocCount(8)
                )
        ).addCluster(new DummyBackend.Cluster("bar")
                .addNode(new DummyBackend.Node("2")
                        .setState("down")
                )
        );
    }

    private DummyStateApi stateApi;
    private TestTransport testTransport;

    private void setupDummyStateApi() {
        DummyBackend backend = new DummyBackend();
        stateApi = new DummyStateApi(backend);
        populateDummyBackend(backend);
        testTransport = new TestTransport();
        RestApiHandler handler = new RestApiHandler(stateApi);
        handler.setDefaultPathPrefix("/cluster/v2");
        testTransport.addServer(handler, "host", 80, "/cluster/v2");
    }

    public void tearDown() {
        if (testTransport != null) {
            testTransport.close();
            testTransport = null;
        }
        stateApi = null;
    }

    private HttpResult execute(HttpRequest request) {
        request.setHost("host").setPort(80);
        AsyncOperation<HttpResult> op = testTransport.getClient().execute(request);
        AsyncUtils.waitFor(op);
        if (!op.isSuccess()) { // Don't call getCause() unless it fails
            assertTrue(op.getCause().toString(), op.isSuccess());
        }
        assertTrue(op.getResult() != null);
        return op.getResult();
    }
    private JSONObject executeOkJsonRequest(HttpRequest request) {
        HttpResult result = execute(request);
        assertEquals(result.toString(true), 200, result.getHttpReturnCode());
        assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
        return (JSONObject) result.getContent();
    }

    public void testTopLevelList() throws Exception {
        setupDummyStateApi();
        HttpResult result = execute(new HttpRequest().setPath("/cluster/v2"));
        assertEquals(result.toString(true), 200, result.getHttpReturnCode());
        assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
        String expected = "{\"cluster\": {\n"
                        + "  \"foo\": {\"link\": \"\\/cluster\\/v2\\/foo\"},\n"
                        + "  \"bar\": {\"link\": \"\\/cluster\\/v2\\/bar\"}\n"
                        + "}}";
        assertEquals(expected, ((JSONObject) result.getContent()).toString(2));
    }

    public void testClusterState() throws Exception {
        setupDummyStateApi();
        HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo"));
        assertEquals(result.toString(true), 200, result.getHttpReturnCode());
        assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
        String expected = "{\"node\": {\n"
                        + "  \"1\": {\"link\": \"\\/cluster\\/v2\\/foo\\/1\"},\n"
                        + "  \"3\": {\"link\": \"\\/cluster\\/v2\\/foo\\/3\"}\n"
                        + "}}";
        assertEquals(expected, ((JSONObject) result.getContent()).toString(2));
    }

    public void testNodeState() throws Exception {
        setupDummyStateApi();
        HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3"));
        assertEquals(result.toString(true), 200, result.getHttpReturnCode());
        assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
        String expected = "{\n"
                        + "  \"attributes\": {\"group\": \"mygroup\"},\n"
                        + "  \"state\": {\"current\": {\n"
                        + "    \"state\": \"up\",\n"
                        + "    \"reason\": \"\"\n"
                        + "  }},\n"
                        + "  \"metrics\": {\"doc-count\": 8}\n"
                        + "}";
        assertEquals(expected, ((JSONObject) result.getContent()).toString(2));
    }

    public void testRecursiveMode() throws Exception {
        setupDummyStateApi();
        {
            JSONObject json = executeOkJsonRequest(
                    new HttpRequest().setPath("/cluster/v2").addUrlOption("recursive", "true"));
            String expected =
                    "{\"cluster\": {\n" +
                    "  \"foo\": {\"node\": {\n" +
                    "    \"1\": {\n" +
                    "      \"attributes\": {\"group\": \"mygroup\"},\n" +
                    "      \"state\": {\"current\": {\n" +
                    "        \"state\": \"initializing\",\n" +
                    "        \"reason\": \"\"\n" +
                    "      }},\n" +
                    "      \"metrics\": {\"doc-count\": 5}\n" +
                    "    },\n" +
                    "    \"3\": {\n" +
                    "      \"attributes\": {\"group\": \"mygroup\"},\n" +
                    "      \"state\": {\"current\": {\n" +
                    "        \"state\": \"up\",\n" +
                    "        \"reason\": \"\"\n" +
                    "      }},\n" +
                    "      \"metrics\": {\"doc-count\": 8}\n" +
                    "    }\n" +
                    "  }},\n" +
                    "  \"bar\": {\"node\": {\"2\": {\n" +
                    "    \"attributes\": {\"group\": \"mygroup\"},\n" +
                    "    \"state\": {\"current\": {\n" +
                    "      \"state\": \"down\",\n" +
                    "      \"reason\": \"\"\n" +
                    "    }},\n" +
                    "    \"metrics\": {\"doc-count\": 0}\n" +
                    "  }}}\n" +
                    "}}";
            assertEquals(expected, json.toString(2));
        }
        {
            JSONObject json = executeOkJsonRequest(
                    new HttpRequest().setPath("/cluster/v2").addUrlOption("recursive", "1"));
            String expected =
                    "{\"cluster\": {\n" +
                    "  \"foo\": {\"node\": {\n" +
                    "    \"1\": {\"link\": \"\\/cluster\\/v2\\/foo\\/1\"},\n" +
                    "    \"3\": {\"link\": \"\\/cluster\\/v2\\/foo\\/3\"}\n" +
                    "  }},\n" +
                    "  \"bar\": {\"node\": {\"2\": {\"link\": \"\\/cluster\\/v2\\/bar\\/2\"}}}\n" +
                    "}}";
            // Verify that the actual link does not contain backslash. It's just an artifact of
            // jettison json output.
            assertEquals("/cluster/v2/foo/1",
                         json.getJSONObject("cluster").getJSONObject("foo").getJSONObject("node")
                                    .getJSONObject("1").getString("link"));
            assertEquals(expected, json.toString(2));
        }
        {
            JSONObject json = executeOkJsonRequest(
                    new HttpRequest().setPath("/cluster/v2/foo").addUrlOption("recursive", "1"));
            String expected =
                    "{\"node\": {\n" +
                    "  \"1\": {\n" +
                    "    \"attributes\": {\"group\": \"mygroup\"},\n" +
                    "    \"state\": {\"current\": {\n" +
                    "      \"state\": \"initializing\",\n" +
                    "      \"reason\": \"\"\n" +
                    "    }},\n" +
                    "    \"metrics\": {\"doc-count\": 5}\n" +
                    "  },\n" +
                    "  \"3\": {\n" +
                    "    \"attributes\": {\"group\": \"mygroup\"},\n" +
                    "    \"state\": {\"current\": {\n" +
                    "      \"state\": \"up\",\n" +
                    "      \"reason\": \"\"\n" +
                    "    }},\n" +
                    "    \"metrics\": {\"doc-count\": 8}\n" +
                    "  }\n" +
                    "}}";
            assertEquals(expected, json.toString(2));
        }
        {
            JSONObject json = executeOkJsonRequest(
                    new HttpRequest().setPath("/cluster/v2/foo").addUrlOption("recursive", "false"));
            String expected =
                    "{\"node\": {\n" +
                    "  \"1\": {\"link\": \"\\/cluster\\/v2\\/foo\\/1\"},\n" +
                    "  \"3\": {\"link\": \"\\/cluster\\/v2\\/foo\\/3\"}\n" +
                    "}}";
            assertEquals(expected, json.toString(2));
        }
    }

    public void testSetNodeState() throws Exception {
        setupDummyStateApi();
        {
            JSONObject json = new JSONObject().put("state", new JSONObject()
                    .put("current", new JSONObject()
                            .put("state", "retired")
                            .put("reason", "No reason")));
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(result.toString(true), 200, result.getHttpReturnCode());
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
        }
        {
            JSONObject json = executeOkJsonRequest(new HttpRequest().setPath("/cluster/v2/foo/3"));
            String expected = "{\n"
                    + "  \"attributes\": {\"group\": \"mygroup\"},\n"
                    + "  \"state\": {\"current\": {\n"
                    + "    \"state\": \"retired\",\n"
                    + "    \"reason\": \"No reason\"\n"
                    + "  }},\n"
                    + "  \"metrics\": {\"doc-count\": 8}\n"
                    + "}";
            assertEquals(json.toString(2), expected, json.toString(2));
        }
        {
            JSONObject json = new JSONObject().put("state", new JSONObject()
                    .put("current", new JSONObject()));
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(result.toString(true), 200, result.getHttpReturnCode());
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
        }
        {
            JSONObject json = executeOkJsonRequest(new HttpRequest().setPath("/cluster/v2/foo/3"));
            String expected = "{\n"
                    + "  \"attributes\": {\"group\": \"mygroup\"},\n"
                    + "  \"state\": {\"current\": {\n"
                    + "    \"state\": \"up\",\n"
                    + "    \"reason\": \"\"\n"
                    + "  }},\n"
                    + "  \"metrics\": {\"doc-count\": 8}\n"
                    + "}";
            assertEquals(json.toString(2), expected, json.toString(2));
        }
        {
            JSONObject json = new JSONObject()
                    .put("state", new JSONObject()
                            .put("current", new JSONObject()
                                    .put("state", "retired")
                                    .put("reason", "No reason")))
                    .put("condition", "FORCE");
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(result.toString(true), 200, result.getHttpReturnCode());
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
            StringBuilder print = new StringBuilder();
            result.printContent(print);
            assertEquals(print.toString(),
                    "JSON: {\n" +
                            "  \"wasModified\": true,\n" +
                            "  \"reason\": \"DummyStateAPI\"\n" +
                            "}");
        }
    }

    public void testMissingUnits() throws Exception {
        setupDummyStateApi();
        {
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/unknown"));
            assertEquals(result.toString(true), 404, result.getHttpReturnCode());
            assertEquals(result.toString(true), "No such resource 'unknown'.", result.getHttpReturnCodeDescription());
            String expected = "{\"message\":\"No such resource 'unknown'.\"}";
            assertEquals(expected, result.getContent().toString());
        }
        {
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/1234"));
            assertEquals(result.toString(true), 404, result.getHttpReturnCode());
            assertEquals(result.toString(true), "No such resource 'foo/1234'.", result.getHttpReturnCodeDescription());
            String expected = "{\"message\":\"No such resource 'foo\\/1234'.\"}";
            assertEquals(expected, result.getContent().toString());
        }
    }

    public void testUnknownMaster() throws Exception {
        setupDummyStateApi();
        stateApi.induceException(new UnknownMasterException());
        HttpResult result = execute(new HttpRequest().setPath("/cluster/v2"));
        assertEquals(result.toString(true), 503, result.getHttpReturnCode());
        assertEquals(result.toString(true), "Service Unavailable", result.getHttpReturnCodeDescription());
        assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
        String expected = "{\"message\":\"No known master cluster controller currently exists.\"}";
        assertEquals(expected, result.getContent().toString());
        assertTrue(result.getHeader("Location") == null);
    }

    public void testOtherMaster() throws Exception {
        setupDummyStateApi();
        {
            stateApi.induceException(new OtherMasterException("example.com", 80));
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2").addUrlOption(" %=?&", "&?%=").addUrlOption("foo", "bar"));
            assertEquals(result.toString(true), 307, result.getHttpReturnCode());
            assertEquals(result.toString(true), "Temporary Redirect", result.getHttpReturnCodeDescription());
            assertEquals(result.toString(true), "http://example.com:80/cluster/v2?%20%25%3D%3F%26=%26%3F%25%3D&foo=bar", result.getHeader("Location"));
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
            String expected = "{\"message\":\"Cluster controller not master. Use master at example.com:80.\"}";
            assertEquals(expected, result.getContent().toString());
        }
        {
            stateApi.induceException(new OtherMasterException("example.com", 80));
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo"));
            assertEquals(result.toString(true), 307, result.getHttpReturnCode());
            assertEquals(result.toString(true), "Temporary Redirect", result.getHttpReturnCodeDescription());
            assertEquals(result.toString(true), "http://example.com:80/cluster/v2/foo", result.getHeader("Location"));
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
            String expected = "{\"message\":\"Cluster controller not master. Use master at example.com:80.\"}";
            assertEquals(expected, result.getContent().toString());
        }
    }

    public void testRuntimeException() throws Exception {
        setupDummyStateApi();
        stateApi.induceException(new RuntimeException("Moahaha"));
        HttpResult result = execute(new HttpRequest().setPath("/cluster/v2"));
        assertEquals(result.toString(true), 500, result.getHttpReturnCode());
        assertEquals(result.toString(true), "Failed to process request", result.getHttpReturnCodeDescription());
        assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
        String expected = "{\"message\":\"java.lang.RuntimeException: Moahaha\"}";
        assertEquals(expected, result.getContent().toString());
    }

    public void testClientFailures() throws Exception {
        setupDummyStateApi();
        {
            stateApi.induceException(new InvalidContentException("Foo bar"));
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2"));
            assertEquals(result.toString(true), 400, result.getHttpReturnCode());
            assertEquals(result.toString(true), "Content of HTTP request had invalid data", result.getHttpReturnCodeDescription());
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
            String expected = "{\"message\":\"Foo bar\"}";
            assertEquals(expected, result.getContent().toString());
        }
        {
            stateApi.induceException(new InvalidOptionValueException("foo", "bar", "Foo can not be bar"));
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2"));
            assertEquals(result.toString(true), 400, result.getHttpReturnCode());
            assertEquals(result.toString(true), "Option 'foo' have invalid value 'bar'", result.getHttpReturnCodeDescription());
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
            String expected = "{\"message\":\"Option 'foo' have invalid value 'bar': Foo can not be bar\"}";
            assertEquals(expected, result.getContent().toString());
        }
        {
            String path[] = new String[1];
            path[0] = "foo";
            stateApi.induceException(new OperationNotSupportedForUnitException(path, "Foo"));
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2"));
            assertEquals(result.toString(true), 405, result.getHttpReturnCode());
            assertEquals(result.toString(true), "Operation not supported for resource", result.getHttpReturnCodeDescription());
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
            String expected = "{\"message\":\"[foo]: Foo\"}";
            assertEquals(expected, result.getContent().toString());
        }
    }

    public void testInternalFailure() throws Exception {
        setupDummyStateApi();
        {
            stateApi.induceException(new InternalFailure("Foo"));
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2"));
            assertEquals(result.toString(true), 500, result.getHttpReturnCode());
            assertEquals(result.toString(true), "Failed to process request", result.getHttpReturnCodeDescription());
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
            String expected = "{\"message\":\"Internal failure. Should not happen: Foo\"}";
            assertEquals(expected, result.getContent().toString());
        }
    }

    public void testInvalidRecursiveValues() throws Exception {
        setupDummyStateApi();
        {
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2").addUrlOption("recursive", "-5"));
            assertEquals(result.toString(true), 400, result.getHttpReturnCode());
            assertEquals(result.toString(true), "Option 'recursive' have invalid value '-5'", result.getHttpReturnCodeDescription());
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
            String expected = "{\"message\":\"Option 'recursive' have invalid value '-5': Recursive option must be true, false, 0 or a positive integer\"}";
            assertEquals(expected, result.getContent().toString());
        }
        {
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2").addUrlOption("recursive", "foo"));
            assertEquals(result.toString(true), 400, result.getHttpReturnCode());
            assertEquals(result.toString(true), "Option 'recursive' have invalid value 'foo'", result.getHttpReturnCodeDescription());
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
            String expected = "{\"message\":\"Option 'recursive' have invalid value 'foo': Recursive option must be true, false, 0 or a positive integer\"}";
            assertEquals(expected, result.getContent().toString());
        }
    }

    public void testInvalidJsonInSetStateRequest() throws Exception {
        setupDummyStateApi();
        {
            JSONObject json = new JSONObject();
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(result.toString(true), 400, result.getHttpReturnCode());
            assertEquals(result.toString(true), "Content of HTTP request had invalid data", result.getHttpReturnCodeDescription());
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
            assertTrue(result.toString(true), result.getContent().toString().contains("Set state requests must contain a state object"));
        }
        {
            JSONObject json = new JSONObject().put("state", 5);
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(result.toString(true), 400, result.getHttpReturnCode());
            assertEquals(result.toString(true), "Content of HTTP request had invalid data", result.getHttpReturnCodeDescription());
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
            assertTrue(result.toString(true), result.getContent().toString().contains("value of state is not a json object"));
        }
        {
            JSONObject json = new JSONObject().put("state", new JSONObject()
                    .put("current", 5));
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(result.toString(true), 400, result.getHttpReturnCode());
            assertEquals(result.toString(true), "Content of HTTP request had invalid data", result.getHttpReturnCodeDescription());
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
            assertTrue(result.toString(true), result.getContent().toString().contains("value of state->current is not a json object"));
        }
        {
            JSONObject json = new JSONObject().put("state", new JSONObject()
                    .put("current", new JSONObject().put("state", 5)));
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(result.toString(true), 400, result.getHttpReturnCode());
            assertEquals(result.toString(true), "Content of HTTP request had invalid data", result.getHttpReturnCodeDescription());
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
            assertTrue(result.toString(true), result.getContent().toString().contains("value of state->current->state is not a string"));
        }
        {
            JSONObject json = new JSONObject().put("state", new JSONObject()
                    .put("current", new JSONObject().put("state", "down").put("reason", 5)));
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(result.toString(true), 400, result.getHttpReturnCode());
            assertEquals(result.toString(true), "Content of HTTP request had invalid data", result.getHttpReturnCodeDescription());
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
            assertTrue(result.toString(true), result.getContent().toString().contains("value of state->current->reason is not a string"));
        }
        {
            JSONObject json = new JSONObject()
                    .put("state", new JSONObject()
                            .put("current", new JSONObject()
                                    .put("state", "retired")
                                    .put("reason", "No reason")))
                    .put("condition", "Non existing condition");
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(result.toString(true), 500, result.getHttpReturnCode());
            assertEquals(result.toString(true), "application/json", result.getHeader("Content-Type"));
            StringBuilder print = new StringBuilder();
            result.printContent(print);
            assertEquals(print.toString(),
                    "JSON: {\"message\": \"java.lang.IllegalArgumentException: No enum constant " +
                            "com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest." +
                            "Condition.Non existing condition\"}");
        }
    }

    public void testInvalidPathPrefix() throws Exception {
        DummyBackend backend = new DummyBackend();
        stateApi = new DummyStateApi(backend);
        populateDummyBackend(backend);
        testTransport = new TestTransport();
        RestApiHandler handler = new RestApiHandler(stateApi);
        try{
            handler.setDefaultPathPrefix("cluster/v2");
            assertTrue(false);
        } catch (IllegalArgumentException e) {
        }
    }
}
