// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.instance;

import com.yahoo.security.Pkcs10Csr;

import java.util.Objects;

/**
 * Information for refreshing a instance in the system. This is the same type as InstanceRefreshInformation type in
 * the ZTS API.
 *
 * @author mpolden
 */
public class InstanceRefresh {

    private final Pkcs10Csr csr;

    public InstanceRefresh(Pkcs10Csr csr) {
        this.csr = Objects.requireNonNull(csr, "csr must be non-null");
    }

    /** The Certificate Signed Request describing the wanted certificate */
    public Pkcs10Csr csr() {
        return csr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceRefresh that = (InstanceRefresh) o;
        return csr.equals(that.csr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(csr);
    }

}
