// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.xml;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import com.yahoo.vespa.model.container.http.JettyHttpServer;
import org.w3c.dom.Element;

/**
 * @author Einar M R Rosenvinge
 */
public class JettyHttpServerBuilder extends VespaDomBuilder.DomConfigProducerBuilder<JettyHttpServer> {

    private final ContainerCluster<?> cluster;

    public JettyHttpServerBuilder(ContainerCluster<?> cluster) {
        this.cluster = cluster;
    }

    @Override
    protected JettyHttpServer doBuild(DeployState deployState, AbstractConfigProducer<?> ancestor, Element http) {
        JettyHttpServer jettyHttpServer = new JettyHttpServer("jdisc-jetty", cluster, deployState);
        for (Element serverSpec: XML.getChildren(http, "server")) {
            ConnectorFactory connectorFactory = new JettyConnectorBuilder().build(deployState, ancestor, serverSpec);
            jettyHttpServer.addConnector(connectorFactory);
        }

        return jettyHttpServer;
    }

}
