// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include "iserviceaddress.h"
#include "rpctarget.h"

namespace mbus {

/**
 * An RPCServiceAddress contains the service name, connection spec and
 * session name of a concrete remote rpc service.
 **/
class RPCServiceAddress : public IServiceAddress {
private:
    string   _serviceName;
    string   _sessionName;
    string   _connectionSpec;
    RPCTarget::SP _target;

public:
    /**
     * Convenience typedefs.
     */
    using UP = std::unique_ptr<RPCServiceAddress>;

    /**
     * Constructs a service address from the given specifications. The last component of the service is stored
     * as the session name.
     *
     * @param serviceName    The full service name of the address.
     * @param connectionSpec The connection specification.
     */
    RPCServiceAddress(const string &serviceName, const string &connectionSpec);
    ~RPCServiceAddress() override;

    /**
     * Returns whether or not this service address is malformed.
     *
     * @return True if malformed.
     */
    bool isMalformed();

    /**
     * Returns the name of the remove service.
     *
     * @return The service name.
     */
    const string &getServiceName() const { return _serviceName; }

    /**
     * Returns the name of the remote session.
     *
     * @return The session name.
     */
    const string &getSessionName() const { return _sessionName; }

    /**
     * Returns the connection spec for the remote service.
     *
     * @return The connection spec.
     */
    const string &getConnectionSpec() const { return _connectionSpec; }

    /**
     * Sets the RPC target to be used when communicating with the remove service.
     *
     * @param target The target to set.
     */
    void setTarget(RPCTarget::SP target) { _target = std::move(target); }

    /**
     * Returns the RPC target to be used when communicating with the remove service. Make sure that {@link
     * hasTarget()} returns true before calling this method, or you will be deref'ing null.
     *
     * @return The target to use.
     */
    RPCTarget &getTarget() { return *_target; }

    /**
     * Returns whether or not this has an RPC target set.
     *
     * @return True if target is set.
     */
    bool hasTarget() const { return bool(_target); }
};

} // namespace mbus

