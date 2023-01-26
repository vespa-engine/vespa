// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.tenant.SecretStoreExternalIdRetriever;
import com.yahoo.yolean.Exceptions;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;

import java.io.IOException;
import java.net.URI;

import static com.yahoo.config.model.api.container.ContainerServiceType.CONTAINER;
import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author olaa
 * Takes the payload received from the controller, adds external ID and acts as a proxy for the AwsParameterStoreValidationHandler result
 */
public class SecretStoreValidator {

    // http for easier testing. VespaHttpClient rewrites to https
    private static final String PROTOCOL = "http://";
    private static final String AWS_PARAMETER_VALIDATION_HANDLER_POSTFIX = ":4080/validate-secret-store";
    private final SecretStore secretStore;
    private final CloseableHttpClient httpClient = VespaHttpClientBuilder.create().build();

    public SecretStoreValidator(SecretStore secretStore) {
        this.secretStore = secretStore;
    }

    public HttpResponse validateSecretStore(Application application, SystemName system, Slime slime) {
        addExternalId(application.getId().tenant(), system, slime);
        var uri = getUri(application);
        return postRequest(uri, slime);
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
        return URI.create(PROTOCOL + hostname + AWS_PARAMETER_VALIDATION_HANDLER_POSTFIX);
    }

    private HttpResponse postRequest(URI uri, Slime slime) {
        var postRequest = new HttpPost(uri);
        var data = uncheck(() -> SlimeUtils.toJsonBytes(slime));
        var entity = new ByteArrayEntity(data, ContentType.DEFAULT_BINARY);
        postRequest.setEntity(entity);
        try {
            return new ProxyResponse(httpClient.execute(postRequest));
        } catch (IOException e) {
            return HttpErrorResponse.internalServerError(
                    String.format("Failed to post request to %s: %s", uri, Exceptions.toMessageString(e))
            );
        }
    }

    private void addExternalId(TenantName tenantName, SystemName system, Slime slime) {
        var data = slime.get();
        var name = data.field("name").asString();
        var secretName = SecretStoreExternalIdRetriever.secretName(tenantName, system, name);
        data.setString("externalId", secretStore.getSecret(secretName));
    }

}
