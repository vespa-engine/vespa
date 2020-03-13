// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.text.XML;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.xml.BundleInstantiationSpecificationBuilder;
import org.w3c.dom.Element;

/**
 * @author gjoranv
 */
public class DomHandlerBuilder extends VespaDomBuilder.DomConfigProducerBuilder<Handler> {

    @Override
    protected Handler doBuild(DeployState deployState, AbstractConfigProducer parent, Element handlerElement) {
        Handler<? super Component<?, ?>> handler = createHandler(handlerElement);

        for (Element binding : XML.getChildren(handlerElement, "binding"))
            handler.addServerBindings(XML.getValue(binding));

        for (Element clientBinding : XML.getChildren(handlerElement, "clientBinding"))
            handler.addClientBindings(XML.getValue(clientBinding));

        DomComponentBuilder.addChildren(deployState, parent, handlerElement, handler);

        return handler;
    }

    protected Handler<? super Component<?, ?>> createHandler(Element handlerElement) {
        BundleInstantiationSpecification bundleSpec = BundleInstantiationSpecificationBuilder.build(handlerElement);
        return new Handler<>(new ComponentModel(bundleSpec));
    }
}
