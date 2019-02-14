// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.ConfigModelUtils;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.Container;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Tony Vaagenes
 */
public class ContainerServiceBuilder extends VespaDomBuilder.DomConfigProducerBuilder<Container> {

    private final String id;
    private final int index;

    public ContainerServiceBuilder(String id, int index) {
        this.id = id;
        this.index = index;
    }

    @Override
    protected Container doBuild(DeployState deployState, AbstractConfigProducer parent, Element nodeElem) {
        return new Container(parent, id, index, deployState.isHosted());
    }

}
