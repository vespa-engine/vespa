package com.yahoo.vespa.config.server.http;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.tenant.SecretStoreExternalIdRetriever;
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
        var requestBody = SlimeUtils.jsonToSlime("{\"awsId\":\"123\"," +
                "\"name\":\"store\"," +
                "\"role\":\"role\"," +
                "\"region\":\"some-region\"," +
                "\"parameterName\":\"some-parameter\"" +
                "}");
        var expectedSecretName = SecretStoreExternalIdRetriever.secretName(TenantName.defaultName(), SystemName.PublicCd, "store");
        when(secretStore.getSecret(expectedSecretName)).thenReturn("some-secret-value");
        stubFor(post(urlEqualTo("/validate-secret-store"))
                .withRequestBody(equalToJson("{\"awsId\":\"123\"," +
                        "\"name\":\"store\"," +
                        "\"role\":\"role\"," +
                        "\"region\":\"some-region\"," +
                        "\"parameterName\":\"some-parameter\"," +
                        "\"externalId\":\"some-secret-value\"" +
                        "}"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("is ok")));
        var response = secretStoreValidator.validateSecretStore(app, SystemName.PublicCd, requestBody);
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
        when(app.getId()).thenReturn(ApplicationId.defaultId());
        return app;
    }

    private List<HostInfo> createHostList() {
        return List.of(new HostInfo("localhost",
                List.of(new ServiceInfo("default", "container", null, null, "", "localhost"))
        ));
    }
}