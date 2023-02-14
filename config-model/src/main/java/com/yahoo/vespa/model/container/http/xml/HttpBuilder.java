// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.xml;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.text.XML;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.component.UserBindingPattern;
import com.yahoo.vespa.model.container.http.AccessControl;
import com.yahoo.vespa.model.container.http.FilterBinding;
import com.yahoo.vespa.model.container.http.FilterChains;
import com.yahoo.vespa.model.container.http.Http;
import org.w3c.dom.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class HttpBuilder extends VespaDomBuilder.DomConfigProducerBuilderBase<Http> {

    static final String REQUEST_CHAIN_TAG_NAME = "request-chain";
    static final String RESPONSE_CHAIN_TAG_NAME = "response-chain";
    static final List<String> VALID_FILTER_CHAIN_TAG_NAMES = List.of(REQUEST_CHAIN_TAG_NAME, RESPONSE_CHAIN_TAG_NAME);

    @Override
    protected Http doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element spec) {
        FilterChains filterChains;
        List<FilterBinding> bindings = new ArrayList<>();
        AccessControl accessControl = null;
        Optional<Boolean> strictFiltering = Optional.empty();

        Element filteringElem = XML.getChild(spec, "filtering");
        if (filteringElem != null) {
            filterChains = new FilterChainsBuilder().build(deployState, ancestor, filteringElem);
            bindings = readFilterBindings(filteringElem);
            strictFiltering = XmlHelper.getOptionalAttribute(filteringElem, "strict-mode")
                    .map(Boolean::valueOf);

            Element accessControlElem = XML.getChild(filteringElem, "access-control");
            if (accessControlElem != null) {
                accessControl = buildAccessControl(deployState, ancestor, accessControlElem);
            }
        } else {
            filterChains = new FilterChainsBuilder().newChainsInstance(ancestor);
        }

        Http http = new Http(filterChains);
        strictFiltering.ifPresent(http::setStrictFiltering);
        http.getBindings().addAll(bindings);
        ApplicationContainerCluster cluster = getContainerCluster(ancestor).orElse(null);
        http.setHttpServer(new JettyHttpServerBuilder(cluster).build(deployState, ancestor, spec));
        if (accessControl != null) {
            accessControl.configureHttpFilterChains(http);
        }
        return http;
    }

    private AccessControl buildAccessControl(DeployState deployState, TreeConfigProducer<?> ancestor, Element accessControlElem) {
        AthenzDomain domain = getAccessControlDomain(deployState, accessControlElem);
        AccessControl.Builder builder = new AccessControl.Builder(domain.value());

        getContainerCluster(ancestor).ifPresent(builder::setHandlers);

        // TODO: Remove in Vespa 9
        XmlHelper.getOptionalAttribute(accessControlElem, "read").ifPresent(
                readAttr -> deployState.getDeployLogger()
                        .logApplicationPackage(Level.WARNING,
                                "The 'read' attribute of the 'access-control' element has no effect and is deprecated. " +
                                        "Please remove the attribute from services.xml, support will be removed in Vespa 9"));
        // TODO: Remove in Vespa 9
        XmlHelper.getOptionalAttribute(accessControlElem, "write").ifPresent(
                writeAttr -> deployState.getDeployLogger()
                        .logApplicationPackage(Level.WARNING,
                                "The 'write' attribute of the 'access-control' element has no effect and is deprecated. " +
                                        "Please remove the attribute from services.xml, support will be removed in Vespa 9"));

        AccessControl.ClientAuthentication clientAuth =
                XmlHelper.getOptionalAttribute(accessControlElem, "tls-handshake-client-auth")
                        .filter("want"::equals)
                        .map(value -> AccessControl.ClientAuthentication.want)
                        .orElse(AccessControl.ClientAuthentication.need);
        if (! deployState.getProperties().allowDisableMtls() && clientAuth == AccessControl.ClientAuthentication.want) {
            throw new IllegalArgumentException("Overriding 'tls-handshake-client-auth' for application is not allowed.");
        }
        builder.clientAuthentication(clientAuth);

        Element excludeElem = XML.getChild(accessControlElem, "exclude");
        if (excludeElem != null) {
            XML.getChildren(excludeElem, "binding").stream()
                    .map(xml -> UserBindingPattern.fromPattern(XML.getValue(xml)))
                    .forEach(builder::excludeBinding);
        }
        return builder.build();
    }

    // TODO(tokle,bjorncs) Vespa > 8: Fail if domain is not provided through deploy properties
    private static AthenzDomain getAccessControlDomain(DeployState deployState, Element accessControlElem) {
        AthenzDomain tenantDomain = deployState.getProperties().athenzDomain().orElse(null);
        AthenzDomain explicitDomain = XmlHelper.getOptionalAttribute(accessControlElem, "domain")
                .map(AthenzDomain::from)
                .orElse(null);
        if (tenantDomain == null) {
            if (explicitDomain == null) {
                throw new IllegalArgumentException("No Athenz domain provided for 'access-control'");
            }
            deployState.getDeployLogger().logApplicationPackage(Level.WARNING, "Athenz tenant is not provided by deploy call. This will soon be handled as failure.");
        }
        if (explicitDomain != null) {
            if (tenantDomain != null && !explicitDomain.equals(tenantDomain)) {
                throw new IllegalArgumentException(
                        String.format("Domain in access-control ('%s') does not match tenant domain ('%s')", explicitDomain.value(), tenantDomain.value()));
            }
            deployState.getDeployLogger()
                    .logApplicationPackage(Level.WARNING,
                            "Domain in 'access-control' is deprecated and is no longer necessary. " +
                                    "Please remove the 'domain' attribute from the 'access-control' element in services.xml.");
        }
        return tenantDomain != null ? tenantDomain : explicitDomain;
    }

    private static Optional<ApplicationContainerCluster> getContainerCluster(TreeConfigProducer<?> configProducer) {
        AnyConfigProducer currentProducer = configProducer;
        while (! ApplicationContainerCluster.class.isAssignableFrom(currentProducer.getClass())) {
            currentProducer = currentProducer.getParent();
            if (currentProducer == null)
                return Optional.empty();
        }
        return Optional.of((ApplicationContainerCluster) currentProducer);
    }

    private List<FilterBinding> readFilterBindings(Element filteringSpec) {
        List<FilterBinding> result = new ArrayList<>();

        for (Element child: XML.getChildren(filteringSpec)) {
            String tagName = child.getTagName();
            if (VALID_FILTER_CHAIN_TAG_NAMES.contains(tagName)) {
                ComponentSpecification chainId = XmlHelper.getIdRef(child);

                for (Element bindingSpec: XML.getChildren(child, "binding")) {
                    String binding = XML.getValue(bindingSpec);
                    result.add(FilterBinding.create(toFilterBindingType(tagName), chainId, UserBindingPattern.fromPattern(binding)));
                }
            }
        }
        return result;
    }

    private static FilterBinding.Type toFilterBindingType(String chainTag) {
        switch (chainTag) {
            case REQUEST_CHAIN_TAG_NAME: return FilterBinding.Type.REQUEST;
            case RESPONSE_CHAIN_TAG_NAME: return FilterBinding.Type.RESPONSE;
            default: throw new IllegalArgumentException("Unknown filter chain tag: " + chainTag);
        }
    }

    static int readPort(ModelElement spec, boolean isHosted) {
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
