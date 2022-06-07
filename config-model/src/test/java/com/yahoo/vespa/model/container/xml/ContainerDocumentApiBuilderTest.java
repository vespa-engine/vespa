// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import org.junit.Test;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class ContainerDocumentApiBuilderTest extends ContainerModelBuilderTestBase {

    private Map<String, Handler<?>> getHandlers(String clusterName) {
        ContainerCluster<?> cluster = (ContainerCluster<?>) root.getChildren().get(clusterName);
        Map<String, Handler<?>> handlerMap = new HashMap<>();
        Collection<Handler<?>> handlers = cluster.getHandlers();
        for (Handler<?> handler : handlers) {
            assertFalse(handlerMap.containsKey(handler.getComponentId().toString()));  //die on overwrites
            handlerMap.put(handler.getComponentId().toString(), handler);
        }
        return handlerMap;
    }

    @Test
    public void requireThatHandlersAreSetup() {
        Element elem = DomBuilderTest.parse(
                "<container id='cluster1' version='1.0'>",
                "  <document-api />",
                nodesXml,
                "</container>");
        createModel(root, elem);

        Map<String, Handler<?>> handlerMap = getHandlers("cluster1");

        assertNotNull(handlerMap.get("com.yahoo.container.handler.VipStatusHandler"));
        assertNotNull(handlerMap.get("com.yahoo.container.handler.observability.ApplicationStatusHandler"));
        assertNotNull(handlerMap.get("com.yahoo.container.jdisc.state.StateHandler"));
        assertNotNull(handlerMap.get("com.yahoo.document.restapi.resource.DocumentV1ApiHandler"));
    }

}
