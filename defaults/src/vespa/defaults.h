// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>

namespace vespa {

/**
 * This is a class containing the defaults for basic Vespa configuration variables.
 **/
class Defaults {
public:
    /**
     * bootstrap VESPA_HOME (if unset) from argv[0]
     **/
    static void bootstrap(const char *argv0);

    /**
     * Compute the path prefix where Vespa files will live;
     * the return value ends with a "/" so you can just append
     * the relative pathname to the file(s) you want.
     *
     * @return the vespa home directory, ending by "/"
     **/
    static const char *vespaHome();

    static std::string underVespaHome(const char *path);

    /**
     * Compute the user name to own directories and run processes.
     * @return the vespa user name
     **/
    static const char *vespaUser();

    /**
     * Compute the host name that identifies myself.
     * Detection of the hostname is now done before starting any Vespa
     * programs and provided in the environment variable VESPA_HOSTNAME;
     * if that variable isn't set a default of "localhost" is always returned.
     * @return the vespa host name
     **/
    static const char *vespaHostname();

    /**
     * Compute the port number where the Vespa webservice
     * container should be available.
     *
     * @return the vespa webservice port
     **/
    static int vespaWebServicePort();

    /**
     * Compute the base for port numbers where the Vespa services
     * should listen.
     *
     * @return the vespa base number for ports
     **/
    static int vespaPortBase();

    /**
     * Find the hostnames of configservers that are configured
     * @return a list of hostnames
     **/
    static std::vector<std::string> vespaConfigServerHosts();

    /**
     * Find the RPC port for talking to configservers
     *
     * @return the RPC port number
     **/
    static int vespaConfigServerRpcPort();

    /**
     * Find the RPC addresses to configservers that are configured
     * @return a list of RPC specs in the format tcp/{hostname}:{portnumber}
     **/
    static std::vector<std::string> vespaConfigServerRpcAddrs();

    /**
     * Find the URLs to the REST api on configservers
     * @return a list of URLS in the format http://{hostname}:{portnumber}/
     **/
    static std::vector<std::string> vespaConfigServerRestUrls();

    /**
     * Find the RPC address to the local config proxy
     * @return one RPC spec in the format tcp/{hostname}:{portnumber}
     **/
    static std::string vespaConfigProxyRpcAddr();

    /**
     * Get the RPC addresses to all known config sources
     * @return same as vespaConfigProxyRpcAddr + vespaConfigServerRpcAddrs
     **/
    static std::vector<std::string> vespaConfigSourcesRpcAddrs();
};

} // namespace vespa
