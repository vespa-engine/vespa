// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <vbench/core/string.h>

namespace vbench {

/**
 * Simple wrapper specifying the host and port of a server. This will
 * typically be a HTTP server.
 **/
struct ServerSpec
{
    string host;
    int    port;

    ServerSpec() : host(), port(0) {}
    ServerSpec(const string &h, int p) : host(h), port(p) {}
    bool operator==(const ServerSpec &rhs) const {
        return (port == rhs.port && host == rhs.host);
    }
    bool operator<(const ServerSpec &rhs) const {
        if (port == rhs.port) {
            return (host < rhs.host);
        }
        return (port < rhs.port);
    }
};

} // namespace vbench

