// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.provision.Hosts;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 * @author bratseth
 */
public class DedicatedAdminV4Test {

    private static final String services =
                "<services>" +
                "  <admin version='4.0'>" +
                "    <slobroks><nodes count='2' dedicated='true'/></slobroks>" +
                "    <logservers><nodes count='1' dedicated='true'/></logservers>" +
                "  </admin>" +
                "</services>";

    @Test
    public void testModelBuilding() throws IOException, SAXException {
        String hosts = "<hosts>"
                + " <host name=\"myhost0\">"
                + "  <alias>node0</alias>"
                + " </host>"
                + " <host name=\"myhost1\">"
                + "  <alias>node1</alias>"
                + " </host>"
                + " <host name=\"myhost2\">"
                + "  <alias>node2</alias>"
                + " </host>"
                + "</hosts>";
        ApplicationPackage app = new MockApplicationPackage.Builder().withHosts(hosts).withServices(services).build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder().applicationPackage(app).modelHostProvisioner(new InMemoryProvisioner(Hosts.readFrom(app.getHosts()), true)).build());
        assertEquals(3, model.getHosts().size());

        Set<String> serviceNames0 = serviceNames(model.getConfig(SentinelConfig.class, "hosts/myhost0"));
        assertEquals(3, serviceNames0.size());
        assertTrue(serviceNames0.contains("slobrok"));
        assertTrue(serviceNames0.contains("logd"));
        assertTrue(serviceNames0.contains("filedistributorservice"));

        Set<String> serviceNames1 = serviceNames(model.getConfig(SentinelConfig.class, "hosts/myhost1"));
        assertEquals(3, serviceNames1.size());
        assertTrue(serviceNames1.contains("slobrok"));
        assertTrue(serviceNames1.contains("logd"));
        assertTrue(serviceNames1.contains("filedistributorservice"));

        Set<String> serviceNames2 = serviceNames(model.getConfig(SentinelConfig.class, "hosts/myhost2"));
        assertEquals(3, serviceNames2.size());
        assertTrue(serviceNames2.contains("logserver"));
        assertTrue(serviceNames2.contains("logd"));
        assertTrue(serviceNames2.contains("filedistributorservice"));
    }

    private Set<String> serviceNames(SentinelConfig config) {
        return config.service().stream().map(SentinelConfig.Service::name).collect(Collectors.toSet());
    }

}
