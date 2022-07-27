// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.hc4;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * @author bjorncs
 */
public class VespaHttpClientBuilderTest {

    @Test
    void route_planner_modifies_scheme_of_requests() throws HttpException {
        verifyProcessedUriMatchesExpectedOutput("http://dummyhostname:8080", "https://dummyhostname:8080");
    }

    @Test
    void route_planer_handles_implicit_http_port() throws HttpException {
        verifyProcessedUriMatchesExpectedOutput("http://dummyhostname", "https://dummyhostname:80");
    }

    @Test
    void route_planer_handles_https_port() throws HttpException {
        verifyProcessedUriMatchesExpectedOutput("http://dummyhostname:443", "https://dummyhostname:443");
    }

    private static void verifyProcessedUriMatchesExpectedOutput(String inputHostString, String expectedHostString) throws HttpException {
        HttpRoutePlanner routePlanner = new VespaHttpClientBuilder.HttpToHttpsRoutePlanner();
        HttpRoute newRoute = routePlanner.determineRoute(HttpHost.create(inputHostString), mock(HttpRequest.class), new HttpClientContext());
        HttpHost target = newRoute.getTargetHost();
        assertEquals(expectedHostString, target.toURI());
    }

}