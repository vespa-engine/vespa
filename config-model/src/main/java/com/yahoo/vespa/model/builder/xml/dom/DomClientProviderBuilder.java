// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.UserBindingPattern;
import org.w3c.dom.Element;

/**
 * @author gjoranv
 * @since 5.1.6
 */
public class DomClientProviderBuilder extends DomHandlerBuilder {

    public DomClientProviderBuilder(ApplicationContainerCluster cluster) {
        super(cluster);
    }

    @Override
    protected Handler doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> parent, Element clientElement) {
        Handler client = createHandler(clientElement);

        for (Element binding : XML.getChildren(clientElement, "binding"))
            client.addClientBindings(UserBindingPattern.fromPattern(XML.getValue(binding)));

        for (Element serverBinding : XML.getChildren(clientElement, "serverBinding"))
            client.addServerBindings(UserBindingPattern.fromPattern(XML.getValue(serverBinding)));

        DomComponentBuilder.addChildren(deployState, parent, clientElement, client);

        return client;
    }
}
