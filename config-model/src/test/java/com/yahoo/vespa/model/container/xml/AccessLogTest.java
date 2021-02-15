// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.core.AccessLogConfig;
import com.yahoo.container.logging.ConnectionLogConfig;
import com.yahoo.container.logging.FileConnectionLog;
import com.yahoo.container.logging.JSONAccessLog;
import com.yahoo.container.logging.VespaAccessLog;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import org.junit.Test;
import org.w3c.dom.Element;

import static com.yahoo.text.StringUtilities.quote;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author gjoranv
 */
public class AccessLogTest extends ContainerModelBuilderTestBase {

    @Test
    public void default_access_log_is_added_by_default() {
        Element cluster1Elem = DomBuilderTest.parse(
                "<container id='cluster1' version='1.0'>",
                "  <nodes>",
                "    <node hostalias='mockhost' baseport='1234' />",
                "  </nodes>",
                "</container>" );

        createModel(root, cluster1Elem);

        assertNotNull(getJsonAccessLog("cluster1"));
        assertNull(getVespaAccessLog("cluster1"));
    }

    @Test
    public void default_search_access_log_can_be_disabled() {
        final String jdiscClusterId = "jdisc-cluster";

        Element clusterElem = DomBuilderTest.parse(
                "<container id=" + quote(jdiscClusterId) + " version='1.0'>" +
                        "  <search />" +
                        "  <accesslog type='disabled' />" +
                        "</container>" );

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
    public void access_log_can_be_configured() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <accesslog type='vespa' ",
                "             fileNamePattern='pattern' rotationInterval='interval' />",
                "  <accesslog type='json' ",
                "             fileNamePattern='pattern' rotationInterval='interval' />",
                nodesXml,
                "</container>" );

        createModel(root, clusterElem);
        assertNotNull(getJsonAccessLog("default"));
        assertNotNull(getVespaAccessLog("default"));

        { // vespa
            Component<?, ?> accessLogComponent = getContainerComponent("default", VespaAccessLog.class.getName());
            assertNotNull(accessLogComponent);
            assertEquals(VespaAccessLog.class.getName(), accessLogComponent.getClassId().getName(), VespaAccessLog.class.getName());
            AccessLogConfig config = root.getConfig(AccessLogConfig.class, "default/component/com.yahoo.container.logging.VespaAccessLog");
            AccessLogConfig.FileHandler fileHandlerConfig = config.fileHandler();
            assertEquals("pattern", fileHandlerConfig.pattern());
            assertEquals("interval", fileHandlerConfig.rotation());
            assertEquals(10000, fileHandlerConfig.queueSize());
        }

        { // json
            Component<?, ?> accessLogComponent = getContainerComponent("default", JSONAccessLog.class.getName());
            assertNotNull(accessLogComponent);
            assertEquals(JSONAccessLog.class.getName(), accessLogComponent.getClassId().getName(), JSONAccessLog.class.getName());
            AccessLogConfig config = root.getConfig(AccessLogConfig.class, "default/component/com.yahoo.container.logging.JSONAccessLog");
            AccessLogConfig.FileHandler fileHandlerConfig = config.fileHandler();
            assertEquals("pattern", fileHandlerConfig.pattern());
            assertEquals("interval", fileHandlerConfig.rotation());
            assertEquals(10000, fileHandlerConfig.queueSize());
        }
    }

    @Test
    public void connection_log_configured_when_access_log_not_disabled() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <accesslog type='vespa' ",
                "             fileNamePattern='pattern' rotationInterval='interval' />",
                "  <accesslog type='json' ",
                "             fileNamePattern='pattern' rotationInterval='interval' />",
                nodesXml,
                "</container>" );
        createModel(root, clusterElem);
        Component<?, ?> connectionLogComponent = getContainerComponent("default", FileConnectionLog.class.getName());
        assertNotNull(connectionLogComponent);
        ConnectionLogConfig config = root.getConfig(ConnectionLogConfig.class, "default/component/com.yahoo.container.logging.FileConnectionLog");
        assertEquals("default", config.cluster());
        assertEquals(10000, config.queueSize());
    }

    @Test
    public void connection_log_disabled_when_access_log_disabled() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <accesslog type='disabled' />",
                nodesXml,
                "</container>" );
        createModel(root, clusterElem);
        Component<?, ?> fileConnectionLogComponent = getContainerComponent("default", FileConnectionLog.class.getName());
        assertNull(fileConnectionLogComponent);
    }
}
