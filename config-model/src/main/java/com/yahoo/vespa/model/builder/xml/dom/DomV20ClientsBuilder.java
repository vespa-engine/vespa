// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.text.XML;
import com.yahoo.vespa.model.clients.Clients;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Builds the Clients plugin
 *
 * @author vegardh
 */
public class DomV20ClientsBuilder {

    // The parent docproc plugin to register data with.
    private final Clients clients;

    DomV20ClientsBuilder(Clients clients, String version) {
        if ( ! version.equals("2.0"))
            throw new IllegalArgumentException("Version '" + version + "' of 'clients' not supported.");
        this.clients = clients;
    }

    public void build(Element spec) {
        NodeList children = spec.getElementsByTagName("load-types");
        for (int i = 0; i < children.getLength(); i++) {
            createLoadTypes((Element) children.item(i), clients);
        }
    }

    private void createLoadTypes(Element element, Clients clients) {
        for (Element e : XML.getChildren(element, "type")) {
            String priority = e.getAttribute("default-priority");
            clients.getLoadTypes().addType(e.getAttribute("name"), priority.length() > 0 ? priority : null);
        }
    }

}
