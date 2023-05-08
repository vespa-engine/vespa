// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.core.AccessLogConfig;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.logging.ConnectionLogConfig;
import com.yahoo.container.logging.FileConnectionLog;
import com.yahoo.container.logging.JSONAccessLog;
import com.yahoo.container.logging.VespaAccessLog;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.logging.Level;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.text.StringUtilities.quote;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author gjoranv
 */
public class AccessLogTest extends ContainerModelBuilderTestBase {

    @Test
    void default_access_log_is_added_by_default() {
        Element cluster1Elem = DomBuilderTest.parse(
                "<container id='cluster1' version='1.0'>",
                "  <nodes>",
                "    <node hostalias='mockhost' baseport='1234' />",
                "  </nodes>",
                "</container>");

        createModel(root, cluster1Elem);

        assertNotNull(getJsonAccessLog("cluster1"));
        assertNull(getVespaAccessLog("cluster1"));
    }

    @Test
    void default_search_access_log_can_be_disabled() {
        final String jdiscClusterId = "jdisc-cluster";

        Element clusterElem = DomBuilderTest.parse(
                "<container id=" + quote(jdiscClusterId) + " version='1.0'>" +
                        "  <search />" +
                        "  <accesslog type='disabled' />" +
                        "</container>");

        createModel(root, clusterElem);
        assertNull(getVespaAccessLog(jdiscClusterId));
        assertNull(getJsonAccessLog(jdiscClusterId));
    }

    private Component<?, ?> getVespaAccessLog(String clusterName) {
        ApplicationContainerCluster cluster = (ApplicationContainerCluster) root.getChildren().get(clusterName);
        return cluster.getComponentsMap().get(ComponentId.fromString((VespaAccessLog.class.getName())));
    }
    private Component<?, ?> getJsonAccessLog(String clusterName) {
        ApplicationContainerCluster cluster = (ApplicationContainerCluster) root.getChildren().get(clusterName);
        return cluster.getComponentsMap().get(ComponentId.fromString((JSONAccessLog.class.getName())));
    }

    @Test
    void access_log_can_be_configured() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <accesslog type='vespa' ",
                "             fileNamePattern='pattern' rotationInterval='interval' />",
                "  <accesslog type='json' ",
                "             fileNamePattern='pattern' rotationInterval='interval' queueSize='17' bufferSize='65536'/>",
                nodesXml,
                "</container>");

        createModel(root, clusterElem);
        assertNotNull(getJsonAccessLog("default"));
        assertNotNull(getVespaAccessLog("default"));
        assertNotNull(getAccessLog("default"));

        { // vespa
            Component<?, ?> accessLogComponent = getComponent("default", VespaAccessLog.class.getName());
            assertNotNull(accessLogComponent);
            assertEquals(accessLogComponent.getClassId().getName(), VespaAccessLog.class.getName(), VespaAccessLog.class.getName());
            AccessLogConfig config = root.getConfig(AccessLogConfig.class, "default/component/com.yahoo.container.logging.VespaAccessLog");
            AccessLogConfig.FileHandler fileHandlerConfig = config.fileHandler();
            assertEquals("pattern", fileHandlerConfig.pattern());
            assertEquals("interval", fileHandlerConfig.rotation());
            assertEquals(256, fileHandlerConfig.queueSize());
            assertEquals(256 * 1024, fileHandlerConfig.bufferSize());
        }

        { // json
            Component<?, ?> accessLogComponent = getComponent("default", JSONAccessLog.class.getName());
            assertNotNull(accessLogComponent);
            assertEquals(accessLogComponent.getClassId().getName(), JSONAccessLog.class.getName(), JSONAccessLog.class.getName());
            AccessLogConfig config = root.getConfig(AccessLogConfig.class, "default/component/com.yahoo.container.logging.JSONAccessLog");
            AccessLogConfig.FileHandler fileHandlerConfig = config.fileHandler();
            assertEquals("pattern", fileHandlerConfig.pattern());
            assertEquals("interval", fileHandlerConfig.rotation());
            assertEquals(17, fileHandlerConfig.queueSize());
            assertEquals(65536, fileHandlerConfig.bufferSize());
        }
    }

    @Test
    void connection_log_configured_when_access_log_not_disabled() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <accesslog type='vespa' ",
                "             fileNamePattern='pattern' rotationInterval='interval' />",
                "  <accesslog type='json' ",
                "             fileNamePattern='pattern' rotationInterval='interval' />",
                nodesXml,
                "</container>");
        createModel(root, clusterElem);
        Component<?, ?> connectionLogComponent = getComponent("default", FileConnectionLog.class.getName());
        assertNotNull(connectionLogComponent);
        ConnectionLogConfig config = root.getConfig(ConnectionLogConfig.class, "default/component/com.yahoo.container.logging.FileConnectionLog");
        assertEquals("default", config.cluster());
        assertEquals(-1, config.queueSize());
        assertEquals(256 * 1024, config.bufferSize());
    }

    @Test
    void connection_log_disabled_when_access_log_disabled() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <accesslog type='disabled' />",
                nodesXml,
                "</container>");
        createModel(root, clusterElem);
        Component<?, ?> fileConnectionLogComponent = getComponent("default", FileConnectionLog.class.getName());
        assertNull(fileConnectionLogComponent);
    }

    @Test
    void hosted_applications_get_a_log_warning_when_overriding_accesslog() {
        String containerService = joinLines("<container id='foo' version='1.0'>",
                "  <accesslog type='json' fileNamePattern='logs/vespa/qrs/access.%Y%m%d%H%M%S' symlinkName='json_access' />",
                "  <nodes count=\"2\">",
                "  </nodes>",
                "</container>");

        String deploymentXml = joinLines("<deployment version='1.0'>",
                "  <prod>",
                "    <region>us-east-1</region>",
                "  </prod>",
                "</deployment>");

        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder()
                .withServices(containerService)
                .withDeploymentSpec(deploymentXml)
                .build();

        TestLogger logger = new TestLogger();
        DeployState deployState = new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .zone(new Zone(Environment.prod, RegionName.from("us-east-1")))
                .properties(new TestProperties().setHostedVespa(true))
                .deployLogger(logger)
                .build();
        createModel(root, deployState, null, DomBuilderTest.parse(containerService));
        assertFalse(logger.msgs.isEmpty());
        assertEquals(Level.WARNING, logger.msgs.get(0).getFirst());
        assertEquals("Applications are not allowed to override the 'accesslog' element",
                logger.msgs.get(0).getSecond());
    }

    private Component<?, ?> getAccessLog(String clusterName) {
        ApplicationContainerCluster cluster = (ApplicationContainerCluster) root.getChildren().get(clusterName);
        return cluster.getComponentsMap().get(ComponentId.fromString((AccessLog.class.getName())));
    }

}
