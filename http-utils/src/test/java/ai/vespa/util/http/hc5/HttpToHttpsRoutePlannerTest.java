// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.hc5;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
public class HttpToHttpsRoutePlannerTest {

    final HttpToHttpsRoutePlanner planner = new HttpToHttpsRoutePlanner();

    @Test
    void verifySchemeMustBeHttp() throws HttpException {
        try {
            planner.determineRoute(new HttpHost("https", "host", 1), new HttpClientContext());
        }
        catch (IllegalArgumentException e) {
            assertEquals("Scheme must be 'http' when using HttpToHttpsRoutePlanner", e.getMessage());
        }
    }

    @Test
    void verifyPortMustBeSet() throws HttpException {
        try {
            planner.determineRoute(new HttpHost("http", "host", -1), new HttpClientContext());
        }
        catch (IllegalArgumentException e) {
            assertEquals("Port must be set when using HttpToHttpsRoutePlanner", e.getMessage());
        }
    }


    @Test
    void verifyProxyIsDisallowed() throws HttpException {
        HttpClientContext context = new HttpClientContext();
        context.setRequestConfig(RequestConfig.custom().setProxy(new HttpHost("proxy")).build());
        try {
            planner.determineRoute(new HttpHost("http", "host", 1), context);
        }
        catch (IllegalArgumentException e) {
            assertEquals("Proxies are not supported with HttpToHttpsRoutePlanner", e.getMessage());
        }
    }

    @Test
    void verifySchemeIsRewritten() throws HttpException {
        assertEquals(new HttpRoute(new HttpHost("https", "host", 1)),
                planner.determineRoute(new HttpHost("http", "host", 1), new HttpClientContext()));
    }

}
