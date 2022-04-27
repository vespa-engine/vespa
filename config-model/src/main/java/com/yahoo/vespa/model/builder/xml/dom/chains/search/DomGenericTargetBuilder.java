// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.DomChainBuilderBase;
import com.yahoo.vespa.model.container.search.searchchain.GenericTarget;
import com.yahoo.vespa.model.container.search.searchchain.Searcher;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Map;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * Base functionality for all target chain builders (provider, source)
 */
abstract public class DomGenericTargetBuilder<T extends GenericTarget> extends DomChainBuilderBase<Searcher<?>, T> {

    DomGenericTargetBuilder(Map<String, ComponentsBuilder.ComponentType<?>> outerSearcherTypeByComponentName) {
        super(Arrays.asList(ComponentsBuilder.ComponentType.searcher, ComponentsBuilder.ComponentType.federation),
                outerSearcherTypeByComponentName);
    }

    protected static FederationOptions readFederationOptions(Element sourceElement) {
        Element optionsElement = XML.getChild(sourceElement, FederationOptionsBuilder.federationOptionsElement);
        if (optionsElement == null) {
            return new FederationOptions();
        } else {
            return new FederationOptionsBuilder(optionsElement).build();
        }
    }
}
