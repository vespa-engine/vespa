// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.defaults;

import java.util.Optional;
import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.InputStreamReader;

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
    private final int vespaWebServicePort;
    private final int vespaPortBase;
    private final String hostName;

    private Defaults() {
        vespaHome = findVespaHome();
        vespaWebServicePort = findVespaWebServicePort();
        vespaPortBase = 19000; // TODO
        hostName = findHostName();
    }

    private String findHostName() {
        Optional<String> vespaHostName = Optional.ofNullable(System.getenv("VESPA_HOSTNAME"));
        if (vespaHostName.isPresent() && ! vespaHostName.get().trim().isEmpty()) {
            return vespaHostName.get().trim();
        }
        try {
            Process p = Runtime.getRuntime().exec("hostname");
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String myHost = in.readLine();
            p.waitFor();
            if (p.exitValue() != 0) {
                log.warning("Command 'hostname' failed: exit("+p.exitValue()+")");
            } else if (myHost.trim().isEmpty()) {
                log.warning("Command 'hostname' did not give any output");
            } else {
                return myHost.trim();
            }
        } catch (Exception e) {
            log.warning("Failure executing command 'hostname': " + e);
        }
        return "localhost";
    }

    private String findVespaHome() {
        Optional<String> vespaHomeEnv = Optional.ofNullable(System.getenv("VESPA_HOME"));
        if ( ! vespaHomeEnv.isPresent() || vespaHomeEnv.get().trim().isEmpty()) {
            log.info("VESPA_HOME not set, using /opt/yahoo/vespa/");
            return "/opt/yahoo/vespa/";
        }
        String vespaHome = vespaHomeEnv.get();
        if ( ! vespaHome.endsWith("/"))
            vespaHome = vespaHome + "/";
        return vespaHome;
    }

    private int findVespaWebServicePort() {
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
     * Returns the canonical (FQDN) name of the host,
     * which should work for other hosts to connect to
     **/
    public String canonicalHostName() { return hostName; }

    /**
     * Returns the path to the root under which Vespa should read and write files, ending by "/".
     *
     * @return the vespa home directory, ending by "/"
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
        return vespaHome() + path;
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
