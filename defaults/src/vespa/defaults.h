// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

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
};

} // namespace vespa
