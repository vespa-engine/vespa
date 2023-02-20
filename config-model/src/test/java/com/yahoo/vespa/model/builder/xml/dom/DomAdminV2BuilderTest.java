// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.cloud.config.log.LogdConfig;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.Configserver;
import com.yahoo.vespa.model.admin.Slobrok;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hmusum
 */
public class DomAdminV2BuilderTest extends DomBuilderTest {

    private static MockRoot root;

    @BeforeEach
    public void prepareTest() {
        root = new MockRoot("root");
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

    private Element servicesAdminNoAdminServerOrConfigServer() {
        return XML.getDocument("<admin version=\"2.0\">" +
                        "</admin>").getDocumentElement();
    }

    @Test
    void multitenant() {
        List<ConfigServerSpec> configServerSpecs = Arrays.asList(
                new TestProperties.Spec("test1", 19070, 2181),
                new TestProperties.Spec("test2", 19070, 2181),
                new TestProperties.Spec("test3", 19070, 2181));
        Admin admin = buildAdmin(servicesMultitenantAdminOnly(), true, configServerSpecs);
        assertEquals(3, admin.getConfigservers().size());
        assertEquals(1, admin.getSlobroks().size());
        assertTrue(admin.hostSystem().getAllHosts().stream().map(HostResource::getHost).anyMatch(host -> host.getHostname().equals("test1")));
        for (Configserver configserver : admin.getConfigservers()) {
            for (Slobrok slobrok : admin.getSlobroks()) {
                assertNotEquals(configserver.getHostName(), slobrok.getHostName());
            }
        }
    }

    /**
     * Tests that configservers/configserver works
     */
    @Test
    void adminWithConfigserversElement() {
        Admin admin = buildAdmin(servicesConfigservers());
        assertEquals(1, admin.getConfigservers().size());
    }

    @Test
    void basicYamasNoXml() {
        Admin admin = buildAdmin(servicesNoYamas());
        Monitoring y = admin.getMonitoring();
        assertEquals("vespa", y.getClustername());
        assertEquals(1, y.getInterval().intValue());
    }

    @Test
    void testAdminServerOnly() {
        Admin admin = buildAdmin(servicesAdminServerOnly());
        assertEquals(1, admin.getSlobroks().size());
    }

    @Test
    void basicYamasXml() {
        Admin admin = buildAdmin(servicesYamas());
        Monitoring y = admin.getMonitoring();
        assertEquals("foo", y.getClustername());
        assertEquals(1, y.getInterval().intValue());
    }

    @Test
    void yamasWithIntervalOverride() {
        Admin admin = buildAdmin(servicesYamasIntervalOverride());
        Monitoring y = admin.getMonitoring();
        assertEquals("foo", y.getClustername());
        assertEquals(5, y.getInterval().intValue());
    }

    /**
     * Test that illegal yamas interval throws exception
     */
    @Test
    void yamasElementInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            Element servicesYamasIllegalInterval = XML.getDocument(
                    "<admin version=\"2.0\">" +
                            "  <adminserver hostalias=\"mockhost\"/>" +
                            "  <monitoring interval=\"5\"/>" +
                            "</admin>").getDocumentElement();
            Admin admin = buildAdmin(servicesYamasIllegalInterval);
        });
    }

    @Test
    void configOverridesCanBeUsedInAdmin() {
        Admin admin = buildAdmin(servicesOverride());
        assertEquals(1, admin.getUserConfigs().size());
        LogdConfig.Builder logdBuilder = new LogdConfig.Builder();
        admin.addUserConfig(logdBuilder);
        LogdConfig config = new LogdConfig(logdBuilder);
        assertEquals("foobar", config.logserver().host());
    }

    @Test
    void noAdminServerOrConfigServer() {
        Admin admin = buildAdmin(servicesAdminNoAdminServerOrConfigServer());
        assertEquals(1, admin.getConfigservers().size());
    }

    private Admin buildAdmin(Element xml) {
        return buildAdmin(xml, false, new ArrayList<>());
    }

    private Admin buildAdmin(Element xml, boolean multitenant, List<ConfigServerSpec> configServerSpecs) {
        DeployState deployState = DeployState.createTestState();
        final DomAdminV2Builder domAdminBuilder =
                new DomAdminV2Builder(ConfigModelContext.ApplicationType.DEFAULT, multitenant, configServerSpecs);
        Admin admin = domAdminBuilder.build(deployState, root, xml);
        admin.addPerHostServices(root.hostSystem().getHosts(), deployState);
        return admin;
    }

}
