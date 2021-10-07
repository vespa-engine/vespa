// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.generic;

import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.vespa.model.generic.builder.DomServiceClusterBuilder;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.List;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class GenericServicesBuilder extends ConfigModelBuilder<GenericServicesModel> {

    public GenericServicesBuilder() {
        super(GenericServicesModel.class);
    }

    @Override
    public List<ConfigModelId> handlesElements() {
        return Arrays.asList(ConfigModelId.fromName("service"));
    }

    @Override
    public void doBuild(GenericServicesModel model, Element spec, ConfigModelContext context) {
        String name = spec.getAttribute("name");
        model.addCluster(new DomServiceClusterBuilder(name).build(context.getDeployState(), context.getParentProducer(), spec));
    }
}
