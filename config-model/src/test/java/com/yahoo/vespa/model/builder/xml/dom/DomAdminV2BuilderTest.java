// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.cloud.config.log.LogdConfig;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.admin.*;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * @author hmusum
 */
public class DomAdminV2BuilderTest extends DomBuilderTest {

    private static MockRoot root;

    @Before
    public void prepareTest() {
        root = new MockRoot("root");
    }

    // Supported for backwards compatibility
    private Element servicesConfigserver() {
        return XML.getDocument(
                        "<admin version=\"2.0\">" +
                        "  <configserver hostalias=\"mockhost\"/>" +
                        "  <adminserver hostalias=\"mockhost\"/>" +
                        "</admin>").getDocumentElement();

    }

    private Element servicesOverride() {
        return XML.getDocument(
                        "<admin version=\"2.0\">" +
                        "  <adminserver hostalias=\"mockhost\"/>" +
                        "  <config name=\"cloud.config.log.logd\">" +
                        "    <logserver><host>foobar</host></logserver>" +
                        "  </config>" +
                        "</admin>").getDocumentElement();

    }

    private Element servicesConfigservers() {
        return XML.getDocument(
                        "<admin version=\"2.0\">" +
                        "  <configservers>" +
                        "    <configserver hostalias=\"mockhost\"/>" +
                        "  </configservers>" +
                        "  <adminserver hostalias=\"mockhost\"/>" +
                        "</admin>").getDocumentElement();
    }

    private Element servicesYamas() {
        return XML.getDocument(
                        "<admin version=\"2.0\">" +
                        "  <configservers>" +
                        "    <configserver hostalias=\"mockhost\"/>" +
                        "  </configservers>" +
                        "    <adminserver hostalias=\"mockhost\"/>" +
                        "  <monitoring systemname=\"foo\"/>" +
                        "</admin>").getDocumentElement();
    }

    private Element servicesNoYamas() {
        return XML.getDocument(
                        "<admin version=\"2.0\">" +
                        "  <configservers>" +
                        "    <configserver hostalias=\"mockhost\"/>" +
                        "  </configservers>" +
                        "   <adminserver hostalias=\"mockhost\"/>" +
                        "</admin>").getDocumentElement();
    }

    private Element servicesAdminServerOnly() {
        return XML.getDocument(
                        "<admin version=\"2.0\">" +
                        "  <adminserver hostalias=\"mockhost\"/>" +
                        "</admin>").getDocumentElement();
    }

    private Element servicesYamasIntervalOverride() {
        return XML.getDocument(
                        "<admin version=\"2.0\">" +
                        "  <configservers>" +
                        "    <configserver hostalias=\"mockhost\"/>" +
                        "  </configservers>" +
                        "    <adminserver hostalias=\"mockhost\"/>" +
                        "  <monitoring systemname=\"foo\" interval=\"300\"/>" +
                        "</admin>").getDocumentElement();
    }

    private Element servicesMultitenantAdminOnly() {
        return XML.getDocument(
                        "<admin version=\"2.0\">" +
                        "  <adminserver hostalias=\"mockhost\" />" +
                        "</admin>").getDocumentElement();
    }

    @Test
    public void multitenant() {
        List<ConfigServerSpec> configServerSpecs = Arrays.asList(
                new TestProperties.Spec("test1", 19070, 2181),
                new TestProperties.Spec("test2", 19070, 2181),
                new TestProperties.Spec("test3", 19070, 2181));
        Admin admin = buildAdmin(servicesMultitenantAdminOnly(), true, configServerSpecs);
        assertThat(admin.getConfigservers().size(), is(3));
        assertThat(admin.getSlobroks().size(), is(1));
        assertThat(admin.getClusterControllerHosts().size(), is(1));
        assertNotNull(admin.hostSystem().getHostByHostname("test1"));
        for (Configserver configserver : admin.getConfigservers()) {
            assertThat(configserver.getHostName(), is(not(admin.getClusterControllerHosts().get(0).getHost().getHostname())));
            for (Slobrok slobrok : admin.getSlobroks()) {
                    assertThat(slobrok.getHostName(), is(not(configserver.getHostName())));
            }
        }
    }

    /**
     * Tests that configserver works (deprecated, but allowed in admin 2.0)
     */
    @Test
    public void adminWithConfigserverElement() {
        Admin admin = buildAdmin(servicesConfigserver());
        assertThat(admin.getConfigservers().size(), is(1));
    }

    /**
     * Tests that configservers/configserver works
     */
    @Test
    public void adminWithConfigserversElement() {
        Admin admin = buildAdmin(servicesConfigservers());
        assertThat(admin.getConfigservers().size(), is(1));
    }

    @Test
    public void basicYamasNoXml() {
        Admin admin = buildAdmin(servicesNoYamas());
        Monitoring y = admin.getMonitoring();
        assertThat(y.getClustername(), is("vespa"));
        assertThat(y.getInterval(), is(1));
    }

    @Test
    public void testAdminServerOnly() {
        Admin admin = buildAdmin(servicesAdminServerOnly());
        assertEquals(1, admin.getSlobroks().size());
    }

    @Test
    public void basicYamasXml() {
        Admin admin = buildAdmin(servicesYamas());
        Monitoring y = admin.getMonitoring();
        assertThat(y.getClustername(), is("foo"));
        assertThat(y.getInterval(), is(1));
    }

    @Test
    public void yamasWithIntervalOverride() {
        Admin admin = buildAdmin(servicesYamasIntervalOverride());
        Monitoring y = admin.getMonitoring();
        assertThat(y.getClustername(), is("foo"));
        assertThat(y.getInterval(), is(5));
    }

    /**
     * Test that illegal yamas interval throws exception
     */
    @Test(expected = IllegalArgumentException.class)
    public void yamasElementInvalid() {
        Element servicesYamasIllegalInterval = XML.getDocument(
                        "<admin version=\"2.0\">" +
                        "  <adminserver hostalias=\"mockhost\"/>" +
                        "  <monitoring interval=\"5\"/>" +
                        "</admin>").getDocumentElement();
        Admin admin = buildAdmin(servicesYamasIllegalInterval);
    }

    @Test
    public void configOverridesCanBeUsedInAdmin() {
        Admin admin = buildAdmin(servicesOverride());
        assertThat(admin.getUserConfigs().size(), is(1));
        LogdConfig.Builder logdBuilder = new LogdConfig.Builder();
        admin.addUserConfig(logdBuilder);
        LogdConfig config = new LogdConfig(logdBuilder);
        assertThat(config.logserver().host(), is("foobar"));
    }

    private Admin buildAdmin(Element xml) {
        return buildAdmin(xml, false, new ArrayList<>());
    }

    private Admin buildAdmin(Element xml, boolean multitenant, List<ConfigServerSpec> configServerSpecs) {
        DeployState deployState = DeployState.createTestState();
        final DomAdminV2Builder domAdminBuilder =
                new DomAdminV2Builder(ConfigModelContext.ApplicationType.DEFAULT,
                                      deployState.getFileRegistry(), multitenant,
                                      configServerSpecs);
        Admin admin = domAdminBuilder.build(deployState, root, xml);
        admin.addPerHostServices(root.hostSystem().getHosts(), deployState);
        return admin;
    }

}
