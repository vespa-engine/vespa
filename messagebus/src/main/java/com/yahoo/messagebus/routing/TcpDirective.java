// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

/**
 * This class represents a tcp directive within a {@link Hop}'s selector. This is a connection string used to establish
 * a direct connection to a host, bypassing service lookups through Slobrok.
 *
 * @author Simon Thoresen Hult
 */
public class TcpDirective implements HopDirective {

    private final String host;
    private final int port;
    private final String session;

    /**
     * Constructs a new directive to route directly to a tcp address.
     *
     * @param host    The host name to connect to.
     * @param port    The port to connect to.
     * @param session The session to route to.
     */
    public TcpDirective(String host, int port, String session) {
        this.host = host;
        this.port = port;
        this.session = session;
    }

    @Override
    public boolean matches(HopDirective dir) {
        if (!(dir instanceof TcpDirective)) {
            return false;
        }
        TcpDirective rhs = (TcpDirective)dir;
        return host.equals(rhs.host) && port == rhs.port && session.equals(rhs.session);
    }

    /**
     * Returns the host to connect to. This may be an ip address or a name.
     *
     * @return The host.
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the port to connect to on the remove host.
     *
     * @return The port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the name of the session to route to.
     *
     * @return The session name.
     */
    public String getSession() {
        return session;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TcpDirective)) {
            return false;
        }
        TcpDirective rhs = (TcpDirective)obj;
        if (!host.equals(rhs.host)) {
            return false;
        }
        if (port != rhs.port) {
            return false;
        }
        if (!session.equals(rhs.session)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "tcp/" + host + ":" + port + "/" + session;
    }

    @Override
    public String toDebugString() {
        return "TcpDirective(host = '" + host + "', port = " + port + ", session = '" + session + "')";
    }

    @Override
    public int hashCode() {
        int result = host != null ? host.hashCode() : 0;
        result = 31 * result + port;
        result = 31 * result + (session != null ? session.hashCode() : 0);
        return result;
    }
}
