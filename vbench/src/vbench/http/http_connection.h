// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <memory>
#include <vbench/core/socket.h>
#include "server_spec.h"

namespace vbench {

/**
 * A connection to a specific server that can be reused at a later
 * time to support persistent connections.
 **/
class HttpConnection
{
private:
    ServerSpec _server;
    Socket     _socket;
    double     _lastUsed;

public:
    using CryptoEngine = vespalib::CryptoEngine;
    using UP = std::unique_ptr<HttpConnection>;

    HttpConnection(CryptoEngine &crypto, const ServerSpec &server);
    bool fresh() const { return (_lastUsed < 0); }
    const ServerSpec &server() const { return _server; }
    Stream &stream() { return _socket; }
    void touch(double now) { _lastUsed = now; }
    bool mayReuse(double now) const;
};

} // namespace vbench

