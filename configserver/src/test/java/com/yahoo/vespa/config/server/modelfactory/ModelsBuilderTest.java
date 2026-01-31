// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.api.Provisioned;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.MockProvisioner;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.model.VespaModelFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

import static org.junit.Assert.assertNotNull;

/**
 * Tests for ModelsBuilder, specifically the createStaticProvisioner method.
 *
 * @author bjorncs
 */
public class ModelsBuilderTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * Tests that createStaticProvisioner does not read hosts.xml in hosted Vespa,
     * even when allocatedHosts is empty. This is important for customers migrating
     * from self-hosted Vespa who may have a hosts.xml with invalid content (e.g.,
     * duplicate hostnames) that should be ignored in cloud deployments.
     */
    @Test
    public void testCreateStaticProvisionerIgnoresHostsXmlInHostedVespa() throws IOException {
        // Create an application package with a hosts.xml containing duplicate hostnames
        // This would cause an error if hosts.xml was parsed
        File appDir = temporaryFolder.newFolder("app");
        File servicesXml = new File(appDir, "services.xml");
        File hostsXml = new File(appDir, "hosts.xml");

        Files.writeString(servicesXml.toPath(), """
                <?xml version="1.0" encoding="utf-8" ?>
                <services version="1.0">
                    <container id="default" version="1.0">
                        <nodes count="1"/>
                    </container>
                </services>
                """);

        // hosts.xml with duplicate hostname - would fail if parsed
        Files.writeString(hostsXml.toPath(), """
                <?xml version="1.0" encoding="utf-8" ?>
                <hosts>
                    <host name="myhost.example.com">
                        <alias>node1</alias>
                    </host>
                    <host name="myhost.example.com">
                        <alias>node2</alias>
                    </host>
                </hosts>
                """);

        ApplicationPackage applicationPackage = FilesApplicationPackage.fromDir(appDir, Map.of());

        // Create a hosted config
        ConfigserverConfig.Builder configBuilder = new ConfigserverConfig.Builder();
        configBuilder.hostedVespa(true);
        ConfigserverConfig hostedConfig = new ConfigserverConfig(configBuilder);

        // Create HostProvisionerProvider with a provisioner (simulating hosted Vespa)
        MockProvisioner provisioner = new MockProvisioner();
        HostProvisionerProvider hostProvisionerProvider = HostProvisionerProvider.withProvisioner(provisioner, hostedConfig);

        // Create a test ModelsBuilder
        TestModelsBuilder modelsBuilder = new TestModelsBuilder(
                hostedConfig,
                Zone.defaultZone(),
                hostProvisionerProvider
        );

        // This should succeed without reading hosts.xml
        // If hosts.xml was read, it would fail with "Multiple entries with same key"
        ApplicationId applicationId = ApplicationId.from("tenant", "app", "default");
        Provisioned provisioned = new Provisioned();
        HostProvisioner result = modelsBuilder.createStaticProvisioner(applicationPackage, applicationId, provisioned);

        assertNotNull("Should return a provisioner without reading hosts.xml", result);
    }

    /**
     * A concrete implementation of ModelsBuilder for testing purposes.
     */
    private static class TestModelsBuilder extends ModelsBuilder<ModelResult> {

        TestModelsBuilder(ConfigserverConfig configserverConfig,
                         Zone zone,
                         HostProvisionerProvider hostProvisionerProvider) {
            super(new ModelFactoryRegistry(List.of(VespaModelFactory.createTestFactory())),
                  configserverConfig,
                  zone,
                  hostProvisionerProvider,
                  new TestDeployLogger());
        }

        @Override
        protected ModelResult buildModelVersion(ModelFactory modelFactory,
                                                ApplicationPackage applicationPackage,
                                                ApplicationId applicationId,
                                                Optional<DockerImage> dockerImageRepository,
                                                Version wantedNodeVespaVersion) {
            throw new UnsupportedOperationException("Not implemented for test");
        }
    }

    private static class TestDeployLogger implements DeployLogger {
        @Override
        public void log(Level level, String message) {
            // no-op for testing
        }
    }

}
