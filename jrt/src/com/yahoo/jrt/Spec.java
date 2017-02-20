// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import com.yahoo.net.HostName;

import java.net.InetSocketAddress;
import java.net.SocketAddress;


/**
 * A Spec is a network address used for either listening or
 * connecting.
 */
public class Spec {

    private SocketAddress address;
    private String        host;
    private int           port;
    private boolean       malformed;

    /**
     * Create a Spec from a string. The form of the input string is
     * 'tcp/host:port' or 'tcp/port' where 'host' is the host name and
     * 'port' is the port number.
     *
     * @param spec input string to be parsed
     * @see #malformed
     */
    public Spec(String spec) {
        if (spec.startsWith("tcp/")) {
            int sep = spec.indexOf(':');
            String portStr;
            if (sep == -1) {
                portStr = spec.substring(4);
            } else {
                host = spec.substring(4, sep);
                portStr = spec.substring(sep + 1);
            }
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                host = null;
                port = 0;
                malformed = true;
            }
        } else {
            malformed = true;
        }
    }

    /**
     * Create a Spec from a host name and a port number.
     *
     * @param host host name
     * @param port port number
     */
    public Spec(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Create a Spec from a port number.
     *
     * @param port port number
     */
    public Spec(int port) {
        this.port = port;
    }

    /**
     * Creates a Spec with the hostname of the current/local host and given port
     *
     * @param port port number
     */
    public static Spec fromLocalHostName(int port) {
      return new Spec(HostName.getLocalhost(), port);
    }

    /**
     * Obtain the host name of this address
     *
     * @return host name
     */
    public String host() {
        return host;
    }

    /**
     * Obtain the port number if this address
     *
     * @return port number
     */
    public int port() {
        return port;
    }

    /**
     * If this Spec was created from a string, this method will tell
     * you whether that string was malformed.
     *
     * @return true if this address is malformed
     */
    public boolean malformed() {
        return malformed;
    }

    /**
     * Resolve the listening socket address for this Spec. If this Spec is
     * malformed, this method will return null.
     *
     * @return listening socket address
     */
    SocketAddress listenAddress() {
        return address(host);
    }

    /**
     * Resolve the connect socket address for this Spec. If this Spec is
     * malformed, this method will return null.
     *
     * Why is this different than listenAddress()? If no host is given, a SocketAddress will be bound
     * to the wildcard address (INADDR_ANY or 0.0.0.0 assuming IPv4) by InetSocketAddress. A wildcard
     * address used to connect (at least SocketChannel::open) is interpreted as InetAddress::getLocalHost,
     * which is wrong for the reasons stated in HostName::getLocalhost.
     *
     * @return connect socket address
     */
    SocketAddress connectAddress() {
        return address(HostName.getLocalhost());
    }

    private SocketAddress address(String hostOverride) {
        if (malformed) {
            return null;
        }
        if (address == null) {
            if (hostOverride == null) {
                address = new InetSocketAddress(port);
            } else {
                address = new InetSocketAddress(hostOverride, port);
            }
        }
        return address;
    }

    /**
     * Obtain a string representation of this address. The return
     * value from this method may be used to create a new Spec.
     *
     * @return string representation of this address
     */
    public String toString() {
        if (malformed) {
            return "MALFORMED";
        }
        if (host == null) {
            return "tcp/" + port;
        }
        return "tcp/" + host + ":" + port;
    }

}
