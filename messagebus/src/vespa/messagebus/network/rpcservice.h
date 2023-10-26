// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rpcserviceaddress.h"
#include <vespa/slobrok/imirrorapi.h>

namespace mbus {

class RPCNetwork;

/**
 * An RPCService represents a set of remote sessions matching a service pattern.
 * The sessions are monitored using the slobrok. If multiple sessions are
 * available, round robin is used to balance load between them.
 */
class RPCService {
private:
    using Mirror = slobrok::api::IMirrorAPI ;

    string        _serviceName;
    string        _connectionSpec;

public:
    using UP = std::unique_ptr<RPCService>;
    RPCService(const RPCService &) = delete;
    RPCService & operator = (const RPCService &) = delete;
    /**
     * Create a new RPCService backed by the given network and using
     * the given service pattern.
     *
     * @param mirror  The naming server to send queries to.
     * @param pattern The pattern to use when querying.
     */
    RPCService(const Mirror &mirror, const string &pattern);
    ~RPCService();

    /**
     * Resolve a concrete address from this service. This service may represent
     * multiple remote sessions, so this will select one that is online.
     *
     * @return A concrete service address.
     */
    RPCServiceAddress::UP make_address();

    bool isValid() const { return ! _connectionSpec.empty(); }
};

} // namespace mbus
