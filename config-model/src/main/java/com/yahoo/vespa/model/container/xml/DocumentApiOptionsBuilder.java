// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.text.XML;
import com.yahoo.vespa.model.clients.ContainerDocumentApi;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Einar M R Rosenvinge
 * @since 5.1.11
 */
public class DocumentApiOptionsBuilder {

    private static final Logger log = Logger.getLogger(DocumentApiOptionsBuilder.class.getName());
    private static final String[] DEFAULT_BINDINGS = {"http://*/"};

    public static ContainerDocumentApi.Options build(Element spec) {
        return new ContainerDocumentApi.Options(
                getBindings(spec),
                getAbortOnDocumentError(spec),
                getRoute(spec),
                getMaxPendingDocs(spec),
                getRetryEnabled(spec),
                getTimeout(spec),
                getTracelevel(spec),
                getMbusPort(spec));
    }

    private static List<String> getBindings(Element spec) {
        Collection<Element> bindingElems =  XML.getChildren(spec, "binding");
        if (bindingElems.isEmpty())
            return Arrays.asList(DEFAULT_BINDINGS);

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

    private static String getCleanValue(Element spec, String name) {
        Element elem = XML.getChild(spec, name);
        if (elem == null) {
            return null;
        }
        String value = elem.getFirstChild().getNodeValue();
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private static Integer getMbusPort(Element spec) {
        String value = getCleanValue(spec, "mbusport");
        return value == null ? null : Integer.parseInt(value);
    }

    private static Integer getTracelevel(Element spec) {
        String value = getCleanValue(spec, "tracelevel");
        return value == null ? null : Integer.parseInt(value);
    }

    private static Double getTimeout(Element spec) {
        String value = getCleanValue(spec, "timeout");
        return value == null ? null : Double.parseDouble(value);
    }

    private static Boolean getRetryEnabled(Element spec) {
        String value = getCleanValue(spec, "retryenabled");
        return value == null ? null : Boolean.parseBoolean(value);
    }

    private static Integer getMaxPendingDocs(Element spec) {
        String value = getCleanValue(spec, "maxpendingdocs");
        return value == null ? null : Integer.parseInt(value);
    }

    private static String getRoute(Element spec) {
        return getCleanValue(spec, "route");
    }

    private static Boolean getAbortOnDocumentError(Element spec) {
        String value = getCleanValue(spec, "abortondocumenterror");
        return value == null ? null : Boolean.parseBoolean(value);
    }

}
