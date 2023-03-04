// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.instance;

import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.Optional;

/**
 * A signed instance identity object that includes a client certificate. This is the result of a successful
 * {@link InstanceRegistration} and is the same type as InstanceIdentity in the ZTS API.
 *
 * @author mpolden
 */
public class InstanceIdentity {

    private final String provider;
    private final String service;
    private final String instanceId;
    private final Optional<X509Certificate> x509Certificate;

    public InstanceIdentity(String provider, String service, String instanceId, Optional<X509Certificate> x509Certificate) {
        this.provider = Objects.requireNonNull(provider, "provider must be non-null");
        this.service = Objects.requireNonNull(service, "service must be non-null");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId must be non-null");
        this.x509Certificate = Objects.requireNonNull(x509Certificate, "x509Certificate must be non-null");
    }

    /** Same as {@link InstanceRegistration#domain()} */
    public String provider() {
        return provider;
    }

    /** Same as {@link InstanceRegistration#service()} ()} */
    public String service() {
        return service;
    }

    /** A unique identifier of the instance to which the certificate is issued */
    public String instanceId() {
        return instanceId;
    }

    /** The issued certificate */
    public Optional<X509Certificate> x509Certificate() {
        return x509Certificate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceIdentity that = (InstanceIdentity) o;
        return provider.equals(that.provider) &&
               service.equals(that.service) &&
               instanceId.equals(that.instanceId) &&
               x509Certificate.equals(that.x509Certificate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, service, instanceId, x509Certificate);
    }

}
