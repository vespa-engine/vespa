// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.xml;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.text.XML;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.http.AccessControl;
import com.yahoo.vespa.model.container.http.Binding;
import com.yahoo.vespa.model.container.http.FilterChains;
import com.yahoo.vespa.model.container.http.Http;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

import static com.yahoo.vespa.model.container.http.AccessControl.ACCESS_CONTROL_CHAIN_ID;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class HttpBuilder extends VespaDomBuilder.DomConfigProducerBuilder<Http> {

    @Override
    protected Http doBuild(DeployState deployState, AbstractConfigProducer ancestor, Element spec) {
        FilterChains filterChains;
        List<Binding> bindings = new ArrayList<>();
        AccessControl accessControl = null;

        Element filteringElem = XML.getChild(spec, "filtering");
        if (filteringElem != null) {
            filterChains = new FilterChainsBuilder().build(deployState, ancestor, filteringElem);
            bindings = readFilterBindings(filteringElem, deployState.getDeployLogger());

            Element accessControlElem = XML.getChild(filteringElem, "access-control");
            if (accessControlElem != null) {
                accessControl = buildAccessControl(deployState, ancestor, accessControlElem);
                bindings.addAll(accessControl.getBindings());
                filterChains.add(new Chain<>(FilterChains.emptyChainSpec(ACCESS_CONTROL_CHAIN_ID)));
            }
        } else {
            filterChains = new FilterChainsBuilder().newChainsInstance(ancestor);
        }

        Http http = new Http(bindings, accessControl);
        http.setFilterChains(filterChains);

        buildHttpServers(deployState, ancestor, http, spec);

        return http;
    }

    private AccessControl buildAccessControl(DeployState deployState, AbstractConfigProducer ancestor, Element accessControlElem) {
        AthenzDomain domain = getAccessControlDomain(deployState, accessControlElem);
        AccessControl.Builder builder = new AccessControl.Builder(domain.value(), deployState.getDeployLogger());

        getContainerCluster(ancestor).ifPresent(builder::setHandlers);

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
        return builder.build();
    }

    // TODO Fail if domain is not provided through deploy properties
    private static AthenzDomain getAccessControlDomain(DeployState deployState, Element accessControlElem) {
        AthenzDomain tenantDomain = deployState.getProperties().athenzDomain().orElse(null);
        AthenzDomain explicitDomain = XmlHelper.getOptionalAttribute(accessControlElem, "domain")
                .map(AthenzDomain::from)
                .orElse(null);
        if (tenantDomain == null) {
            if (explicitDomain == null) {
                throw new IllegalStateException("No Athenz domain provided for 'access-control'");
            }
            deployState.getDeployLogger().log(Level.WARNING, "Athenz tenant is not provided by deploy call. This will soon be handled as failure.");
        }
        if (explicitDomain != null) {
            if (explicitDomain.equals(tenantDomain)) {
                throw new IllegalArgumentException(
                        String.format("Domain in access-control ('%s') does not match tenant domain ('%s')", tenantDomain, explicitDomain));
            }
            deployState.getDeployLogger().log(Level.WARNING, "Domain in 'access-control' is deprecated and will be removed soon");
        }
        return tenantDomain != null ? tenantDomain : explicitDomain;
    }

    private static Optional<ApplicationContainerCluster> getContainerCluster(AbstractConfigProducer configProducer) {
        AbstractConfigProducer currentProducer = configProducer;
        while (! ApplicationContainerCluster.class.isAssignableFrom(currentProducer.getClass())) {
            currentProducer = currentProducer.getParent();
            if (currentProducer == null)
                return Optional.empty();
        }
        return Optional.of((ApplicationContainerCluster) currentProducer);
    }

    private List<Binding> readFilterBindings(Element filteringSpec, DeployLogger logger) {
        List<Binding> result = new ArrayList<>();

        for (Element child: XML.getChildren(filteringSpec)) {
            String tagName = child.getTagName();
            if ((tagName.equals("request-chain") || tagName.equals("response-chain"))) {
                ComponentSpecification chainId = XmlHelper.getIdRef(child);

                for (Element bindingSpec: XML.getChildren(child, "binding")) {
                    String binding = XML.getValue(bindingSpec);
                    result.add(Binding.create(chainId, binding, logger));
                }
            }
        }
        return result;
    }

    private void buildHttpServers(DeployState deployState, AbstractConfigProducer ancestor, Http http, Element spec) {
        http.setHttpServer(new JettyHttpServerBuilder().build(deployState, ancestor, spec));
    }

    static int readPort(ModelElement spec, boolean isHosted, DeployLogger logger) {
        Integer port = spec.integerAttribute("port");
        if (port == null)
            return Defaults.getDefaults().vespaWebServicePort();

        if (port < 0)
            throw new IllegalArgumentException("Invalid port " + port);

        int legalPortInHostedVespa = Container.BASEPORT;
        if (isHosted && port != legalPortInHostedVespa && ! spec.booleanAttribute("required", false)) {
            throw new IllegalArgumentException("Illegal port " + port + " in http server '" +
                                               spec.stringAttribute("id") + "'" +
                                               ": Port must be set to " + legalPortInHostedVespa);
        }
        return port;
    }
}
