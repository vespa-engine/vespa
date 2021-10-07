// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.vespa.model.clients.Clients;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.List;

/**
 * Builds the Clients plugin
 *
 * @author hmusum
 */
public class DomClientsBuilder extends LegacyConfigModelBuilder<Clients> {

    public DomClientsBuilder() {
        super(Clients.class);
    }

    @Override
    public List<ConfigModelId> handlesElements() {
        return Arrays.asList(ConfigModelId.fromNameAndVersion("clients", "2.0"));
    }

    @Override
    public void doBuild(Clients clients, Element clientsE, ConfigModelContext modelContext) {
        String version = clientsE.getAttribute("version");
        if (version.startsWith("2.")) {
            DomV20ClientsBuilder parser = new DomV20ClientsBuilder(clients, version);
            parser.build(clientsE);
        } else {
            throw new IllegalArgumentException("Version '" + version + "' of 'clients' not supported.");
        }
    }

}
