// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.xml;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.log.LogLevel;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.http.AccessControl;
import com.yahoo.vespa.model.container.http.FilterChains;
import com.yahoo.vespa.model.container.http.Http;
import com.yahoo.vespa.model.container.http.Http.Binding;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.model.container.http.AccessControl.ACCESS_CONTROL_CHAIN_ID;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class HttpBuilder extends VespaDomBuilder.DomConfigProducerBuilder<Http> {

    @Override
    protected Http doBuild(AbstractConfigProducer ancestor, Element spec) {
        FilterChains filterChains;
        List<Binding> bindings = new ArrayList<>();
        AccessControl accessControl = null;

        Element filteringElem = XML.getChild(spec, "filtering");
        if (filteringElem != null) {
            filterChains = new FilterChainsBuilder().build(ancestor, filteringElem);
            bindings = readFilterBindings(filteringElem);

            Element accessControlElem = XML.getChild(filteringElem, "access-control");
            if (accessControlElem != null) {
                accessControl = buildAccessControl(ancestor, accessControlElem);
                bindings.addAll(accessControl.getBindings());
                filterChains.add(new Chain<>(FilterChains.emptyChainSpec(ACCESS_CONTROL_CHAIN_ID)));
            }
        } else {
            filterChains = new FilterChainsBuilder().newChainsInstance(ancestor);
        }

        Http http = new Http(bindings, accessControl);
        http.setFilterChains(filterChains);

        buildHttpServers(ancestor, http, spec);

        return http;
    }

    private AccessControl buildAccessControl(AbstractConfigProducer ancestor, Element accessControlElem) {
        String application = XmlHelper.getOptionalChildValue(accessControlElem, "application")
                .orElse(getDeployedApplicationId(ancestor).value());

        AccessControl.Builder builder = new AccessControl.Builder(accessControlElem.getAttribute("domain"), application);

        getContainerCluster(ancestor).ifPresent(cluster -> {
            builder.setHandlers(cluster.getHandlers());
            builder.setServlets(cluster.getAllServlets());
        });

        XmlHelper.getOptionalAttribute(accessControlElem, "read").ifPresent(
                readAttr -> builder.readEnabled(Boolean.valueOf(readAttr)));
        XmlHelper.getOptionalAttribute(accessControlElem, "write").ifPresent(
                writeAttr -> builder.writeEnabled(Boolean.valueOf(writeAttr)));

        Element excludeElem = XML.getChild(accessControlElem, "exclude");
        if (excludeElem != null) {
            XML.getChildren(excludeElem, "binding").stream()
                    .map(XML::getValue)
                    .forEach(builder::excludeBinding);
        }
        XmlHelper.getOptionalChildValue(accessControlElem, "vespa-domain").ifPresent(builder::vespaDomain);
        return builder.build();
    }

    /**
     * Returns the id of the deployed application, or the default value if not explicitly set (self-hosted).
     */
    private static ApplicationName getDeployedApplicationId(AbstractConfigProducer ancestor) {
        return getContainerCluster(ancestor)
                .map(cluster -> cluster.getRoot().getDeployState().getProperties().applicationId().application())
                .orElse(ApplicationId.defaultId().application());
    }

    private static Optional<ContainerCluster> getContainerCluster(AbstractConfigProducer configProducer) {
        AbstractConfigProducer currentProducer = configProducer;
        while (currentProducer.getClass() != ContainerCluster.class) {
            currentProducer = currentProducer.getParent();
            if (currentProducer == null)
                return Optional.empty();
        }
        return Optional.of((ContainerCluster) currentProducer);
    }

    private List<Binding> readFilterBindings(Element filteringSpec) {
        List<Binding> result = new ArrayList<>();

        for (Element child: XML.getChildren(filteringSpec)) {
            String tagName = child.getTagName();
            if ((tagName.equals("request-chain") || tagName.equals("response-chain"))) {
                ComponentSpecification chainId = XmlHelper.getIdRef(child);

                for (Element bindingSpec: XML.getChildren(child, "binding")) {
                    String binding = XML.getValue(bindingSpec);
                    result.add(new Binding(chainId, binding));
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
