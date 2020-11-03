// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.text.XML;
import com.yahoo.vespa.model.clients.ContainerDocumentApi;
import com.yahoo.vespa.model.container.ContainerThreadpool;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Einar M R Rosenvinge
 */
public class DocumentApiOptionsBuilder {

    private static final Logger log = Logger.getLogger(DocumentApiOptionsBuilder.class.getName());


    public static ContainerDocumentApi.Options build(Element spec) {
        return new ContainerDocumentApi.Options(getBindings(spec), threadpoolOptions(spec, "http-client-api"));
    }

    private static ContainerThreadpool.UserOptions threadpoolOptions(Element spec, String elementName) {
        Element element = XML.getChild(spec, elementName);
        if (element == null) return null;
        return ContainerThreadpool.UserOptions.fromXml(element).orElse(null);
    }

    private static List<String> getBindings(Element spec) {
        Collection<Element> bindingElems =  XML.getChildren(spec, "binding");
        if (bindingElems.isEmpty())
            return List.of();
        List<String> bindings = new ArrayList<>();
        for (Element e :bindingElems) {
            String binding = getBinding(e);
            bindings.add(binding);
        }
        return bindings;
    }

    private static String getBinding(Element e) {
        String binding = XML.getValue(e);
        if (! binding.endsWith("/")) {
            log.warning("Adding a trailing '/' to the document-api binding: " + binding + " -> " + binding + "/");
            binding = binding + "/";
        }
        return binding;
    }

}
