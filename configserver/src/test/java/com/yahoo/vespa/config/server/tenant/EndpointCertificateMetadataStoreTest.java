// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.EndpointCertificateMetadata;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.MockSecretStore;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EndpointCertificateMetadataStoreTest {

    private static final Path tenantPath = Path.createRoot();
    private static final Path endpointCertificateMetadataPath = Path.createRoot().append("tlsSecretsKeys").append("default:test:default");
    private static final ApplicationId applicationId = ApplicationId.from(TenantName.defaultName(),
            ApplicationName.from("test"), InstanceName.defaultName());

    private MockCurator curator;
    private MockSecretStore secretStore = new MockSecretStore();
    private EndpointCertificateMetadataStore endpointCertificateMetadataStore;
    private EndpointCertificateRetriever endpointCertificateRetriever;

    @Before
    public void setUp() {
        curator = new MockCurator();
        endpointCertificateMetadataStore = new EndpointCertificateMetadataStore(curator, tenantPath);
        endpointCertificateRetriever = new EndpointCertificateRetriever(secretStore);

        secretStore.put("vespa.tlskeys.tenant1--app1-cert", "CERT");
        secretStore.put("vespa.tlskeys.tenant1--app1-key", "KEY");
    }

    @Test
    public void reads_string_format() {
        curator.set(endpointCertificateMetadataPath, ("\"vespa.tlskeys.tenant1--app1\"").getBytes());

        // Read from zk and verify cert and key are available
        var endpointCertificateSecrets = endpointCertificateMetadataStore.readEndpointCertificateMetadata(applicationId)
                .flatMap(endpointCertificateRetriever::readEndpointCertificateSecrets);
        assertTrue(endpointCertificateSecrets.isPresent());
        assertEquals("KEY", endpointCertificateSecrets.get().key());
        assertEquals("CERT", endpointCertificateSecrets.get().certificate());
    }

    @Test
    public void reads_object_format() {
        curator.set(endpointCertificateMetadataPath,
                "{\"keyName\": \"vespa.tlskeys.tenant1--app1-key\", \"certName\":\"vespa.tlskeys.tenant1--app1-cert\", \"version\": 0}"
                        .getBytes());

        // Read from zk and verify cert and key are available
        var secrets = endpointCertificateMetadataStore.readEndpointCertificateMetadata(applicationId)
                .flatMap(endpointCertificateRetriever::readEndpointCertificateSecrets);
        assertTrue(secrets.isPresent());
        assertEquals("KEY", secrets.get().key());
        assertEquals("CERT", secrets.get().certificate());
    }

    @Test
    public void can_write_object_format() {
        var endpointCertificateMetadata = new EndpointCertificateMetadata("key-name", "cert-name", 1);

        endpointCertificateMetadataStore.writeEndpointCertificateMetadata(applicationId, endpointCertificateMetadata);

        assertEquals("{\"keyName\":\"key-name\",\"certName\":\"cert-name\",\"version\":1}",
                new String(curator.getData(endpointCertificateMetadataPath).orElseThrow()));
    }
}
