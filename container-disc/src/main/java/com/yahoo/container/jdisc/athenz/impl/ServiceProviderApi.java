// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz.impl;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.http.HttpStatus;

import java.io.IOException;
import java.net.URI;

/**
 * @author mortent
 */
public class ServiceProviderApi {

    private final URI providerUri;

    public ServiceProviderApi(String providerAddress) {
        providerUri = URI.create(String.format("https://%s:8443/athenz/v1/provider", providerAddress));
    }

    /**
     * Get signed identity document from config server
     */
    public String getSignedIdentityDocument() {

        // TODO Use client side auth to establish trusted secure channel
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {

            CloseableHttpResponse idDocResponse = httpClient.execute(RequestBuilder.get().setUri(providerUri + "/identity-document").build());
            if (HttpStatus.isSuccess(idDocResponse.getStatusLine().getStatusCode())) {
                return EntityUtils.toString(idDocResponse.getEntity());
            } else {
                // make sure we have retried a few times (AND logged) before giving up
                throw new RuntimeException("Failed to initialize Athenz instance provider");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed getting signed identity document", e);
        }
    }

}
