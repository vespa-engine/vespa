// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.processors;

import com.yahoo.component.chain.Chain;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.*;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.execution.chain.ChainRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class MockUserDatabaseClientTest {

    private TestDriver driver;

    @Test
    void testClientExampleProcessor() {
        Request request = null;
        try {
            Chain<Processor> chain = new Chain<>("default", new MockUserDatabaseClient());
            setupJDisc(Collections.singletonList(chain));
            request = createRequest();
            Response response = Execution.createRoot(chain, 0, Execution.Environment.createEmpty()).process(request);
            MockUserDatabaseClient.User user = (MockUserDatabaseClient.User) response.data().request().properties().get("User");
            assertNotNull(user);
            assertEquals("foo", user.getId());
        }
        finally {
            release(request);
        }
    }

    /** Creates a request which has an underlying jdisc request, which is needed to make the outgoing request */
    private Request createRequest() {
        com.yahoo.jdisc.http.HttpRequest jdiscRequest = HttpRequest.newServerRequest(driver, URI.create("http://localhost/"));
        com.yahoo.container.jdisc.HttpRequest containerRequest = new com.yahoo.container.jdisc.HttpRequest(jdiscRequest,new ByteArrayInputStream(new byte[0]));

        Request request = new Request();
        request.properties().set("jdisc.request",containerRequest);
        request.properties().set("timeout",1000);
        return request;
    }

    private void release(Request request) {
        if (request==null) return;
       ((com.yahoo.container.jdisc.HttpRequest)request.properties().get("jdisc.request")).getJDiscRequest().release();
    }

    private void setupJDisc(Collection<Chain<Processor>> chains) {
        driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();

        ChainRegistry<Processor> registry = new ChainRegistry<>();
        for (Chain<Processor> chain : chains)
            registry.register(chain.getId(), chain);

        builder.clientBindings().bind("pio://endpoint/*", new MockUserDatabaseRequestHandler());
        driver.activateContainer(builder);
    }

    @AfterEach
    public void shutDownDisc() {
        assertTrue(driver.close());
    }

    private static class MockUserDatabaseRequestHandler extends AbstractRequestHandler {

        @Override
        public ContentChannel handleRequest(com.yahoo.jdisc.Request request, ResponseHandler responseHandler) {
            FastContentWriter writer = new FastContentWriter(ResponseDispatch.newInstance(com.yahoo.jdisc.Response.Status.OK).connect(responseHandler));
            try {
                writer.write("id=foo\n");
            } finally {
                writer.close();
            }
            return null;
        }

    }

}
