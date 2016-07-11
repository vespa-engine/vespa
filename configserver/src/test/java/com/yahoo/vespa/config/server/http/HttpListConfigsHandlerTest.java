// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.server.rpc.MockRequestHandler;
import com.yahoo.vespa.config.server.http.HttpListConfigsHandler.ListConfigsResponse;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

import static com.yahoo.jdisc.http.HttpResponse.Status.*;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;

/**
 * @author lulf
 * @since 5.1
 */
public class HttpListConfigsHandlerTest {
    
    private MockRequestHandler mockRequestHandler;
    private HttpListConfigsHandler handler;
    private HttpListNamedConfigsHandler namedHandler;

    @Before
    public void setUp() {
        mockRequestHandler = new MockRequestHandler();
        mockRequestHandler.setAllConfigs(new HashSet<ConfigKey<?>>() {{ 
            add(new ConfigKey<>("bar", "conf/id/", "foo"));
            }} );
        handler = new HttpListConfigsHandler(new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        }, AccessLog.voidAccessLog(), mockRequestHandler);
        namedHandler = new HttpListNamedConfigsHandler(new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        }, mockRequestHandler, AccessLog.voidAccessLog());
    }
    
    @Test
    public void require_that_handler_can_be_created() throws IOException {
        HttpResponse response = handler.handle(HttpRequest.createTestRequest("/config/v1/", GET));
        assertThat(SessionHandlerTest.getRenderedString(response), is("{\"children\":[],\"configs\":[]}"));
    }
    
    @Test
    public void require_that_named_handler_can_be_created() throws IOException {
        HttpRequest req = HttpRequest.createTestRequest("http://foo.com:8080/config/v1/foo.bar/conf/id/", GET);
        req.getJDiscRequest().parameters().put("http.path", Arrays.asList("foo.bar"));
        HttpResponse response = namedHandler.handle(req);
        assertThat(SessionHandlerTest.getRenderedString(response), is("{\"children\":[],\"configs\":[]}"));
    }
    
    @Test
    public void require_child_listings_correct() {
        Set<ConfigKey<?>> keys = new LinkedHashSet<ConfigKey<?>>() {{
            add(new ConfigKey<>("name1", "id/1", "ns1"));
            add(new ConfigKey<>("name1", "id/1", "ns1"));
            add(new ConfigKey<>("name1", "id/2", "ns1"));
            add(new ConfigKey<>("name1", "", "ns1"));
            add(new ConfigKey<>("name1", "id/1/1", "ns1"));
            add(new ConfigKey<>("name1", "id2", "ns1"));
            add(new ConfigKey<>("name1", "id/2/1", "ns1"));
            add(new ConfigKey<>("name1", "id/2/1/5/6", "ns1"));
        }};
        Set<ConfigKey<?>> keysThatHaveChild = HttpListConfigsHandler.ListConfigsResponse.keysThatHaveAChildWithSameName(keys, keys);
        assertEquals(keysThatHaveChild.size(), 3);
    }
 
    @Test
    public void require_url_building_and_mimetype_correct() {
        HttpListConfigsHandler.ListConfigsResponse resp = new ListConfigsResponse(new HashSet<ConfigKey<?>>(), null, "http://foo.com/config/v1/", true);
        assertEquals(resp.toUrl(new ConfigKey<>("myconfig", "my/id", "mynamespace"), true), "http://foo.com/config/v1/mynamespace.myconfig/my/id");
        assertEquals(resp.toUrl(new ConfigKey<>("myconfig", "my/id", "mynamespace"), false), "http://foo.com/config/v1/mynamespace.myconfig/my/id/");
        assertEquals(resp.getContentType(), "application/json");
        
    }
 
    @Test
    public void require_error_on_bad_request() throws IOException {
        HttpRequest req = HttpRequest.createTestRequest("http://foo.com:8080/config/v1/foobar/conf/id/", GET);
        HttpResponse resp = namedHandler.handle(req);
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(resp, BAD_REQUEST, HttpErrorResponse.errorCodes.BAD_REQUEST, "Illegal config, must be of form namespace.name.");
        req = HttpRequest.createTestRequest("http://foo.com:8080/config/v1/foo.barNOPE/conf/id/", GET);
        resp = namedHandler.handle(req);
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(resp, NOT_FOUND, HttpErrorResponse.errorCodes.NOT_FOUND, "No such config: foo.barNOPE");
        req = HttpRequest.createTestRequest("http://foo.com:8080/config/v1/foo.bar/conf/id/NOPE/", GET);
        resp = namedHandler.handle(req);
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(resp, NOT_FOUND, HttpErrorResponse.errorCodes.NOT_FOUND, "No such config id: conf/id/NOPE/");
    }
    
    @Test
    public void require_correct_error_response_on_no_model() throws IOException {
        mockRequestHandler.setAllConfigs(new HashSet<ConfigKey<?>>());
        HttpResponse response = namedHandler.handle(HttpRequest.createTestRequest("http://yahoo.com:8080/config/v1/foo.bar/myid/", GET));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, NOT_FOUND,
                HttpErrorResponse.errorCodes.NOT_FOUND,
                "Config not available, verify that an application package has been deployed and activated.");
    }
    
}
