// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.BinaryUnit;
import com.yahoo.vespa.model.clients.ContainerDocumentApi;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

/**
 * @author Einar M R Rosenvinge
 */
public class DocumentApiOptionsBuilder {

    private static final Logger log = Logger.getLogger(DocumentApiOptionsBuilder.class.getName());

    public static ContainerDocumentApi.HandlerOptions build(Element spec) {
        return new ContainerDocumentApi.HandlerOptions(getBindings(spec), XML.getChild(spec, "http-client-api"));
    }

    /**
     * Parses the optional {@code <max-document-size>} child of {@code <document-api>} and returns
     * its value in MiB. The value uses the same syntax as the content cluster's
     * {@code max-document-size} (e.g. {@code 100Mb}).
     */
    public static OptionalInt parseMaxDocumentSizeMib(Element spec, DeployLogger deployLogger) {
        if (spec == null) return OptionalInt.empty();
        Element maxSize = XML.getChild(spec, "max-document-size");
        if (maxSize == null) return OptionalInt.empty();
        String configuredValue = XML.getValue(maxSize);
        if (configuredValue == null || configuredValue.isEmpty()) return OptionalInt.empty();
        // The configured value has units, but the config expects it in MiB, extract the value and convert
        int maxDocumentSize = (int) (BinaryUnit.valueOf(configuredValue) / 1024 / 1024);
        if (maxDocumentSize < 1 || maxDocumentSize > 2048)
            throw new IllegalArgumentException("Invalid max-document-size value '" + configuredValue + "': Value must be between 1 MiB and 2048 MiB");
        if (maxDocumentSize > 128)
            deployLogger.log(WARNING, "max-document-size value is set to '" + configuredValue +
                    "', setting this above 128 MiB is strongly discouraged, as it may cause major performance issues. " +
                    "See https://docs.vespa.ai/en/reference/services/container.html#document-api");
        return OptionalInt.of(maxDocumentSize);
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
