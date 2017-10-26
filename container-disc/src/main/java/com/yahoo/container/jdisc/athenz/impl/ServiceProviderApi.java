// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz.impl;

import com.yahoo.vespa.defaults.Defaults;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

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
        try (CloseableHttpClient httpClient = createHttpClient()) {
            // TODO Figure out a proper way of determining the hostname matching what's registred in node-repository
            String uri = providerUri + "/identity-document?hostname=" + URLEncoder.encode(
                    Defaults.getDefaults().vespaHostname(), "UTF-8");
            HttpUriRequest request = RequestBuilder.get().setUri(uri).build();
            CloseableHttpResponse idDocResponse = httpClient.execute(request);
            String responseContent = EntityUtils.toString(idDocResponse.getEntity());
            if (HttpStatus.isSuccess(idDocResponse.getStatusLine().getStatusCode())) {
                return responseContent;
            } else {
                // TODO make sure we have retried a few times (AND logged) before giving up
                throw new RuntimeException(
                        "Failed to initialize Athenz instance provider: " +
                                idDocResponse.getStatusLine() + ": " + responseContent);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed getting signed identity document", e);
        }
    }

    // TODO Use client side auth to establish trusted secure channel
    // TODO Validate TLS certifcate of config server
    private static CloseableHttpClient createHttpClient() {
        try {
            SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
            sslContextBuilder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
            SSLConnectionSocketFactory sslSocketFactory =
                    new SSLConnectionSocketFactory(sslContextBuilder.build(),
                                                   SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            return HttpClientBuilder.create().setSSLSocketFactory(sslSocketFactory).build();
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

}
