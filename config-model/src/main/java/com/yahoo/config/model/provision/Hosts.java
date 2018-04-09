// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.net.HostName;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.logging.Logger;

/**
 * A collection of hosts
 *
 * @author bratseth
 */
public class Hosts {

    public static final Logger log = Logger.getLogger(Hosts.class.getPackage().toString());

    private final ImmutableMap<String, Host> hosts;

    public Hosts(Collection<Host> hosts) {
        validateAliases(hosts);
        
        ImmutableMap.Builder<String, Host> hostsBuilder = new ImmutableMap.Builder<>();
        for (Host host : hosts)
            hostsBuilder.put(host.hostname(), host);
        this.hosts = hostsBuilder.build();

        // Don't limit zk connections on non-hosted systems
        System.setProperty("zookeeper.vespa.clients", "");
    }

    /** Throw IllegalArgumentException if host aliases breaks invariants */
    private void validateAliases(Collection<Host> hosts) {
        Set<String> aliases = new HashSet<>();
        for (Host host : hosts) {
            if (host.aliases().size() > 0) {
                if (host.aliases().size() < 1)
                    throw new IllegalArgumentException("Host '" + host.hostname() + "' must have at least one <alias> tag.");
                for (String alias : host.aliases()) {
                    if (aliases.contains(alias))
                        throw new IllegalArgumentException("Alias '" + alias + "' is used by multiple hosts.");
                    aliases.add(alias);
                }
            }
        }
    }
    
    /**
     * Builds host system from a hosts.xml file
     *
     * @param hostsFile a reader for host from application package
     * @return the HostSystem for this application package
     */
    public static Hosts readFrom(Reader hostsFile) {
        List<Host> hosts = new ArrayList<>();
        Document doc;
        try {
            doc = XmlHelper.getDocumentBuilder().parse(new InputSource(hostsFile));
        } catch (SAXException | IOException e) {
            throw new IllegalArgumentException(e);
        }
        for (Element hostE : XML.getChildren(doc.getDocumentElement(), "host")) {
            String name = hostE.getAttribute("name");
            if (name.equals("")) {
                throw new RuntimeException("Missing 'name' attribute for host.");
            }
            if ("localhost".equals(name)) {
                name = HostName.getLocalhost();
            }
            List<String> hostAliases = VespaDomBuilder.getHostAliases(hostE.getChildNodes());
            if (hostAliases.isEmpty()) {
                throw new IllegalArgumentException("No host aliases defined for host '" + name + "'");
            }
            hosts.add(new Host(name, hostAliases));
        }
        return new Hosts(hosts);
    }

    /** Returns an immutable collection of the hosts of this */
    public Collection<Host> asCollection() { return hosts.values(); }

    @Override
    public String toString() {
        return "Hosts: " + hosts.keySet();
    }

}
