// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncUtils;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpRequest;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpResult;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.DeadlineExceededException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InternalFailure;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InvalidContentException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InvalidOptionValueException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.OperationNotSupportedForUnitException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.OtherMasterException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.UnknownMasterException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.server.RestApiHandler;
import com.yahoo.vespa.clustercontroller.utils.test.TestTransport;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StateRestAPITest {

    private static final ObjectMapper mapper = new ObjectMapper();

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
            assertTrue(op.isSuccess(), op.getCause().toString());
        }
        assertNotNull(op.getResult());
        return op.getResult();
    }

    private JsonNode executeOkJsonRequest(HttpRequest request) {
        HttpResult result = execute(request);
        assertEquals(200, result.getHttpReturnCode(), result.toString(true));
        assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
        return (JsonNode) result.getContent();
    }

    @Test
    void testTopLevelList() throws Exception {
        setupDummyStateApi();
        HttpResult result = execute(new HttpRequest().setPath("/cluster/v2"));
        assertEquals(200, result.getHttpReturnCode(), result.toString(true));
        assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
        assertEquals("""
                     {
                       "cluster" : {
                         "foo" : {
                           "link" : "/cluster/v2/foo"
                         },
                         "bar" : {
                           "link" : "/cluster/v2/bar"
                         }
                       }
                     }""",
                     ((JsonNode) result.getContent()).toPrettyString());
    }

    @Test
    void testClusterState() throws Exception {
        setupDummyStateApi();
        HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo"));
        assertEquals(200, result.getHttpReturnCode(), result.toString(true));
        assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
        assertEquals("""
                     {
                       "node" : {
                         "1" : {
                           "link" : "/cluster/v2/foo/1"
                         },
                         "3" : {
                           "link" : "/cluster/v2/foo/3"
                         }
                       }
                     }""",
                     ((JsonNode) result.getContent()).toPrettyString());
    }

    @Test
    void testNodeState() throws Exception {
        setupDummyStateApi();
        HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3"));
        assertEquals(200, result.getHttpReturnCode(), result.toString(true));
        assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
        String expected = """
                          {
                            "attributes" : {
                              "group" : "mygroup"
                            },
                            "state" : {
                              "current" : {
                                "state" : "up",
                                "reason" : ""
                              }
                            },
                            "metrics" : {
                              "doc-count" : 8
                            }
                          }""";
        assertEquals(expected, ((JsonNode) result.getContent()).toPrettyString());
    }

    @Test
    void testRecursiveMode() throws Exception {
        setupDummyStateApi();
        {
            JsonNode json = executeOkJsonRequest(
                    new HttpRequest().setPath("/cluster/v2").addUrlOption("recursive", "true"));
            assertEquals("""
                         {
                           "cluster" : {
                             "foo" : {
                               "node" : {
                                 "1" : {
                                   "attributes" : {
                                     "group" : "mygroup"
                                   },
                                   "state" : {
                                     "current" : {
                                       "state" : "initializing",
                                       "reason" : ""
                                     }
                                   },
                                   "metrics" : {
                                     "doc-count" : 5
                                   }
                                 },
                                 "3" : {
                                   "attributes" : {
                                     "group" : "mygroup"
                                   },
                                   "state" : {
                                     "current" : {
                                       "state" : "up",
                                       "reason" : ""
                                     }
                                   },
                                   "metrics" : {
                                     "doc-count" : 8
                                   }
                                 }
                               }
                             },
                             "bar" : {
                               "node" : {
                                 "2" : {
                                   "attributes" : {
                                     "group" : "mygroup"
                                   },
                                   "state" : {
                                     "current" : {
                                       "state" : "down",
                                       "reason" : ""
                                     }
                                   },
                                   "metrics" : {
                                     "doc-count" : 0
                                   }
                                 }
                               }
                             }
                           }
                         }""",
                         json.toPrettyString());
        }
        {
            JsonNode json = executeOkJsonRequest(new HttpRequest().setPath("/cluster/v2").addUrlOption("recursive", "1"));
            assertEquals("""
                         {
                           "cluster" : {
                             "foo" : {
                               "node" : {
                                 "1" : {
                                   "link" : "/cluster/v2/foo/1"
                                 },
                                 "3" : {
                                   "link" : "/cluster/v2/foo/3"
                                 }
                               }
                             },
                             "bar" : {
                               "node" : {
                                 "2" : {
                                   "link" : "/cluster/v2/bar/2"
                                 }
                               }
                             }
                           }
                         }""",
                         json.toPrettyString());
        }
        {
            JsonNode json = executeOkJsonRequest( new HttpRequest().setPath("/cluster/v2/foo").addUrlOption("recursive", "1"));
            assertEquals("""
                         {
                           "node" : {
                             "1" : {
                               "attributes" : {
                                 "group" : "mygroup"
                               },
                               "state" : {
                                 "current" : {
                                   "state" : "initializing",
                                   "reason" : ""
                                 }
                               },
                               "metrics" : {
                                 "doc-count" : 5
                               }
                             },
                             "3" : {
                               "attributes" : {
                                 "group" : "mygroup"
                               },
                               "state" : {
                                 "current" : {
                                   "state" : "up",
                                   "reason" : ""
                                 }
                               },
                               "metrics" : {
                                 "doc-count" : 8
                               }
                             }
                           }
                         }""", json.toPrettyString());
        }
        {
            JsonNode json = executeOkJsonRequest( new HttpRequest().setPath("/cluster/v2/foo").addUrlOption("recursive", "false"));
            assertEquals("""
                         {
                           "node" : {
                             "1" : {
                               "link" : "/cluster/v2/foo/1"
                             },
                             "3" : {
                               "link" : "/cluster/v2/foo/3"
                             }
                           }
                         }""", json.toPrettyString());
        }
    }

    private String retireAndExpectHttp200Response(Optional<String> responseWait) throws Exception {
        ObjectNode json = new ObjectNode(mapper.getNodeFactory());
        json.putObject("state").putObject("current").put("state", "retired").put("reason", "No reason");
        json.put("condition", "FORCE");
        responseWait.ifPresent(wait -> json.put("response-wait", wait));
        HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
        assertEquals(200, result.getHttpReturnCode(), result.toString(true));
        assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
        StringBuilder print = new StringBuilder();
        result.printContent(print);
        return print.toString();
    }

    @Test
    void testSetNodeState() throws Exception {
        setupDummyStateApi();
        {
            ObjectNode json = new ObjectNode(mapper.getNodeFactory());
            json.putObject("state").putObject("current").put("state", "retired").put("reason", "No reason");
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(200, result.getHttpReturnCode(), result.toString(true));
            assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
        }
        {
            JsonNode json = executeOkJsonRequest(new HttpRequest().setPath("/cluster/v2/foo/3"));
            assertEquals("""
                         {
                           "attributes" : {
                             "group" : "mygroup"
                           },
                           "state" : {
                             "current" : {
                               "state" : "retired",
                               "reason" : "No reason"
                             }
                           },
                           "metrics" : {
                             "doc-count" : 8
                           }
                         }""",
                         json.toPrettyString());
        }
        {
            ObjectNode json = new ObjectNode(mapper.getNodeFactory());
            json.putObject("state").putObject("current");
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(200, result.getHttpReturnCode(), result.toString(true));
            assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
        }
        {
            JsonNode json = executeOkJsonRequest(new HttpRequest().setPath("/cluster/v2/foo/3"));
            assertEquals("""
                         {
                           "attributes" : {
                             "group" : "mygroup"
                           },
                           "state" : {
                             "current" : {
                               "state" : "up",
                               "reason" : ""
                             }
                           },
                           "metrics" : {
                             "doc-count" : 8
                           }
                         }""",
                         json.toPrettyString());
        }
    }

    @Test
    void set_node_state_response_wait_type_is_propagated_to_handler() throws Exception {
        setupDummyStateApi();
        {
            String result = retireAndExpectHttp200Response(Optional.of("wait-until-cluster-acked"));
            assertEquals("""
                         JSON: {
                           "wasModified" : true,
                           "reason" : "DummyStateAPI wait-until-cluster-acked call"
                         }""",
                         result);
        }
        {
            String result = retireAndExpectHttp200Response(Optional.of("no-wait"));
            assertEquals("""
                         JSON: {
                           "wasModified" : true,
                           "reason" : "DummyStateAPI no-wait call"
                         }""",
                         result);
        }
    }

    @Test
    void set_node_state_response_wait_type_is_cluster_acked_by_default() throws Exception {
        setupDummyStateApi();
        String result = retireAndExpectHttp200Response(Optional.empty());
        assertEquals("""
                     JSON: {
                       "wasModified" : true,
                       "reason" : "DummyStateAPI wait-until-cluster-acked call"
                     }""",
                     result);
    }

    @Test
    void testMissingUnits() throws Exception {
        setupDummyStateApi();
        {
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/unknown"));
            assertEquals(404, result.getHttpReturnCode(), result.toString(true));
            assertEquals("No such resource 'unknown'.", result.getHttpReturnCodeDescription(), result.toString(true));
            String expected = "{\"message\":\"No such resource 'unknown'.\"}";
            assertEquals(expected, result.getContent().toString());
        }
        {
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/1234"));
            assertEquals(404, result.getHttpReturnCode(), result.toString(true));
            assertEquals("No such resource 'foo/1234'.", result.getHttpReturnCodeDescription(), result.toString(true));
            String expected = "{\"message\":\"No such resource 'foo/1234'.\"}";
            assertEquals(expected, result.getContent().toString());
        }
    }

    @Test
    void testUnknownMaster() throws Exception {
        setupDummyStateApi();
        stateApi.induceException(new UnknownMasterException());
        HttpResult result = execute(new HttpRequest().setPath("/cluster/v2"));
        assertEquals(503, result.getHttpReturnCode(), result.toString(true));
        assertEquals("Service Unavailable", result.getHttpReturnCodeDescription(), result.toString(true));
        assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
        String expected = "{\"message\":\"No known master cluster controller currently exists.\"}";
        assertEquals(expected, result.getContent().toString());
        assertNull(result.getHeader("Location"));
    }

    @Test
    void testOtherMaster() throws Exception {
        setupDummyStateApi();
        {
            stateApi.induceException(new OtherMasterException("example.com", 80));
            HttpResult result = execute(new HttpRequest().setScheme("https").setPath("/cluster/v2").addUrlOption(" %=?&", "&?%=").addUrlOption("foo", "bar"));
            assertEquals(307, result.getHttpReturnCode(), result.toString(true));
            assertEquals("Temporary Redirect", result.getHttpReturnCodeDescription(), result.toString(true));
            assertEquals("https://example.com:80/cluster/v2?%20%25%3D%3F%26=%26%3F%25%3D&foo=bar", result.getHeader("Location"), result.toString(true));
            assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
            String expected = "{\"message\":\"Cluster controller not master. Use master at example.com:80.\"}";
            assertEquals(expected, result.getContent().toString());
        }
        {
            stateApi.induceException(new OtherMasterException("example.com", 80));
            HttpResult result = execute(new HttpRequest().setScheme("http").setPath("/cluster/v2/foo"));
            assertEquals(307, result.getHttpReturnCode(), result.toString(true));
            assertEquals("Temporary Redirect", result.getHttpReturnCodeDescription(), result.toString(true));
            assertEquals("http://example.com:80/cluster/v2/foo", result.getHeader("Location"), result.toString(true));
            assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
            String expected = "{\"message\":\"Cluster controller not master. Use master at example.com:80.\"}";
            assertEquals(expected, result.getContent().toString());
        }
    }

    @Test
    void testRuntimeException() throws Exception {
        setupDummyStateApi();
        stateApi.induceException(new RuntimeException("Moahaha"));
        HttpResult result = execute(new HttpRequest().setPath("/cluster/v2"));
        assertEquals(500, result.getHttpReturnCode(), result.toString(true));
        assertEquals("Failed to process request", result.getHttpReturnCodeDescription(), result.toString(true));
        assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
        String expected = "{\"message\":\"java.lang.RuntimeException: Moahaha\"}";
        assertEquals(expected, result.getContent().toString());
    }

    @Test
    void testClientFailures() throws Exception {
        setupDummyStateApi();
        {
            stateApi.induceException(new InvalidContentException("Foo bar"));
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2"));
            assertEquals(400, result.getHttpReturnCode(), result.toString(true));
            assertEquals("Content of HTTP request had invalid data", result.getHttpReturnCodeDescription(), result.toString(true));
            assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
            String expected = "{\"message\":\"Foo bar\"}";
            assertEquals(expected, result.getContent().toString());
        }
        {
            stateApi.induceException(new InvalidOptionValueException("foo", "bar", "Foo can not be bar"));
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2"));
            assertEquals(400, result.getHttpReturnCode(), result.toString(true));
            assertEquals("Option 'foo' have invalid value 'bar'", result.getHttpReturnCodeDescription(), result.toString(true));
            assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
            String expected = "{\"message\":\"Option 'foo' have invalid value 'bar': Foo can not be bar\"}";
            assertEquals(expected, result.getContent().toString());
        }
        {
            String path[] = new String[1];
            path[0] = "foo";
            stateApi.induceException(new OperationNotSupportedForUnitException(path, "Foo"));
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2"));
            assertEquals(405, result.getHttpReturnCode(), result.toString(true));
            assertEquals("Operation not supported for resource", result.getHttpReturnCodeDescription(), result.toString(true));
            assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
            String expected = "{\"message\":\"[foo]: Foo\"}";
            assertEquals(expected, result.getContent().toString());
        }
    }

    @Test
    void testInternalFailure() throws Exception {
        setupDummyStateApi();
        {
            stateApi.induceException(new InternalFailure("Foo"));
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2"));
            assertEquals(500, result.getHttpReturnCode(), result.toString(true));
            assertEquals("Failed to process request", result.getHttpReturnCodeDescription(), result.toString(true));
            assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
            String expected = "{\"message\":\"Internal failure. Should not happen: Foo\"}";
            assertEquals(expected, result.getContent().toString());
        }
    }

    @Test
    void testInvalidRecursiveValues() throws Exception {
        setupDummyStateApi();
        {
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2").addUrlOption("recursive", "-5"));
            assertEquals(400, result.getHttpReturnCode(), result.toString(true));
            assertEquals("Option 'recursive' have invalid value '-5'", result.getHttpReturnCodeDescription(), result.toString(true));
            assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
            String expected = "{\"message\":\"Option 'recursive' have invalid value '-5': Recursive option must be true, false, 0 or a positive integer\"}";
            assertEquals(expected, result.getContent().toString());
        }
        {
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2").addUrlOption("recursive", "foo"));
            assertEquals(400, result.getHttpReturnCode(), result.toString(true));
            assertEquals("Option 'recursive' have invalid value 'foo'", result.getHttpReturnCodeDescription(), result.toString(true));
            assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
            String expected = "{\"message\":\"Option 'recursive' have invalid value 'foo': Recursive option must be true, false, 0 or a positive integer\"}";
            assertEquals(expected, result.getContent().toString());
        }
    }

    @Test
    void testInvalidJsonInSetStateRequest() throws Exception {
        setupDummyStateApi();
        {
            ObjectNode json = new ObjectNode(mapper.getNodeFactory());
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(400, result.getHttpReturnCode(), result.toString(true));
            assertEquals("Content of HTTP request had invalid data", result.getHttpReturnCodeDescription(), result.toString(true));
            assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
            assertTrue(result.getContent().toString().contains("Set state requests must contain a state object"), result.toString(true));
        }
        {
            ObjectNode json = new ObjectNode(mapper.getNodeFactory()).put("state", 5);
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(400, result.getHttpReturnCode(), result.toString(true));
            assertEquals("Content of HTTP request had invalid data", result.getHttpReturnCodeDescription(), result.toString(true));
            assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
            assertTrue(result.getContent().toString().contains("value of state is not a json object"), result.toString(true));
        }
        {
            ObjectNode json = new ObjectNode(mapper.getNodeFactory());
            json.putObject("state").put("current", 5);
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(400, result.getHttpReturnCode(), result.toString(true));
            assertEquals("Content of HTTP request had invalid data", result.getHttpReturnCodeDescription(), result.toString(true));
            assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
            assertTrue(result.getContent().toString().contains("value of state->current is not a json object"), result.toString(true));
        }
        {
            ObjectNode json = new ObjectNode(mapper.getNodeFactory());
            json.putObject("state").putObject("current").put("state", 5);
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(400, result.getHttpReturnCode(), result.toString(true));
            assertEquals("Content of HTTP request had invalid data", result.getHttpReturnCodeDescription(), result.toString(true));
            assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
            assertTrue(result.getContent().toString().contains("value of state->current->state is not a string"), result.toString(true));
        }
        {
            ObjectNode json = new ObjectNode(mapper.getNodeFactory());
            json.putObject("state").putObject("current").put("state", "down").put("reason", 5);
            HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
            assertEquals(400, result.getHttpReturnCode(), result.toString(true));
            assertEquals("Content of HTTP request had invalid data", result.getHttpReturnCodeDescription(), result.toString(true));
            assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
            assertTrue(result.getContent().toString().contains("value of state->current->reason is not a string"), result.toString(true));
        }
        {
            String result = retireAndExpectHttp400Response("Non existing condition", "no-wait");
            assertEquals("""
                         JSON: {
                           "message" : "Invalid value for condition: 'Non existing condition', expected one of 'force', 'safe'"
                         }""",
                         result);
        }
        {
            String result = retireAndExpectHttp400Response("FORCE", "banana");
            assertEquals("""
                         JSON: {
                           "message" : "Invalid value for response-wait: 'banana', expected one of 'wait-until-cluster-acked', 'no-wait'"
                         }""",
                         result);
        }
    }

    private String retireAndExpectHttp400Response(String condition, String responseWait) throws Exception {
        ObjectNode json = new ObjectNode(mapper.getNodeFactory());
        json.putObject("state").putObject("current").put("state", "retired").put("reason", "No reason");
        json.put("condition", condition);
        json.put("response-wait", responseWait);
        HttpResult result = execute(new HttpRequest().setPath("/cluster/v2/foo/3").setPostContent(json));
        assertEquals(400, result.getHttpReturnCode(), result.toString(true));
        assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
        StringBuilder print = new StringBuilder();
        result.printContent(print);
        return print.toString();
    }

    @Test
    void testInvalidPathPrefix() throws Exception {
        DummyBackend backend = new DummyBackend();
        stateApi = new DummyStateApi(backend);
        populateDummyBackend(backend);
        testTransport = new TestTransport();
        RestApiHandler handler = new RestApiHandler(stateApi);
        try {
            handler.setDefaultPathPrefix("cluster/v2");
            assertTrue(false);
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    void deadline_exceeded_exception_returns_http_504_error() throws Exception {
        setupDummyStateApi();
        stateApi.induceException(new DeadlineExceededException("argh!"));
        HttpResult result = execute(new HttpRequest().setPath("/cluster/v2"));

        assertEquals(504, result.getHttpReturnCode(), result.toString(true));
        assertEquals("Gateway Timeout", result.getHttpReturnCodeDescription(), result.toString(true));
        assertEquals("application/json", result.getHeader("Content-Type"), result.toString(true));
        String expected = "{\"message\":\"argh!\"}";
        assertEquals(expected, result.getContent().toString());
    }
}
