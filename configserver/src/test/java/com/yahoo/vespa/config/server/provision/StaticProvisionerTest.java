// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.provision;

import com.yahoo.cloud.config.ModelConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author Ulf Lilleengen
 */
public class StaticProvisionerTest {

    @Test
    public void sameHostsAreProvisioned() throws IOException, SAXException {
        ApplicationPackage app = FilesApplicationPackage.fromFile(new File("src/test/apps/hosted"));
        InMemoryProvisioner inMemoryHostProvisioner = new InMemoryProvisioner(false, "host1.yahoo.com", "host2.yahoo.com", "host3.yahoo.com", "host4.yahoo.com");
        VespaModel firstModel = createModel(app, inMemoryHostProvisioner);

        StaticProvisioner staticProvisioner = new StaticProvisioner(firstModel.allocatedHosts());
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
                .properties(new DeployProperties.Builder()
                        .multitenant(true)
                        .hostedVespa(true)
                        .build())
                .build(true);
        return new VespaModel(new NullConfigModelRegistry(), deployState);
    }

}
