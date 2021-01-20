// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.generic.builder;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.generic.service.Module;
import org.w3c.dom.Element;

/**
 * Produces sub services for generic services.
 */
public class DomModuleBuilder extends VespaDomBuilder.DomConfigProducerBuilder<Module> {

    private final String name;

    public DomModuleBuilder(String name) {
        this.name = name;
    }

    private void addChildren(DeployState deployState, Module s, Element subServiceSpec) {
        for (Element nodeSpec : XML.getChildren(subServiceSpec, "module")) {
            new DomModuleBuilder(nodeSpec.getAttribute("name")).build(deployState, s, nodeSpec);
        }
    }

    @Override
    protected Module doBuild(DeployState deployState, AbstractConfigProducer<?> ancestor, Element subServiceSpec) {
        Module s = new Module(ancestor, name);
        addChildren(deployState, s, subServiceSpec);
        return s;
    }
}
