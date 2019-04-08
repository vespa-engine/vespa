// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.https;

import com.yahoo.security.tls.https.VespaHttpClientBuilder.HttpToHttpsRewritingRequestInterceptor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.protocol.BasicHttpContext;
import org.junit.Test;

import java.net.URI;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author bjorncs
 */
public class VespaHttpClientBuilderTest {

    @Test
    public void request_interceptor_modifies_scheme_of_requests() {
        verifyProcessedUriMatchesExpectedOutput("http://dummyhostname:8080/a/path/to/resource?query=value",
                                                "https://dummyhostname:8080/a/path/to/resource?query=value");
    }

    @Test
    public void request_interceptor_add_handles_implicit_http_port() {
        verifyProcessedUriMatchesExpectedOutput("http://dummyhostname/a/path/to/resource?query=value",
                                                "https://dummyhostname:80/a/path/to/resource?query=value");
    }

    private static void verifyProcessedUriMatchesExpectedOutput(String inputUri, String expectedOutputUri) {
        var interceptor = new HttpToHttpsRewritingRequestInterceptor();
        HttpGet request = new HttpGet(inputUri);
        interceptor.process(request, new BasicHttpContext());
        URI modifiedUri = request.getURI();
        URI expectedUri = URI.create(expectedOutputUri);
        assertThat(modifiedUri).isEqualTo(expectedUri);
    }

}