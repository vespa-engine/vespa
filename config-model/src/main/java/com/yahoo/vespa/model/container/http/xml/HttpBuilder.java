// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.xml;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.component.UserBindingPattern;
import com.yahoo.vespa.model.container.http.FilterBinding;
import com.yahoo.vespa.model.container.http.FilterChains;
import com.yahoo.vespa.model.container.http.Http;
import org.w3c.dom.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class HttpBuilder extends VespaDomBuilder.DomConfigProducerBuilderBase<Http> {

    static final String REQUEST_CHAIN_TAG_NAME = "request-chain";
    static final String RESPONSE_CHAIN_TAG_NAME = "response-chain";
    static final List<String> VALID_FILTER_CHAIN_TAG_NAMES = List.of(REQUEST_CHAIN_TAG_NAME, RESPONSE_CHAIN_TAG_NAME);
    private final Set<Integer> portBindingOverrides;

    public HttpBuilder(Set<Integer> portBindingOverrides) {
        super();
        this.portBindingOverrides = portBindingOverrides;
    }

    @Override
    protected Http doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element spec) {
        FilterChains filterChains;
        List<FilterBinding> bindings = new ArrayList<>();
        Optional<Boolean> strictFiltering = Optional.empty();

        Element filteringElem = XML.getChild(spec, "filtering");
        if (filteringElem != null) {
            filterChains = new FilterChainsBuilder().build(deployState, ancestor, filteringElem);
            bindings = readFilterBindings(filteringElem, this.portBindingOverrides);
            strictFiltering = XmlHelper.getOptionalAttribute(filteringElem, "strict-mode")
                    .map(Boolean::valueOf);
        } else {
            filterChains = new FilterChainsBuilder().newChainsInstance(ancestor);
        }

        Http http = new Http(filterChains);
        strictFiltering.ifPresent(http::setStrictFiltering);
        http.getBindings().addAll(bindings);
        var cluster = getContainerCluster(ancestor).orElse(null);
        http.setHttpServer(new JettyHttpServerBuilder(cluster).build(deployState, ancestor, spec));
        return http;
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

    private List<FilterBinding> readFilterBindings(Element filteringSpec, Set<Integer> portBindingOverride) {
        List<FilterBinding> result = new ArrayList<>();

        for (Element child: XML.getChildren(filteringSpec)) {
            String tagName = child.getTagName();
            if (VALID_FILTER_CHAIN_TAG_NAMES.contains(tagName)) {
                ComponentSpecification chainId = XmlHelper.getIdRef(child);

                for (Element bindingSpec: XML.getChildren(child, "binding")) {
                    String binding = XML.getValue(bindingSpec);
                    if (portBindingOverride.isEmpty()) {
                        result.add(FilterBinding.create(toFilterBindingType(tagName), chainId, UserBindingPattern.fromPattern(binding)));
                    } else {
                        UserBindingPattern userBindingPattern = UserBindingPattern.fromPattern(binding);
                        portBindingOverride.stream()
                                .map(userBindingPattern::withOverriddenPort)
                                .forEach(pattern -> result.add(FilterBinding.create(toFilterBindingType(tagName), chainId, pattern)));
                    }
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
