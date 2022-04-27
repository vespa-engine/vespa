// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder;
import com.yahoo.vespa.model.container.search.searchchain.Source;
import org.w3c.dom.Element;

import java.util.Map;

/**
 * Builds a source from xml.
 * @author Tony Vaagenes
 */
public class DomSourceBuilder extends DomGenericTargetBuilder<Source> {
    DomSourceBuilder(Map<String, ComponentsBuilder.ComponentType<?>> outerSearcherTypeByComponentName) {
        super(outerSearcherTypeByComponentName);
    }

    protected Source buildChain(DeployState deployState, AbstractConfigProducer<?> ancestor, Element producerSpec, ChainSpecification specWithoutInnerComponents) {
        Source.GroupOption groupOption =
                XmlHelper.isReference(producerSpec) ?
                        Source.GroupOption.participant :
                        Source.GroupOption.leader;

        return new Source(specWithoutInnerComponents, readFederationOptions(producerSpec), groupOption);
    }

}
