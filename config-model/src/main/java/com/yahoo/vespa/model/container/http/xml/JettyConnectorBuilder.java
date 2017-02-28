// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.xml;

import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import org.w3c.dom.Element;

import java.util.logging.Logger;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.21.0
 */
public class JettyConnectorBuilder extends VespaDomBuilder.DomConfigProducerBuilder<ConnectorFactory>  {
    private static final Logger log = Logger.getLogger(JettyConnectorBuilder.class.getName());

    @Override
    protected ConnectorFactory doBuild(AbstractConfigProducer ancestor, Element serverSpec) {
        String name = XmlHelper.getIdString(serverSpec);
        int port = HttpBuilder.readPort(serverSpec, ancestor.getRoot().getDeployState());

        Element legacyServerConfig = XML.getChild(serverSpec, "config");
        if (legacyServerConfig != null) {
            String configName = legacyServerConfig.getAttribute("name");
            if (!configName.equals("container.jdisc.config.http-server")) {
                log.warning("The config 'container.jdisc.config.http-server' is deprecated and will be removed in a later version of Vespa."
                                    + " Please use 'jdisc.http.connector' instead, see https://git.corp.yahoo.com/pages/vespa/documentation/documentation/jdisc/http-server-and-filters.html#configuring-jetty-server");
                legacyServerConfig = null;
            }
        }
        return new ConnectorFactory(name, port, legacyServerConfig);
    }
}
