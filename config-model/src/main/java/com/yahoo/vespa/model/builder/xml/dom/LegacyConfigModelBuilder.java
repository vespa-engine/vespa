// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.ConfigModelInstanceFactory;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import org.w3c.dom.Element;

/**
 * A model builder that can be used to deal with toplevel config overrides and create another
 * producer in between. This should not be used by new model plugins.
 *
 * @author Ulf Lilleengen
 */
public abstract class LegacyConfigModelBuilder<MODEL extends ConfigModel> extends ConfigModelBuilder<MODEL> {

    public LegacyConfigModelBuilder(Class<MODEL> configModelClass) {
        super(configModelClass);
    }

    @Override
    public MODEL build(ConfigModelInstanceFactory<MODEL> factory, Element spec, ConfigModelContext context) {
        VespaDomBuilder.DomSimpleConfigProducerBuilder builder = new VespaDomBuilder.DomSimpleConfigProducerBuilder(context.getProducerId());
        TreeConfigProducer<AnyConfigProducer> producer = builder.build(context.getDeployState(), context.getParentProducer(), spec);
        return super.build(factory, spec, context.withParent(producer));
    }

}
