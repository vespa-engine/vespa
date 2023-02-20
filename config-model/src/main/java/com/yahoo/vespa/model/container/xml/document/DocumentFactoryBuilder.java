// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml.document;

import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import org.w3c.dom.Element;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles the document bindings (concrete document types). Register the concrete document factories as components.
 *
 * @author vegardh
 * @since 5.1.10
 */
public class DocumentFactoryBuilder {
    private static final String CONCRETE_DOC_FACTORY_CLASS = "ConcreteDocumentFactory";

    public static void buildDocumentFactories(ContainerCluster cluster, Element spec) {
        Map<String, String> types = new LinkedHashMap<>();
        for (Element e : XML.getChildren(spec, "document")) {
            String type = e.getAttribute("type");
            String clazz = e.getAttribute("class");
            // Empty pkg is forbidden in the documentgen Mojo.
            if (clazz.indexOf('.')<0) throw new IllegalArgumentException("Malformed class for <document> binding, must be a full class with package: "+clazz);
            String pkg = clazz.substring(0, clazz.lastIndexOf('.'));
            String concDocFactory=pkg+"."+CONCRETE_DOC_FACTORY_CLASS;
            String bundle = e.getAttribute("bundle");
            Component<AnyConfigProducer, ComponentModel> component = new Component<>(
                    new ComponentModel(BundleInstantiationSpecification.fromStrings(concDocFactory, concDocFactory, bundle)));
            if (!cluster.getComponentsMap().containsKey(component.getComponentId())) cluster.addComponent(component);
            types.put(type, concDocFactory);
        }
        cluster.concreteDocumentTypes().putAll(types);
    }
}
