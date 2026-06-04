// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.BackupConfig;
import com.yahoo.config.provision.BlockWindow;
import com.yahoo.config.provision.DeploymentConfigStore;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TelemetryExporterConfiguration;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.model.api.TenantVault;
import com.yahoo.vespa.config.server.session.PrepareParams;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.config.server.deploy.DeployTester.createHostedModelFactory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests that Deployment.activate() stores backup config and block windows via DeploymentConfigStore
 * when activated in a production environment, and does nothing in non-production environments.
 *
 * @author olaa
 */
public class DeploymentConfigStoreTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void stores_backup_config_and_block_windows_for_prod_deployment() {
        CapturingDeploymentConfigStore store = new CapturingDeploymentConfigStore();
        Zone prodZone = new Zone(Environment.prod, RegionName.from("us-north-1"));

        DeployTester tester = new DeployTester.Builder(temporaryFolder)
                .hostedConfigserverConfig(prodZone)
                .modelFactory(createHostedModelFactory())
                .deploymentConfigStore(store)
                .build();

        tester.deployApp("src/test/apps/hosted-with-backup/");

        assertEquals(1, store.calls.size());
        CapturingDeploymentConfigStore.Call call = store.calls.get(0);

        assertTrue(call.backup().isPresent());
        assertEquals(Duration.ofHours(24), call.backup().get().frequency());
        assertEquals(BackupConfig.Granularity.cluster, call.backup().get().granularity());

        assertEquals(1, call.blockWindows().size());
        BlockWindow window = call.blockWindows().get(0);
        assertTrue(window.revision());
        assertFalse(window.version());
        assertFalse(window.maintenance());
        assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), window.days());
        assertEquals(List.of(8, 9, 10), window.hours());
        assertEquals(ZoneId.of("UTC"), window.zone());

        assertTrue(call.telemetryExporterConfiguration().isEmpty());
    }

    @Test
    public void stores_telemetry_export_config_for_prod_deployment() {
        CapturingDeploymentConfigStore store = new CapturingDeploymentConfigStore();
        Zone prodZone = new Zone(Environment.prod, RegionName.from("us-north-1"));

        DeployTester tester = new DeployTester.Builder(temporaryFolder)
                .hostedConfigserverConfig(prodZone)
                .modelFactory(createHostedModelFactory())
                .deploymentConfigStore(store)
                .build();

        tester.deployApp("src/test/apps/hosted-with-telemetry/",
                new PrepareParams.Builder().tenantVaults(List.of(
                        new TenantVault("vault-id-1", "my-vault", "ext-id-1", List.of(
                                new TenantVault.Secret("secret-id-1", "my-token"))))));

        assertEquals(1, store.calls.size());
        TelemetryExporterConfiguration telemetry = store.calls.get(0).telemetryExporterConfiguration();
        assertFalse(telemetry.isEmpty());
        assertEquals(1, telemetry.exporters().size());

        TelemetryExporterConfiguration.Exporter exporter = telemetry.exporters().get(0);
        assertEquals("my-exporter", exporter.id());
        assertEquals(TelemetryExporterConfiguration.Exporter.ExporterType.otlphttp, exporter.type());
        assertEquals("https://otel.example.com/v1", exporter.endpoint().get());

        assertTrue(exporter.auth().isPresent());
        assertEquals("bearer", exporter.auth().get().type());
        assertEquals("my-vault", exporter.auth().get().vault());
        assertEquals("my-token", exporter.auth().get().secretName().get());

        assertEquals(List.of("default"), exporter.metricSets());

        assertEquals(1, telemetry.vaultReferences().size());
        assertEquals("vault-id-1", telemetry.vaultReferences().get(0).id());
        assertEquals("my-vault", telemetry.vaultReferences().get(0).name());
        assertEquals("ext-id-1", telemetry.vaultReferences().get(0).externalId());
    }

    @Test
    public void does_not_store_config_for_non_prod_deployment() {
        CapturingDeploymentConfigStore store = new CapturingDeploymentConfigStore();
        Zone devZone = new Zone(Environment.dev, RegionName.defaultName());

        DeployTester tester = new DeployTester.Builder(temporaryFolder)
                .hostedConfigserverConfig(devZone)
                .modelFactory(createHostedModelFactory())
                .deploymentConfigStore(store)
                .build();

        tester.deployApp("src/test/apps/hosted-with-backup/");

        assertTrue("store() must not be called for non-prod deployments", store.calls.isEmpty());
    }

    @Test
    public void does_not_store_config_when_no_store_configured() {
        Zone prodZone = new Zone(Environment.prod, RegionName.from("us-north-1"));

        // No deploymentConfigStore set — should succeed without calling any store
        DeployTester tester = new DeployTester.Builder(temporaryFolder)
                .hostedConfigserverConfig(prodZone)
                .modelFactory(createHostedModelFactory())
                .build();

        tester.deployApp("src/test/apps/hosted-with-backup/");
        // No assertion needed — simply must not throw
    }

    private static class CapturingDeploymentConfigStore implements DeploymentConfigStore {

        final List<Call> calls = new ArrayList<>();

        @Override
        public void store(ApplicationId applicationId,
                          Optional<BackupConfig> backup,
                          List<BlockWindow> blockWindows,
                          TelemetryExporterConfiguration telemetryExporterConfiguration) {
            calls.add(new Call(applicationId, backup, blockWindows, telemetryExporterConfiguration));
        }

        record Call(ApplicationId applicationId,
                    Optional<BackupConfig> backup,
                    List<BlockWindow> blockWindows,
                    TelemetryExporterConfiguration telemetryExporterConfiguration) {}
    }

}
