// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;


/**
 * A Spec is a network address used for either listening or
 * connecting.
 */
public class Spec implements Comparable<Spec> {

    private final String        host;
    private final int           port;
    private final boolean       malformed;
    private final String asString;

    private static SocketAddress createAddress(String host, int port) {
        return (host == null)
                ? new InetSocketAddress(port)
                : new InetSocketAddress(host, port);
    }

    private static String createString(String host, int port) {
        return (host == null)
                ?  "tcp/" + port
                : "tcp/" + host + ":" + port;
    }
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
            String hostStr = null;
            if (sep == -1) {
                portStr = spec.substring(4);
            } else {
                hostStr = spec.substring(4, sep);
                portStr = spec.substring(sep + 1);
            }
            boolean correct = true;
            int portNum = 0;
            try {
                portNum = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                correct = false;
            }
            port = portNum;
            malformed = ! correct;
            host = correct ? hostStr : null;
            asString = correct ? createString(host, port) : "MALFORMED";
        } else {
            malformed = true;
            port = 0;
            host = null;
            asString = "MALFORMED";
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
        malformed = false;
        asString = createString(host, port);
    }

    /**
     * Create a Spec with a wildcard address.
     *
     * WARNING: Do not use this constructor to connect to localhost - use e.g. Spec("localhost", port) instead.
     * Why? Because Java may end up picking the wrong localhost hostname to connect to (for reasons
     * detailed in HostName.getLocalhost).
     *
     * @param port port number
     */
    public Spec(int port) {
        this(null, port);
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
     * Resolve the socket address for this Spec. If this Spec is
     * malformed, this method will return null.
     *
     * @return socket address
     */
    SocketAddress resolveAddress() {
        return !malformed ? createAddress(host, port) : null;
    }

    /**
     * Obtain a string representation of this address. The return
     * value from this method may be used to create a new Spec.
     *
     * @return string representation of this address
     */
    public String toString() {
        return asString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Spec spec = (Spec) o;
        return port == spec.port &&
                malformed == spec.malformed &&
                Objects.equals(host, spec.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, malformed);
    }

    @Override
    public int compareTo(Spec o) {
        int cmp = 0;
        if ((host != null) && (o.host != null)) {
            cmp = host.compareTo(o.host);
        } else if (host != null) {
            return -1;
        } else if (o.host != null) {
            return 1;
        }
        return (cmp == 0)
                ? Integer.compare(port, o.port)
                : cmp;

    }
}
