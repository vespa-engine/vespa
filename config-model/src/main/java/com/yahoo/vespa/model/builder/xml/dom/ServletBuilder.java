// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.container.component.Servlet;
import com.yahoo.vespa.model.container.component.ServletProvider;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.xml.BundleInstantiationSpecificationBuilder;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.Map;

/**
 * @author stiankri
 * @since 5.32
 */
public class ServletBuilder extends VespaDomBuilder.DomConfigProducerBuilder<Servlet> {
    @Override
    protected ServletProvider doBuild(DeployState deployState, AbstractConfigProducer ancestor, Element servletElement) {
        SimpleComponent servlet = createServletComponent(servletElement);
        ServletProvider servletProvider = createServletProvider(servletElement, servlet);

        return servletProvider;
    }

    private SimpleComponent createServletComponent(Element servletElement) {
        ComponentModel componentModel = new ComponentModel(BundleInstantiationSpecificationBuilder.build(servletElement));
        return new SimpleComponent(componentModel);
    }

    private ServletProvider createServletProvider(Element servletElement, SimpleComponent servlet) {
        Map<String, String> servletConfig = getServletConfig(servletElement);
        return new ServletProvider(servlet, getPath(servletElement), servletConfig);
    }

    private String getPath(Element servletElement) {
        Element pathElement = XML.getChild(servletElement, "path");
        return XML.getValue(pathElement);
    }

    private Map<String, String> getServletConfig(Element servletElement) {
        Map<String, String> servletConfig = new HashMap<>();

        Element servletConfigElement = XML.getChild(servletElement, "servlet-config");
        XML.getChildren(servletConfigElement).forEach( parameter ->
                servletConfig.put(parameter.getTagName(), XML.getValue(parameter))
        );

        return servletConfig;
    }
}

