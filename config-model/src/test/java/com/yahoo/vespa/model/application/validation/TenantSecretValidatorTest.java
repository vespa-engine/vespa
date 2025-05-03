/*
 * Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.TenantVault;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author gjoranv
 */
public class TenantSecretValidatorTest {

    Zone publicCdZone = new Zone(SystemName.PublicCd, Environment.prod, RegionName.defaultName());

    @Test
    void passes_on_services_without_secrets() throws Exception {
        runTenantSecretValidator(publicCdProperties(), servicesWithoutSecrets());
    }

    @Test
    void passes_on_existing_vault_and_secret() throws Exception {
        TestProperties properties = publicCdProperties();
        properties.setTenantVaults(List.of(new TenantVault("vId1", "vault1", "",
                                                           List.of(new TenantVault.Secret("sId1", "secret1")))));
        runTenantSecretValidator(properties, servicesWithSecrets());
    }

    @Test
    void fails_on_non_existent_vault() {
        TestProperties properties = publicCdProperties();
        properties.setTenantVaults(List.of(new TenantVault("unusedVault", "_", "", List.of())));

        var exception = assertThrows(IllegalArgumentException.class,
                                     () -> runTenantSecretValidator(properties, servicesWithSecrets()));
        assertEquals("Vault 'vault1' does not exist, or application does not have access to it", exception.getMessage());
    }

    @Test
    void fails_on_non_existent_secret() {
        TestProperties properties = publicCdProperties();
        properties.setTenantVaults(List.of(new TenantVault("vId1", "vault1", "",
                                                           List.of(new TenantVault.Secret("unusedSecret", "_")))));

        var exception = assertThrows(IllegalArgumentException.class,
                                     () -> runTenantSecretValidator(properties, servicesWithSecrets()));
        assertEquals("Secret 'secret1' is not defined in vault 'vault1'", exception.getMessage());
    }

    @Test
    void does_not_run_on_self_hosted() throws Exception {
        var properties = new TestProperties().setHostedVespa(false);
        runTenantSecretValidator(properties, servicesWithSecrets());
    }

    @Test
    void does_not_run_on_hosted_non_public() throws Exception {
        var properties = new TestProperties().setHostedVespa(true).setZone(new Zone(SystemName.cd, Environment.prod, RegionName.defaultName()));
        runTenantSecretValidator(properties, servicesWithSecrets());
    }

    private void runTenantSecretValidator(TestProperties properties, String services) throws IOException, SAXException {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(services)
                .build();
        DeployState deployState = new DeployState.Builder()
                .applicationPackage(app)
                .zone(properties.zone())
                .properties(properties)
                .endpoints(Set.of(new ContainerEndpoint("default", ApplicationClusterEndpoint.Scope.zone, List.of("default.example.com"))))
                .build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        ValidationTester.validate(new TenantSecretValidator(), model, deployState);
    }

    private TestProperties publicCdProperties() {
        return new TestProperties().setHostedVespa(true).setZone(publicCdZone);
    }

    private String servicesWithoutSecrets() {
        return "<services><container id='default' version='1.0'></container></services>";
    }

    private String servicesWithSecrets() {
        return """
                <services>
                  <container id='default' version='1.0'>
                  <secrets>
                    <apiKey vault="vault1" name="secret1" />
                    </secrets>
                  </container>
                </services>
                """;
    }

}
