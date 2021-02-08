// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.ApplicationContainer;
import org.w3c.dom.Element;

/**
 * @author Tony Vaagenes
 */
public class ContainerServiceBuilder extends VespaDomBuilder.DomConfigProducerBuilder<ApplicationContainer> {

    private final String id;
    private final int index;

    public ContainerServiceBuilder(String id, int index) {
        this.id = id;
        this.index = index;
    }

    @Override
    protected ApplicationContainer doBuild(DeployState deployState, AbstractConfigProducer<?> parent, Element nodeElem) {
        return new ApplicationContainer(parent, id, index, deployState.isHosted());
    }

}
