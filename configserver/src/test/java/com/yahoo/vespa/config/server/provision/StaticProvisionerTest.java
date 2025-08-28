// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.provision;

import com.yahoo.cloud.config.ModelConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author Ulf Lilleengen
 */
public class StaticProvisionerTest {

    @Test
    public void sameHostsAreProvisioned() throws IOException, SAXException {
        ApplicationPackage app = FilesApplicationPackage.fromDir(new File("src/test/apps/hosted"), Map.of());
        InMemoryProvisioner inMemoryHostProvisioner = new InMemoryProvisioner(false, false, "host1.yahoo.com", "host2.yahoo.com", "host3.yahoo.com", "host4.yahoo.com");
        VespaModel firstModel = createModel(app, inMemoryHostProvisioner);

        StaticProvisioner staticProvisioner = new StaticProvisioner(firstModel.allocatedHosts(), null);
        VespaModel secondModel = createModel(app, staticProvisioner);

        assertModelConfig(firstModel, secondModel);
    }

    private void assertModelConfig(VespaModel firstModel, VespaModel secondModel) {
        String firstConfig = getModelConfig(firstModel);
        String secondConfig = getModelConfig(secondModel);
        assertEquals(firstConfig, secondConfig);
    }

    private String getModelConfig(VespaModel model) {
        return ConfigPayload.fromInstance(model.getConfig(ModelConfig.class, "")).toString();
    }

    private VespaModel createModel(ApplicationPackage app, HostProvisioner provisioner) throws IOException, SAXException {
        DeployState deployState = new DeployState.Builder()
                .applicationPackage(app)
                .modelHostProvisioner(provisioner)
                .endpoints(Set.of(new ContainerEndpoint("container", ApplicationClusterEndpoint.Scope.zone, List.of("c.example.com"))))
                .properties(new TestProperties()
                        .setMultitenant(true)
                        .setHostedVespa(true))
                .build();
        return new VespaModel(new NullConfigModelRegistry(), deployState);
    }

}
