// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.generic.builder;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.generic.service.ServiceCluster;
import org.w3c.dom.Element;
import java.util.Map;

/**
* @author Ulf Lilleengen
*/
public class DomServiceClusterBuilder extends VespaDomBuilder.DomConfigProducerBuilder<ServiceCluster> {

    private final String name;

    public DomServiceClusterBuilder(String name) {
        this.name = name;
    }

    @Override
    protected ServiceCluster doBuild(DeployState deployState, AbstractConfigProducer<?> ancestor, Element spec) {
        ServiceCluster cluster = new ServiceCluster(ancestor, name, spec.getAttribute("command"));
        int nodeIndex = 0;
        for (Element nodeSpec : XML.getChildren(spec, "node")) {
            com.yahoo.vespa.model.generic.service.Service service = new DomServiceBuilder(nodeIndex).build(deployState, cluster, nodeSpec);

            // TODO: Currently creates the config for each service. Should instead build module tree first
            // and store them in ServiceCluster. Then have some way of referencing them from each service.
            for (Element subServiceSpec : XML.getChildren(spec, "module")) {
                String subServiceName = subServiceSpec.getAttribute("name");
                Map<String, AbstractConfigProducer<?>> map = service.getChildren();
                // Add only non-conflicting modules. Does not merge unspecified configs that are specified in root though.
                if (!map.containsKey(subServiceName))
                    new DomModuleBuilder(subServiceName).build(deployState, service, subServiceSpec);
            }
            nodeIndex++;
        }
        return cluster;
    }

}
