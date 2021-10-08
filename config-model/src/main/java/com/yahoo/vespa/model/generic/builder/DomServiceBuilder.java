// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.generic.builder;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.generic.service.Service;
import com.yahoo.vespa.model.generic.service.ServiceCluster;
import org.w3c.dom.Element;

/**
* @author Ulf Lilleengen
* @since 5.1
*/
public class DomServiceBuilder extends VespaDomBuilder.DomConfigProducerBuilder<Service> {
    private final int i;

    public DomServiceBuilder(int i) {
        this.i = i;
    }

    @Override
    protected com.yahoo.vespa.model.generic.service.Service doBuild(DeployState deployState, AbstractConfigProducer parent,
                                                                    Element serviceSpec) {
        ServiceCluster sc = (ServiceCluster) parent;
        com.yahoo.vespa.model.generic.service.Service service = new com.yahoo.vespa.model.generic.service.Service(sc, i + "");
        for (Element subServiceSpec : XML.getChildren(serviceSpec, "module")) {
            new DomModuleBuilder(subServiceSpec.getAttribute("name")).build(deployState, service, subServiceSpec);
        }
        return service;
    }
}
