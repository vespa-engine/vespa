// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vdslib.VisitorStatistics;
import com.yahoo.vespaclient.ClusterDef;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class OperationHandlerImplTest {

    @Test(expected = IllegalArgumentException.class)
    public void missingClusterDef() throws RestApiException {
        List<ClusterDef> clusterDef = new ArrayList<>();
        OperationHandlerImpl.resolveClusterRoute(Optional.empty(), clusterDef);
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingClusterDefSpecifiedCluster() throws RestApiException {
        List<ClusterDef> clusterDef = new ArrayList<>();
        OperationHandlerImpl.resolveClusterRoute(Optional.of("cluster"), clusterDef);
    }

    @Test(expected = RestApiException.class)
    public void oneClusterPresentNotMatching() throws RestApiException {
        List<ClusterDef> clusterDef = new ArrayList<>();
        clusterDef.add(new ClusterDef("foo", "configId"));
        OperationHandlerImpl.resolveClusterRoute(Optional.of("cluster"), clusterDef);
    }

    @Test()
    public void oneClusterMatching() throws RestApiException {
        List<ClusterDef> clusterDef = new ArrayList<>();
        clusterDef.add(new ClusterDef("foo", "configId"));
        assertThat(OperationHandlerImpl.resolveClusterRoute(Optional.of("foo"), clusterDef),
                is("[Storage:cluster=foo;clusterconfigid=configId]"));
    }

    @Test()
    public void oneClusterMatchingManyAvailable() throws RestApiException {
        List<ClusterDef> clusterDef = new ArrayList<>();
        clusterDef.add(new ClusterDef("foo2", "configId2"));
        clusterDef.add(new ClusterDef("foo", "configId"));
        clusterDef.add(new ClusterDef("foo3", "configId2"));
        assertThat(OperationHandlerImpl.resolveClusterRoute(Optional.of("foo"), clusterDef),
                is("[Storage:cluster=foo;clusterconfigid=configId]"));
    }

    @Test()
    public void checkErrorMessage() throws RestApiException, IOException {
        List<ClusterDef> clusterDef = new ArrayList<>();
        clusterDef.add(new ClusterDef("foo2", "configId2"));
        clusterDef.add(new ClusterDef("foo", "configId"));
        clusterDef.add(new ClusterDef("foo3", "configId2"));
        try {
            OperationHandlerImpl.resolveClusterRoute(Optional.of("wrong"), clusterDef);
        } catch(RestApiException e) {
            String errorMsg = renderRestApiExceptionAsString(e);
            assertThat(errorMsg, is("{\"errors\":[{\"description\":" +
                    "\"MISSING_CLUSTER Your vespa cluster contains the content clusters foo2 (configId2), foo (configId)," +
                    " foo3 (configId2),  not wrong. Please select a valid vespa cluster.\",\"id\":-9}]}"));
            return;
        }
        fail("Expected exception");
    }

    private String renderRestApiExceptionAsString(RestApiException e) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        e.getResponse().render(stream);
        return new String( stream.toByteArray());
    }

    private class OperationHandlerImplFixture {
        DocumentAccess documentAccess = mock(DocumentAccess.class);
        AtomicReference<VisitorParameters> assignedParameters = new AtomicReference<>();
        VisitorControlHandler.CompletionCode completionCode = VisitorControlHandler.CompletionCode.SUCCESS;
        int bucketsVisited = 0;

        OperationHandlerImpl createHandler() throws Exception {
            VisitorSession visitorSession = mock(VisitorSession.class);
            // Pre-bake an already completed session
            when(documentAccess.createVisitorSession(any(VisitorParameters.class))).thenAnswer(p -> {
                VisitorParameters params = (VisitorParameters)p.getArguments()[0];
                assignedParameters.set(params);

                VisitorStatistics statistics = new VisitorStatistics();
                statistics.setBucketsVisited(bucketsVisited);
                params.getControlHandler().onVisitorStatistics(statistics);

                ProgressToken progress = new ProgressToken();
                params.getControlHandler().onProgress(progress);

                params.getControlHandler().onDone(completionCode, "bork bork");
                return visitorSession;
            });
            OperationHandlerImpl.ClusterEnumerator clusterEnumerator = () -> Arrays.asList(new ClusterDef("foo", "configId"));
            return new OperationHandlerImpl(documentAccess, clusterEnumerator, MetricReceiver.nullImplementation);
        }
    }

    private static RestUri dummyVisitUri() throws Exception {
        return new RestUri(new URI("http://localhost/document/v1/namespace/document-type/docid/"));
    }

    @Test
    public void timeout_without_buckets_visited_throws_timeout_error() throws Exception {
        OperationHandlerImplFixture fixture = new OperationHandlerImplFixture();
        fixture.completionCode = VisitorControlHandler.CompletionCode.TIMEOUT;
        fixture.bucketsVisited = 0;
        // RestApiException hides its guts internally, so cannot trivially use @Rule directly to check for error category
        try {
            OperationHandlerImpl handler = fixture.createHandler();
            handler.visit(dummyVisitUri(), "", Optional.empty(), Optional.empty());
        } catch (RestApiException e) {
            assertThat(e.getResponse().getStatus(), is(500));
            assertThat(renderRestApiExceptionAsString(e), containsString("Timed out"));
        }
    }

    @Test
    public void timeout_with_buckets_visited_does_not_throw_timeout_error() throws Exception {
        OperationHandlerImplFixture fixture = new OperationHandlerImplFixture();
        fixture.completionCode = VisitorControlHandler.CompletionCode.TIMEOUT;
        fixture.bucketsVisited = 1;

        OperationHandlerImpl handler = fixture.createHandler();
        handler.visit(dummyVisitUri(), "", Optional.empty(), Optional.empty());
    }

    @Test
    public void handler_sets_default_visitor_session_timeout_parameter() throws Exception {
        OperationHandlerImplFixture fixture = new OperationHandlerImplFixture();
        OperationHandlerImpl handler = fixture.createHandler();

        handler.visit(dummyVisitUri(), "", Optional.empty(), Optional.empty());

        assertThat(fixture.assignedParameters.get().getSessionTimeoutMs(), is((long)OperationHandlerImpl.VISIT_TIMEOUT_MS));
    }
}
