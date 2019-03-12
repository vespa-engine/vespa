// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.core.AccessLogConfig;
import com.yahoo.container.logging.JSONAccessLog;
import com.yahoo.container.logging.VespaAccessLog;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import org.junit.Test;
import org.w3c.dom.Element;

import static com.yahoo.text.StringUtilities.quote;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 * @since 5.5
 */
public class AccessLogTest extends ContainerModelBuilderTestBase {

    @Test
    public void default_access_log_is_only_added_when_search_is_present() throws Exception {
        Element cluster1Elem = DomBuilderTest.parse(
                "<jdisc id='cluster1' version='1.0'>",
                "<search />",
                nodesXml,
                "</jdisc>");
        Element cluster2Elem = DomBuilderTest.parse(
                "<jdisc id='cluster2' version='1.0'>",
                "  <nodes>",
                "    <node hostalias='mockhost' baseport='1234' />",
                "  </nodes>",
                "</jdisc>" );

        createModel(root, cluster1Elem, cluster2Elem);

        assertNotNull(getJsonAccessLog("cluster1"));
        assertNull(   getJsonAccessLog("cluster2"));
        assertNull(getVespaAccessLog("cluster1"));
        assertNull(getVespaAccessLog("cluster2"));
    }

    @Test
    public void default_search_access_log_can_be_disabled() throws Exception {
        final String jdiscClusterId = "jdisc-cluster";

        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id=" + quote(jdiscClusterId) + " version='1.0'>" +
                        "  <search />" +
                        "  <accesslog type='disabled' />" +
                        "</jdisc>" );

        createModel(root, clusterElem);
        assertNull(getVespaAccessLog(jdiscClusterId));
        assertNull(getJsonAccessLog(jdiscClusterId));
    }

    private Component<?, ?> getVespaAccessLog(String clusterName) {
        ContainerCluster cluster = (ContainerCluster) root.getChildren().get(clusterName);
        return cluster.getComponentsMap().get(ComponentId.fromString((VespaAccessLog.class.getName())));
    }
    private Component<?, ?> getJsonAccessLog(String clusterName) {
        ContainerCluster cluster = (ContainerCluster) root.getChildren().get(clusterName);
        return cluster.getComponentsMap().get(ComponentId.fromString((JSONAccessLog.class.getName())));
    }

    @Test
    public void access_log_can_be_configured() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <accesslog type='vespa' ",
                "             fileNamePattern='pattern' rotationInterval='interval' />",
                "  <accesslog type='json' ",
                "             fileNamePattern='pattern' rotationInterval='interval' />",
                nodesXml,
                "</jdisc>" );

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
        }

        { // json
            Component<?, ?> accessLogComponent = getContainerComponent("default", JSONAccessLog.class.getName());
            assertNotNull(accessLogComponent);
            assertEquals(JSONAccessLog.class.getName(), accessLogComponent.getClassId().getName(), JSONAccessLog.class.getName());
            AccessLogConfig config = root.getConfig(AccessLogConfig.class, "default/component/com.yahoo.container.logging.JSONAccessLog");
            AccessLogConfig.FileHandler fileHandlerConfig = config.fileHandler();
            assertEquals("pattern", fileHandlerConfig.pattern());
            assertEquals("interval", fileHandlerConfig.rotation());
        }
    }

}
