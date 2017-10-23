package com.yahoo.container.jdisc.athenz;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.http.HttpStatus;

import java.io.IOException;

/**
 * @author mortent
 */
public class ServiceProviderApi {

    private final String providerEndpoint;

    public ServiceProviderApi(String providerEndpoint) {
        this.providerEndpoint = providerEndpoint;
    }


    /**
     * Get signed identity document from config server
     *
     * @return
     */
    String getSignedIdentityDocument() {

        // TODO Use client side auth to establish trusted secure channel
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {

            CloseableHttpResponse idDocResponse = httpClient.execute(RequestBuilder.get().setUri(providerEndpoint + "/identity-document").build());
            if (HttpStatus.isSuccess(idDocResponse.getStatusLine().getStatusCode())) {
                return EntityUtils.toString(idDocResponse.getEntity());
            } else {
                // make sure we have retried a few times (AND logged) before giving up
                throw new RuntimeException("Failed to initialize Athenz instance provider");
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

}
