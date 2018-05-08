// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.defaults;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.util.Optional;



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
    private final int vespaWebServicePort;
    private final int vespaPortBase;

    private Defaults() {
        vespaHome = findVespaHome();
        vespaUser = findVespaUser();
        vespaHost = findVespaHostname();
        vespaWebServicePort = findVespaWebServicePort();
        vespaPortBase = 19000; // TODO
    }

    static private String findVespaHome() {
        Optional<String> vespaHomeEnv = Optional.ofNullable(System.getenv("VESPA_HOME"));
        if ( ! vespaHomeEnv.isPresent() || vespaHomeEnv.get().trim().isEmpty()) {
            log.info("VESPA_HOME not set, using /opt/vespa");
            return "/opt/vespa";
        }
        String vespaHome = vespaHomeEnv.get().trim();
        if (vespaHome.endsWith("/")) {
            int sz = vespaHome.length() - 1;
            vespaHome = vespaHome.substring(0, sz);
        }
        return vespaHome;
    }

    static private String findVespaHostname() {
        Optional<String> vespaHostEnv = Optional.ofNullable(System.getenv("VESPA_HOSTNAME"));
        if (vespaHostEnv.isPresent() && ! vespaHostEnv.get().trim().isEmpty()) {
            return vespaHostEnv.get().trim();
        }
        return "localhost";
    }

    static private String findVespaUser() {
        Optional<String> vespaUserEnv = Optional.ofNullable(System.getenv("VESPA_USER"));
        if (! vespaUserEnv.isPresent()) {
            log.fine("VESPA_USER not set, using vespa");
            return "vespa";
        }
        return vespaUserEnv.get().trim();
    }

    static private int findVespaWebServicePort() {
        Optional<String> vespaWebServicePortString = Optional.ofNullable(System.getenv("VESPA_WEB_SERVICE_PORT"));
        if ( ! vespaWebServicePortString.isPresent() || vespaWebServicePortString.get().trim().isEmpty()) {
            log.info("VESPA_WEB_SERVICE_PORT not set, using 8080");
            return 8080;
        }
        try {
            return Integer.parseInt(vespaWebServicePortString.get());
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("VESPA_WEB_SERVICE_PORT must be an integer, was '" +
                                               vespaWebServicePortString.get() + "'");
        }
    }

    /**
     * Get the username to own directories, files and processes
     * @return the vespa user name
     **/
    public String vespaUser() { return vespaUser; }


    /**
     * Compute the host name that identifies myself
     * @return the vespa host name
     **/
    public String vespaHostname() { return vespaHost; }

    /**
     * Returns the path to the root under which Vespa should read and write files.
     * Will not end with a "/".
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

    /** Returns the defaults of this runtime environment */
    public static Defaults getDefaults() { return defaults; }

}
