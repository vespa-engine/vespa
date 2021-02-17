// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import ai.vespa.util.http.VespaHttpClientBuilder;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.TenantSecretStore;
import com.yahoo.yolean.Exceptions;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.net.URI;

import static com.yahoo.config.model.api.container.ContainerServiceType.CONTAINER;
import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author olaa
 */
public class SecretStoreValidator {

    private static final String AWS_PARAMETER_VALIDATION_HANDLER_POSTFIX = ":4080/validate-secret-store";
    private final SecretStore secretStore;
    private final CloseableHttpClient httpClient = VespaHttpClientBuilder.create().build();

    public SecretStoreValidator(SecretStore secretStore) {
        this.secretStore = secretStore;
    }

    public HttpResponse validateSecretStore(Application application, TenantSecretStore tenantSecretStore, String tenantSecretName) {
        var slime = toSlime(tenantSecretStore, tenantSecretName);
        var uri = getUri(application);
        return postRequest(uri, slime);
    }

    private Slime toSlime(TenantSecretStore tenantSecretStore, String tenantSecretName) {
        var slime = new Slime();
        var cursor = slime.setObject();
        cursor.setString("externalId", secretStore.getSecret(tenantSecretName));
        cursor.setString("awsId", tenantSecretStore.getAwsId());
        cursor.setString("name", tenantSecretStore.getName());
        cursor.setString("role", tenantSecretStore.getRole());
        return slime;
    }

    private URI getUri(Application application) {
        var hostname = application.getModel().getHosts()
                .stream()
                .filter(hostInfo ->
                    hostInfo.getServices()
                            .stream()
                            .filter(service -> CONTAINER.serviceName.equals(service.getServiceType()))
                            .count() > 0)
                .map(HostInfo::getHostname)
                .findFirst().orElseThrow();
        return URI.create(hostname + AWS_PARAMETER_VALIDATION_HANDLER_POSTFIX);
    }

    private HttpResponse postRequest(URI uri, Slime slime) {
        var postRequest = new HttpPost(uri);
        var data = uncheck(() -> SlimeUtils.toJsonBytes(slime));
        var entity = new ByteArrayEntity(data);
        postRequest.setEntity(entity);
        try (CloseableHttpResponse response = httpClient.execute(postRequest)){
            return new ProxyResponse(response);
        } catch (IOException e) {
            return HttpErrorResponse.internalServerError(
                    String.format("Failed to post request to %s: %s", uri, Exceptions.toMessageString(e))
            );
        }
    }

}
