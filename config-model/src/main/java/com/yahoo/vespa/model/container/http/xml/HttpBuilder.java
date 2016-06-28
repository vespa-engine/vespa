// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.xml;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.log.LogLevel;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.http.FilterChains;
import com.yahoo.vespa.model.container.http.Http;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tonytv
 * @author gjoranv
 */
public class HttpBuilder extends VespaDomBuilder.DomConfigProducerBuilder<Http> {

    @Override
    protected Http doBuild(AbstractConfigProducer ancestor, Element spec) {
        FilterChains filterChains;
        List<Http.Binding> bindings = new ArrayList<>();

        Element filteringElem = XML.getChild(spec, "filtering");
        if (filteringElem != null) {
            filterChains = new FilterChainsBuilder().build(ancestor, filteringElem);
            bindings = readFilterBindings(filteringElem);
        } else {
            filterChains = new FilterChainsBuilder().newChainsInstance(ancestor);
        }

        Http http = new Http(bindings);
        http.setFilterChains(filterChains);

        buildHttpServers(ancestor, http, spec);

        return http;
    }

    private List<Http.Binding> readFilterBindings(Element filteringSpec) {
        List<Http.Binding> result = new ArrayList<>();

        for (Element child: XML.getChildren(filteringSpec)) {
            String tagName = child.getTagName();
            if ((tagName.equals("request-chain") || tagName.equals("response-chain"))) {
                ComponentSpecification chainId = XmlHelper.getIdRef(child);

                for (Element bindingSpec: XML.getChildren(child, "binding")) {
                    String binding = XML.getValue(bindingSpec);
                    result.add(new Http.Binding(chainId, binding));
                }
            }
        }
        return result;
    }

    private void buildHttpServers(AbstractConfigProducer ancestor, Http http, Element spec) {
        http.setHttpServer(new JettyHttpServerBuilder().build(ancestor, spec));
    }

    static int readPort(Element spec, DeployState deployState) {
        String portString = spec.getAttribute("port");

        int port = Integer.parseInt(portString);
        if (port < 0)
            throw new IllegalArgumentException(String.format("Invalid port %d.", port));

        int legalPortInHostedVespa = Container.BASEPORT;
        if (deployState.isHosted() && port != legalPortInHostedVespa) {
            deployState.getDeployLogger().log(LogLevel.WARNING,
                    String.format("Trying to set port to %d for http server with id %s. You cannot set port to anything else than %s",
                            port, spec.getAttribute("id"), legalPortInHostedVespa));
        }

        return port;
    }
}
