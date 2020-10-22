// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.defaults;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * The defaults of basic Vespa configuration variables.
 * Use Defaults.getDefaults() to access the defaults of this runtime environment.
 *
 * @author arnej27959
 * @author bratseth
 */
public class Defaults {

    private static final Logger log = Logger.getLogger(Defaults.class.getName());
    private static final Defaults defaults = new Defaults();

    private final String vespaHome;
    private final String vespaUser;
    private final String vespaHost;
    private final String temporaryApplicationStorage;
    private final int vespaWebServicePort;
    private final int vespaPortBase;
    private final int vespaPortConfigServerRpc;
    private final int vespaPortConfigServerHttp;
    private final int vespaPortConfigProxyRpc;

    private Defaults() {
        vespaHome = findVespaHome("/opt/vespa");
        vespaUser = findVespaUser("vespa");
        vespaHost = findVespaHostname("localhost");
        temporaryApplicationStorage = underVespaHome("var/vespa/application");
        vespaWebServicePort = findWebServicePort(8080);
        vespaPortBase = findVespaPortBase(19000);
        vespaPortConfigServerRpc = findConfigServerPort(vespaPortBase + 70);
        vespaPortConfigServerHttp = vespaPortConfigServerRpc + 1;
        vespaPortConfigProxyRpc = findConfigProxyPort(vespaPortBase + 90);
    }

    static private String findVespaHome(String defHome) {
        Optional<String> vespaHomeEnv = Optional.ofNullable(System.getenv("VESPA_HOME"));
        if ( ! vespaHomeEnv.isPresent() || vespaHomeEnv.get().trim().isEmpty()) {
            log.info("VESPA_HOME not set, using " + defHome);
            return defHome;
        }
        String vespaHome = vespaHomeEnv.get().trim();
        if (vespaHome.endsWith("/")) {
            int sz = vespaHome.length() - 1;
            vespaHome = vespaHome.substring(0, sz);
        }
        return vespaHome;
    }

    static private String findVespaHostname(String defHost) {
        Optional<String> vespaHostEnv = Optional.ofNullable(System.getenv("VESPA_HOSTNAME"));
        if (vespaHostEnv.isPresent() && ! vespaHostEnv.get().trim().isEmpty()) {
            return vespaHostEnv.get().trim();
        }
        return defHost;
    }

    static private String findVespaUser(String defUser) {
        Optional<String> vespaUserEnv = Optional.ofNullable(System.getenv("VESPA_USER"));
        if (! vespaUserEnv.isPresent()) {
            log.fine("VESPA_USER not set, using "+defUser);
            return defUser;
        }
        return vespaUserEnv.get().trim();
    }

    static private int findPort(String varName, int defaultPort) {
        Optional<String> port = Optional.ofNullable(System.getenv(varName));
        if ( ! port.isPresent() || port.get().trim().isEmpty()) {
            log.fine("" + varName + " not set, using " + defaultPort);
            return defaultPort;
        }
        try {
            return Integer.parseInt(port.get());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("must be an integer, was '" +
                                               port.get() + "'");
        }
    }

    static private int findVespaPortBase(int defaultPort) {
        return findPort("VESPA_PORT_BASE", defaultPort);
    }
    static private int findConfigServerPort(int defaultPort) {
        return findPort("port_configserver_rpc", defaultPort);
    }
    static private int findConfigProxyPort(int defaultPort) {
        return findPort("port_configproxy_rpc", defaultPort);
    }
    static private int findWebServicePort(int defaultPort) {
        return findPort("VESPA_WEB_SERVICE_PORT", defaultPort);
    }

    /**
     * Get the username to own directories, files and processes
     * @return the vespa user name
     **/
    public String vespaUser() { return vespaUser; }


    /**
     * Compute the host name that identifies myself.
     * Detection of the hostname is now done before starting any Vespa
     * programs and provided in the environment variable VESPA_HOSTNAME;
     * if that variable isn't set a default of "localhost" is always returned.
     * @return the vespa host name
     **/
    public String vespaHostname() { return vespaHost; }

    /**
     * Returns the path where a Vespa application can store arbitrary files on the node. This path 
     * is persistent during the lifetime of this node. The application must be able to recreate
     * required files on its own (e.g. by downloading them from a remote source) if missing.
     *
     * @return the local application storage path
     */
    public String temporaryApplicationStorage() { return temporaryApplicationStorage; }

    /**
     * Returns the path to the root under which Vespa should read and write files.
     * Will not end with a "/".
     *
     * @return the vespa home directory
     */
    public String vespaHome() { return vespaHome; }

    /**
     * Returns an absolute path produced by prepending vespaHome to the argument if it is relative.
     * If the path starts by "/" (absolute) or "./" (explicitly relative - useful for tests),
     * it is returned as-is.
     *
     * @param path the path to prepend vespaHome to unless it is absolute
     * @return the given path string with the root path given from
     *         vespaHome() prepended, unless the given path is absolute, in which
     *         case it is be returned as-is
     */
    public String underVespaHome(String path) {
        if (path.startsWith("/")) return path;
        if (path.startsWith("./")) return path;
        return vespaHome() + "/" + path;
    }

    /**
     * Returns the port number where Vespa web services should be available.
     *
     * @return the vespa webservice port
     */
    public int vespaWebServicePort() { return vespaWebServicePort; }

    /**
     * Returns the base for port numbers where the Vespa services should listen.
     *
     * @return the vespa base number for ports
     */
    public int vespaPortBase() { return vespaPortBase; }

    /** @return port number used by cloud config server (for its RPC protocol) */
    public int vespaConfigServerRpcPort() { return vespaPortConfigServerRpc; }

    /** @return port number used by cloud config server (REST api on HTTP) */
    public int vespaConfigServerHttpPort() { return vespaPortConfigServerHttp; }

    /** @return port number used by config proxy server (RPC protocol) */
    public int vespaConfigProxyRpcPort() { return vespaPortConfigProxyRpc; }

    /** Returns the defaults of this runtime environment */
    public static Defaults getDefaults() { return defaults; }

}
