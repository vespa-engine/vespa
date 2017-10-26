// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.http.HttpStatus;

import java.io.IOException;

/**
 * @author mortent
 */
public class AthenzService {

    /**
     * Send instance register request to ZTS, get InstanceIdentity
     */
     public InstanceIdentity sendInstanceRegisterRequest(InstanceRegisterInformation instanceRegisterInformation, String athenzUrl) {
        try(CloseableHttpClient client = HttpClientBuilder.create().build()) {
            ObjectMapper objectMapper = new ObjectMapper();
            HttpUriRequest postRequest = RequestBuilder.post()
                    .setUri(athenzUrl + "zts/v1/instance")
                    .setEntity(new StringEntity(objectMapper.writeValueAsString(instanceRegisterInformation), ContentType.APPLICATION_JSON))
                    .build();
            CloseableHttpResponse response = client.execute(postRequest);
            if(HttpStatus.isSuccess(response.getStatusLine().getStatusCode())) {
                return objectMapper.readValue(response.getEntity().getContent(), InstanceIdentity.class);
            } else {
                String message = EntityUtils.toString(response.getEntity());
                throw new RuntimeException(String.format("Unable to get identity. http code/message: %d/%s",
                                                         response.getStatusLine().getStatusCode(), message));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
