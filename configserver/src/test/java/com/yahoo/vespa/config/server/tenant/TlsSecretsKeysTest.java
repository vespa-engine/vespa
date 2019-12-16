package com.yahoo.vespa.config.server.tenant;

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

public class TlsSecretsKeysTest {

    private static final Path tenantPath = Path.createRoot();
    private static final Path tlsSecretsKeysPath = Path.createRoot().append("tlsSecretsKeys").append("default:test:default");
    private static final String tlskey = "vespa.tlskeys.tenant1--app1";
    private static final ApplicationId applicationId = ApplicationId.from(TenantName.defaultName(),
            ApplicationName.from("test"), InstanceName.defaultName());

    private MockCurator curator;
    private MockSecretStore secretStore = new MockSecretStore();
    private TlsSecretsKeys tlsSecretsKeys;

    @Before
    public void setUp() {
        curator = new MockCurator();
        tlsSecretsKeys = new TlsSecretsKeys(curator, tenantPath, secretStore);
        secretStore.put(tlskey + "-cert", "CERT");
        secretStore.put(tlskey + "-key", "KEY");
    }

    @Test
    public void reads_string_format() {
        curator.set(tlsSecretsKeysPath, ('"' + tlskey + '"').getBytes());

        // Read from zk and verify cert and key are available
        var tlsSecrets = tlsSecretsKeys.readTlsSecretsKeyFromZookeeper(applicationId);
        assertTrue(tlsSecrets.isPresent());
        assertEquals("KEY", tlsSecrets.get().key());
        assertEquals("CERT", tlsSecrets.get().certificate());
    }

    @Test
    public void reads_object_format() {
        curator.set(tlsSecretsKeysPath,
                "{\"keyName\": \"vespa.tlskeys.tenant1--app1-key\", \"certName\":\"vespa.tlskeys.tenant1--app1-cert\", \"version\": 0}"
                        .getBytes());

        // Read from zk and verify cert and key are available
        var tlsSecrets = tlsSecretsKeys.readTlsSecretsKeyFromZookeeper(applicationId);
        assertTrue(tlsSecrets.isPresent());
        assertEquals("KEY", tlsSecrets.get().key());
        assertEquals("CERT", tlsSecrets.get().certificate());
    }

    @Test
    public void can_write_object_format() {
        var tlsSecretsMetadata = new TlsSecretsKeys.TlsSecretsMetadata();
        tlsSecretsMetadata.certName = "cert-name";
        tlsSecretsMetadata.keyName = "key-name";
        tlsSecretsMetadata.version = 1;

        tlsSecretsKeys.writeTlsSecretsMetadata(applicationId, tlsSecretsMetadata);

        assertEquals("{\"certName\":\"cert-name\",\"keyName\":\"key-name\",\"version\":1}",
                new String(curator.getData(tlsSecretsKeysPath).get()));
    }
}