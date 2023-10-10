// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.messagebus.network.ServiceAddress;
import com.yahoo.jrt.Spec;

/**
 * Implements the {@link ServiceAddress} interface for the RPC network.
 *
 * @author <a href="mailto:havardpe@yahoo-inc.com">Haavard Pettersen</a>
 */
public class RPCServiceAddress implements ServiceAddress {

    private final String serviceName;
    private final String sessionName;
    private final Spec connectionSpec;
    private RPCTarget target;

    /**
     * Constructs a service address from the given specifications. The last component of the service is stored as the
     * session name.
     *
     * @param serviceName    The full service name of the address.
     * @param connectionSpec The connection specification.
     */
    public RPCServiceAddress(String serviceName, Spec connectionSpec) {
        this.serviceName = serviceName;
        int pos = serviceName.lastIndexOf('/');
        sessionName =  (pos > 0 && pos < serviceName.length() - 1)
                ? serviceName.substring(pos + 1)
                : null;
        this.connectionSpec = connectionSpec;
    }
    public RPCServiceAddress(String serviceName, String connectionSpec) {
        this(serviceName, new Spec(connectionSpec));
    }
    public RPCServiceAddress(RPCServiceAddress blueprint) {
        serviceName = blueprint.serviceName;
        sessionName = blueprint.sessionName;
        connectionSpec = blueprint.connectionSpec;
        target = null;
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof RPCServiceAddress)) {
            return false;
        }
        RPCServiceAddress rhs = (RPCServiceAddress)obj;
        if (!serviceName.equals(rhs.serviceName)) {
            return false;
        }
        if (!connectionSpec.host().equals(rhs.connectionSpec.host())) {
            return false;
        }
        if (connectionSpec.port() != rhs.connectionSpec.port()) {
            return false;
        }
        if (!sessionName.equals(rhs.sessionName)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return serviceName.hashCode() + connectionSpec.hashCode() + sessionName.hashCode();
    }

    /**
     * Returns whether or not this service address is malformed.
     *
     * @return True if malformed.
     */
    public boolean isMalformed() {
        return sessionName == null || connectionSpec.malformed();
    }

    /**
     * Returns the name of the remove service.
     *
     * @return The service name.
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Returns the name of the remote session.
     *
     * @return The session name.
     */
    public String getSessionName() {
        return sessionName;
    }

    /**
     * Returns the connection spec for the remote service.
     *
     * @return The connection spec.
     */
    public Spec getConnectionSpec() {
        return connectionSpec;
    }

    /**
     * Sets the RPC target to be used when communicating with the remote service.
     *
     * @param target The target to set.
     */
    public void setTarget(RPCTarget target) {
        this.target = target;
    }

    /**
     * Returns the RPC target to be used when communicating with the remove service.
     *
     * @return The target to use.
     */
    public RPCTarget getTarget() {
        return target;
    }
}
