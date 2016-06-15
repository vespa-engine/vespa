// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.log.LogLevel;
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
 * TODO: What is this?
 *
 * @author musum
 */
public class Hosts {

    public static final Logger log = Logger.getLogger(Hosts.class.getPackage().toString());

    private final HashMap<String, Host> hosts = new LinkedHashMap<>();
    private final Map<String, String> alias2hostname = new LinkedHashMap<>();
    private final Map<String, Host> alias2host = new LinkedHashMap<>();

    /**
     * Builds host system from a hosts.xml file
     *
     * @param hostsFile a reader for host from application package
     * @return the HostSystem for this application package
     */
    public static Hosts getHosts(Reader hostsFile) {
        Hosts hosts = new Hosts();
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
            final List<String> hostAliases = VespaDomBuilder.getHostAliases(hostE.getChildNodes());
            if (hostAliases.isEmpty()) {
                throw new IllegalArgumentException("No host aliases defined for host '" + name + "'");
            }
            Host host = new Host(name, hostAliases);
            hosts.addHost(host, hostAliases);
        }
        log.log(LogLevel.DEBUG, "Created hosts:" + hosts);
        return hosts;
    }

    public Collection<Host> getHosts() {
        return hosts.values();
    }

    /**
     * Adds one host to this host system.
     *
     * @param host    The host to add
     * @param aliases The aliases for this host.
     */
    public void addHost(Host host, List<String> aliases) {
        hosts.put(host.getHostname(), host);
        if ((aliases != null) && (aliases.size() > 0)) {
            addHostAliases(aliases, host);
        }
    }

    /**
     * Add all aliases for one host
     *
     * @param hostAliases A list of host aliases
     * @param host        The Host instance to add the alias for
     */
    private void addHostAliases(List<String> hostAliases, Host host) {
        if (hostAliases.size() < 1) {
            throw new RuntimeException("Host '" + host.getHostname() + "' must have at least one <alias> tag.");
        }
        for (String alias : hostAliases) {
            addHostAlias(alias, host);
        }
    }

    /**
     * Adds an alias for the given host
     *
     * @param alias alias (string) for a Host
     * @param host  the {@link Host} to add the alias for
     */
    protected void addHostAlias(String alias, Host host) {
        if (alias2hostname.containsKey(alias)) {
            throw new RuntimeException("Alias '" + alias + "' must be used for only one host!");
        }
        alias2hostname.put(alias, host.getHostname());
        alias2host.put(alias, host);
    }

    public Map<String, Host> getAlias2host() {
        return alias2host;
    }

    @Override
    public String toString() {
        return "Hosts: " + hosts.keySet();
    }

}
