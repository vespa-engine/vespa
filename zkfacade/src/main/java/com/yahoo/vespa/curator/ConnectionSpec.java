// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.net.HostName;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A connection spec for Curator.
 *
 * @author mpolden
 */
class ConnectionSpec {

    private final String local;
    private final String ensemble;
    private final int ensembleSize;

    private ConnectionSpec(String local, String ensemble, int ensembleSize) {
        this.local = requireNonEmpty(local, "local spec");
        this.ensemble = requireNonEmpty(ensemble, "ensemble spec");
        this.ensembleSize = ensembleSize;
    }

    /** Returns the local spec. This may be a subset of the ensemble spec */
    public String local() {
        return local;
    }

    /** Returns the ensemble spec. This always contains all nodes in the ensemble */
    public String ensemble() {
        return ensemble;
    }

    /** Returns the number of servers in the ensemble */
    public int ensembleSize() {
        return ensembleSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectionSpec that = (ConnectionSpec) o;
        return ensembleSize == that.ensembleSize &&
               local.equals(that.local) &&
               ensemble.equals(that.ensemble);
    }

    @Override
    public int hashCode() {
        return Objects.hash(local, ensemble, ensembleSize);
    }

    public static ConnectionSpec create(String spec) {
        return create(spec, spec);
    }

    public static ConnectionSpec create(String localSpec, String ensembleSpec) {
        return new ConnectionSpec(localSpec, ensembleSpec, ensembleSpec.split(",").length);
    }

    public static <T> ConnectionSpec create(List<T> servers,
                                            Function<T, String> hostnameGetter,
                                            Function<T, Integer> portGetter,
                                            boolean localhostAffinity) {
        String localSpec = createSpec(servers, hostnameGetter, portGetter, localhostAffinity);
        String ensembleSpec = localhostAffinity ? createSpec(servers, hostnameGetter, portGetter, false) : localSpec;
        return new ConnectionSpec(localSpec, ensembleSpec, servers.size());
    }

    private static <T> String createSpec(List<T> servers,
                                         Function<T, String> hostnameGetter,
                                         Function<T, Integer> portGetter,
                                         boolean localhostAffinity) {
        String thisServer = HostName.getLocalhost();
        StringBuilder connectionSpec = new StringBuilder();
        for (var server : servers) {
            if (localhostAffinity && !thisServer.equals(hostnameGetter.apply(server))) continue;
            connectionSpec.append(hostnameGetter.apply(server));
            connectionSpec.append(':');
            connectionSpec.append(portGetter.apply(server));
            connectionSpec.append(',');
        }
        if (localhostAffinity && connectionSpec.length() == 0) {
            throw new IllegalArgumentException("Unable to create connect string to localhost: " +
                                               "There is no localhost server specified in config");
        }
        if (connectionSpec.length() > 0) {
            connectionSpec.setLength(connectionSpec.length() - 1); // Remove trailing comma
        }
        return connectionSpec.toString();
    }

    private static String requireNonEmpty(String s, String field) {
        if (Objects.requireNonNull(s).isEmpty()) throw new IllegalArgumentException(field + " must be non-empty");
        return s;
    }

}
