// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.xml;

import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import org.w3c.dom.Element;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Einar M R Rosenvinge
 */
public class JettyConnectorBuilder extends VespaDomBuilder.DomConfigProducerBuilder<ConnectorFactory>  {

    @Override
    protected ConnectorFactory doBuild(AbstractConfigProducer ancestor, Element serverSpec) {
        String name = XmlHelper.getIdString(serverSpec);
        int port = HttpBuilder.readPort(serverSpec, ancestor.getRoot().getDeployState());

        Element sslKeystoreConfigurator = XML.getChild(serverSpec, "ssl-keystore-configurator");
        Element sslTruststoreConfigurator = XML.getChild(serverSpec, "ssl-truststore-configurator");
        return new ConnectorFactory(name, port, sslKeystoreConfigurator, sslTruststoreConfigurator);
    }

}
