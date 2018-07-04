// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.ChainedComponentModelBuilder;
import com.yahoo.vespa.model.container.search.searchchain.Searcher;
import org.w3c.dom.Element;

/**
 * Builds a Searcher from XML.
 * @author Tony Vaagenes
 */
public class DomSearcherBuilder extends VespaDomBuilder.DomConfigProducerBuilder<Searcher<?>> {

    protected Searcher<ChainedComponentModel> doBuild(AbstractConfigProducer ancestor, Element searcherElement) {
        ChainedComponentModelBuilder modelBuilder = new ChainedComponentModelBuilder(searcherElement);
        return new Searcher<>(modelBuilder.build());
    }

}
