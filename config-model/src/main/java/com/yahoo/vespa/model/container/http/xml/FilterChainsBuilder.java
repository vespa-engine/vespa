// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.chains.ChainsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder.ComponentType;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainBuilderBase;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainsBuilder;
import com.yahoo.vespa.model.container.http.Filter;
import com.yahoo.vespa.model.container.http.FilterChains;
import com.yahoo.vespa.model.container.http.HttpFilterChain;
import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Tony Vaagenes
 */
public class FilterChainsBuilder extends DomChainsBuilder<Filter, HttpFilterChain, FilterChains>  {
    private static final Collection<ComponentType<Filter>> allowedComponentTypes =
            List.of(ComponentType.filter);

    private static final Map<String, Class<? extends DomChainBuilderBase<? extends Filter, ? extends HttpFilterChain>>> chainType2BuilderClass =
            Map.of(
                    HttpBuilder.REQUEST_CHAIN_TAG_NAME, FilterChainBuilder.class,
                    HttpBuilder.RESPONSE_CHAIN_TAG_NAME, FilterChainBuilder.class);

    public FilterChainsBuilder() {
        super(allowedComponentTypes);
    }

    @Override
    protected FilterChains newChainsInstance(TreeConfigProducer<AnyConfigProducer> parent) {
        return new FilterChains(parent);
    }

    @Override
    protected ChainsBuilder<Filter, HttpFilterChain> readChains(
            DeployState deployState,
            TreeConfigProducer<AnyConfigProducer> ancestor,
            List<Element> allChainsElems, Map<String, ComponentsBuilder.ComponentType<?>> outerComponentTypeByComponentName) {

        validateInheritance(allChainsElems);
        return new ChainsBuilder<>(deployState, ancestor, allChainsElems, outerComponentTypeByComponentName, chainType2BuilderClass);
    }

    /** Fails if a request chain inherits a response chain, or vice versa */
    private static void validateInheritance(List<Element> allChainsElems) {
        Map<ComponentId, String> chainTypeById = new HashMap<>();
        for (Element chainsElem : allChainsElems)
            for (String chainType : chainType2BuilderClass.keySet())
                for (Element chainElem : XML.getChildren(chainsElem, chainType))
                    chainTypeById.put(XmlHelper.getId(chainElem), chainType);

        for (Element chainsElem : allChainsElems) {
            for (String chainType : chainType2BuilderClass.keySet()) {
                for (Element chainElem : XML.getChildren(chainsElem, chainType)) {
                    for (String inherited : XmlHelper.spaceSeparatedSymbolsFromAttribute(chainElem, "inherits")) {
                        ComponentSpecification spec = new ComponentSpecification(inherited);
                        chainTypeById.forEach((id, type) -> {
                            if (spec.matches(id) && ! type.equals(chainType))
                                throw new IllegalArgumentException(
                                        "%s '%s' cannot inherit %s '%s'".formatted(chainType, XmlHelper.getIdString(chainElem), type, id));
                        });
                    }
                }
            }
        }
    }
}
