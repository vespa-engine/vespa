// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.search.searchchain.model.federation.FederationSearcherModel;
import com.yahoo.text.XML;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.DomComponentBuilder;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.GenericChainedComponentModelBuilder;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.search.searchchain.FederationSearcher;
import com.yahoo.vespa.model.container.search.searchchain.Searcher;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds a federation searcher config producer from an element.
 * @author tonytv
 */
public class DomFederationSearcherBuilder extends VespaDomBuilder.DomConfigProducerBuilder<Searcher<?>> {
    static class FederationSearcherModelBuilder extends GenericChainedComponentModelBuilder {
        private final List<FederationSearcherModel.TargetSpec> sources;
        private final boolean inheritDefaultSources;

        FederationSearcherModelBuilder(Element searcherSpec) {
            super(searcherSpec);
            sources = readSources(searcherSpec);
            inheritDefaultSources = readSourceSet(searcherSpec);
        }

        private boolean readSourceSet(Element searcherSpec) {
            return XML.getChild(searcherSpec, "source-set") != null;
        }


        private List<FederationSearcherModel.TargetSpec> readSources(Element searcherSpec) {
            List<FederationSearcherModel.TargetSpec> sources = new ArrayList<>();
            for (Element source : XML.getChildren(searcherSpec, "source")) {
                sources.add(readSource(source));
            }
            return sources;
        }

        private FederationSearcherModel.TargetSpec readSource(Element source) {
            ComponentSpecification componentSpecification = XmlHelper.getIdRef(source);

            FederationOptions federationOptions =
                    readFederationOptions(XML.getChild(source,  FederationOptionsBuilder.federationOptionsElement));

            return new FederationSearcherModel.TargetSpec(componentSpecification, federationOptions);
        }

        private FederationOptions readFederationOptions(Element federationOptionsElement) {
            if (federationOptionsElement == null) {
                return new FederationOptions();
            } else {
                return new FederationOptionsBuilder(federationOptionsElement).build();
            }
        }

        protected FederationSearcherModel build() {
            return new FederationSearcherModel(componentId, dependencies, sources, inheritDefaultSources);
        }
    }

    protected FederationSearcher doBuild(AbstractConfigProducer ancestor, Element searcherElement) {
        FederationSearcherModel model = new FederationSearcherModelBuilder(searcherElement).build();
        Optional<Component> targetSelector = buildTargetSelector(ancestor, searcherElement, model.getComponentId());

        return new FederationSearcher(model, targetSelector);
    }

    private Optional<Component> buildTargetSelector(AbstractConfigProducer ancestor, Element searcherElement, ComponentId namespace) {
        Element targetSelectorElement = XML.getChild(searcherElement, "target-selector");
        if (targetSelectorElement == null)
            return Optional.empty();

        return Optional.of(new DomComponentBuilder(namespace).build(ancestor, targetSelectorElement));
    }
}
