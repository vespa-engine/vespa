package com.yahoo.vespa.config.server.http;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.config.model.api.TenantSecretStore;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author olaa
 */
public class SecretStoreValidatorTest {

    private final SecretStore secretStore = mock(SecretStore.class);
    private final SecretStoreValidator secretStoreValidator = new SecretStoreValidator(secretStore);

    @Rule
    public final WireMockRule wireMock = new WireMockRule(options().port(4080), true);

    @Test
    public void createsCorrectRequestData() throws IOException {
        var app = mockApplication();
        var tenantSecretStore = new TenantSecretStore("store", "123", "role");
        var tenantSecretName = "some-secret";
        when(secretStore.getSecret(tenantSecretName)).thenReturn("some-secret-value");

        stubFor(post(urlEqualTo("/validate-secret-store"))
                .withRequestBody(equalToJson("{\"externalId\":\"some-secret-value\"," +
                        "\"awsId\":\"123\"," +
                        "\"name\":\"store\"," +
                        "\"role\":\"role\"" +
                        "}"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("is ok")));
        var response = secretStoreValidator.validateSecretStore(app, tenantSecretStore, tenantSecretName);
        var body = new ByteArrayOutputStream();
        response.render(body);
        assertEquals("is ok", body.toString());
    }

    private Application mockApplication() {
        var app = mock(Application.class);
        var model = mock(Model.class);
        var hostList = createHostList();
        when(app.getModel()).thenReturn(model);
        when(model.getHosts()).thenReturn(hostList);
        return app;
    }

    private List<HostInfo> createHostList() {
        return List.of(new HostInfo("localhost",
                List.of(new ServiceInfo("default", "container", null, null, "", "localhost"))
        ));
    }
}