// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.model;

import com.yahoo.cloud.config.RoutingConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Christian Andersen
 */
@SuppressWarnings("removal") // For RoutingProducer
public class RoutingProducerTest {
    @Test
    public void testNodesFromRoutingAppOnly() throws Exception {
        Map<TenantName, Set<ApplicationInfo>> testModel = createTestModel(new DeployState.Builder());
        RoutingProducer producer = new RoutingProducer(testModel);
        RoutingConfig.Builder builder = new RoutingConfig.Builder();
        producer.getConfig(builder);
        RoutingConfig config = new RoutingConfig(builder);
        assertThat(config.hosts().size(), is(2));
        assertThat(config.hosts(0), is("hosted-vespa.routing.yahoo.com"));
        assertThat(config.hosts(1), is("hosted-vespa.routing2.yahoo.com"));
    }

    private Map<TenantName, Set<ApplicationInfo>> createTestModel(DeployState.Builder deployStateBuilder) throws IOException, SAXException {
        Map<TenantName, Set<ApplicationInfo>> apps = new LinkedHashMap<>();
        TenantName foo = TenantName.from("foo");
        TenantName bar = TenantName.from("bar");
        TenantName routing = TenantName.from(TenantRepository.HOSTED_VESPA_TENANT.value());
        apps.put(foo, createTestApplications(foo, deployStateBuilder));
        apps.put(bar, createTestApplications(bar, deployStateBuilder));
        apps.put(routing, createTestApplications(routing, deployStateBuilder));
        return apps;
    }

    private Set<ApplicationInfo> createTestApplications(TenantName tenant, DeployState.Builder deploystateBuilder) throws IOException, SAXException {
        Set<ApplicationInfo> applicationInfos = new LinkedHashSet<>();
        ApplicationId fooApp = new ApplicationId.Builder().tenant(tenant).applicationName("foo").build();
        ApplicationId barApp = new ApplicationId.Builder().tenant(tenant).applicationName("bar").build();
        ApplicationId routingApp = new ApplicationId.Builder().tenant(tenant).applicationName(RoutingProducer.ROUTING_APPLICATION.value()).build();
        applicationInfos.add(createApplication(fooApp, deploystateBuilder));
        applicationInfos.add(createApplication(barApp, deploystateBuilder));
        applicationInfos.add(createApplication(routingApp, deploystateBuilder));
        return applicationInfos;
    }

    private ApplicationInfo createApplication(ApplicationId appId, DeployState.Builder deploystateBuilder) throws IOException, SAXException {
        return new ApplicationInfo(
                appId,
                3l,
                createVespaModel(
                        createApplicationPackage(
                                appId.tenant() + "." + appId.application() + ".yahoo.com",
                                appId.tenant().value() + "." + appId.application().value() + "2.yahoo.com"),
                        deploystateBuilder));
    }

    private ApplicationPackage createApplicationPackage(String host1, String host2) {
        String hosts = "<hosts><host name='" + host1 + "'><alias>node1</alias></host><host name='" + host2 + "'><alias>node2</alias></host></hosts>";
        String services = "<services><admin version='2.0'><adminserver hostalias='node1' /><logserver hostalias='node1' /><slobroks><slobrok hostalias='node1' /><slobrok hostalias='node2' /></slobroks></admin>"
                + "<jdisc id='mydisc' version='1.0'>" +
                "  <aliases>" +
                "      <endpoint-alias>foo2.bar2.com</endpoint-alias>" +
                "      <service-alias>service1</service-alias>" +
                "      <endpoint-alias>foo1.bar1.com</endpoint-alias>" +
                "  </aliases>" +
                "  <nodes>" +
                "    <node hostalias='node1' />" +
                "  </nodes>" +
                "  <search/>" +
                "</jdisc>" +
                "</services>";
        String deploymentInfo ="<?xml version='1.0' encoding='UTF-8'?>" +
                "<deployment version='1.0'>" +
                "  <test />" +
                "  <prod global-service-id='mydisc'>" +
                "    <region active='true'>us-east</region>" +
                "  </prod>" +
                "</deployment>";

        return new MockApplicationPackage.Builder()
                .withHosts(hosts)
                .withServices(services)
                .withDeploymentSpec(deploymentInfo)
                .build();
    }

    private Model createVespaModel(ApplicationPackage applicationPackage, DeployState.Builder deployStateBuilder) throws IOException, SAXException {
        return new VespaModel(new NullConfigModelRegistry(), deployStateBuilder.applicationPackage(applicationPackage).build());
    }
}
