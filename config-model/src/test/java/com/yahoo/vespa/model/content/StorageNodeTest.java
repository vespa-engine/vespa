// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.config.storage.StorDevicesConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author hakonhall
 */
public class StorageNodeTest {

    private StorDevicesConfig getConfig(boolean useVdsEngine) {
        String vdsConfig = useVdsEngine ? "    <engine>" +
                "      <vds/>" +
                "    </engine>" : "";

        String servicesXml = "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services version='1.0'>" +
                "  <admin version='2.0'>" +
                "    <adminserver hostalias='node0'/>" +
                "  </admin>" +
                "  <content version='1.0' id='zoo'>" +
                "    <redundancy>1</redundancy>" +
                "    <nodes count='1' />" +
                "    <documents>" +
                "      <document type='type1' mode='streaming' />" +
                "    </documents>" +
                vdsConfig +
                "  </content>" +
                "</services>";
        List<String> searchDefinitions = ApplicationPackageUtils.generateSearchDefinition("type1");
        VespaModelCreatorWithMockPkg modelCreator =
                new VespaModelCreatorWithMockPkg(null, servicesXml, searchDefinitions);
        ApplicationPackage appPkg = modelCreator.appPkg;
        boolean failOnOutOfCapacity = true;
        InMemoryProvisioner provisioner =
                new InMemoryProvisioner(failOnOutOfCapacity, "host1.yahoo.com", "host2.yahoo.com");
        DeployProperties.Builder builder = new DeployProperties.Builder();
        DeployProperties properties = builder.hostedVespa(true).build();
        DeployState deployState = new DeployState.Builder()
                .applicationPackage(appPkg)
                .modelHostProvisioner(provisioner)
                .properties(properties)
                .build(true);
        VespaModel model = modelCreator.create(true, deployState);
        return model.getConfig(StorDevicesConfig.class, "zoo/storage/0");
    }

    @Test
    public void verifyDiskPathConfigIsSetForVds() {
        StorDevicesConfig config = getConfig(true);
        assertEquals(1, config.disk_path().size());
        assertEquals(Defaults.getDefaults().underVespaHome("var/db/vespa/vds/zoo/storage/0/disks/d0"), config.disk_path(0));
    }

    @Test
    public void verifyDiskPathConfigIsNotSetForNonHosted() {
        StorDevicesConfig config = getConfig(false);
        assertEquals(0, config.disk_path().size());
    }

}
