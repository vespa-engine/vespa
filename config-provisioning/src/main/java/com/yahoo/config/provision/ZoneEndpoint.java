// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.validation.Validation;
import com.yahoo.config.provision.zone.AuthMethod;

import java.util.List;
import java.util.Objects;

/**
 * Settings for a zone endpoint of a deployment.
 *
 * @author jonmv
 */
public class ZoneEndpoint {

    /**
     * Endpoint service generation.
     * <p>
     * This is used to transition to a new set of endpoint services, with new domain names.
     * The procedure is:
     * <ol>
     *     <li>Start using new endpoint names (in controller code), for <em>all</em> applications.</li>
     *     <li>Bump the generation counter here; this causes new services to be provisioned.</li>
     *     <li>Controller configures the new services with the new endpoint names.</li>
     *     <li>Let users migrate to the new endpoint names.</li>
     *     <li>Currently missing: clean up obsolete, unused endpoint services.</li>
     * </ol>
     */
    public static final int generation = 0;
    public static final ZoneEndpoint defaultEndpoint = new ZoneEndpoint(true, false, List.of(AuthMethod.mtls), List.of());
    public static final ZoneEndpoint privateEndpoint = new ZoneEndpoint(false, false, List.of(AuthMethod.mtls), List.of());

    private final boolean isPublicEndpoint;
    private final boolean isPrivateEndpoint;
    private final List<AuthMethod> authMethods;
    private final List<AllowedUrn> allowedUrns;

    public ZoneEndpoint(boolean isPublicEndpoint, boolean isPrivateEndpoint, List<AllowedUrn> allowedUrns) {
        this(isPublicEndpoint, isPrivateEndpoint, List.of(AuthMethod.mtls), allowedUrns);
    }

    public ZoneEndpoint(boolean isPublicEndpoint, boolean isPrivateEndpoint, List<AuthMethod> authMethods, List<AllowedUrn> allowedUrns) {
        this.isPublicEndpoint = isPublicEndpoint;
        this.isPrivateEndpoint = isPrivateEndpoint;
        this.authMethods = authMethods;
        this.allowedUrns = List.copyOf(allowedUrns);
    }

    /** Whether this has an endpoint accessible from the public internet, e.g. public IP and DNS records. */
    public boolean isPublicEndpoint() {
        return isPublicEndpoint;
    }

    /** Whether this has an endpoint which is visible through private DNS of the cloud. */
    public boolean isPrivateEndpoint() {
        return isPrivateEndpoint;
    }

    /** List of allowed authentication methods for private endpoints. */
    public List<AuthMethod> authMethods() {
        return authMethods;
    }

    /** List of allowed URNs, for specified private access types. */
    public List<AllowedUrn> allowedUrns() {
        return allowedUrns;
    }

    /** List of URNs for the given access type. */
    public List<String> allowedUrnsWith(AccessType type) {
        return allowedUrns.stream().filter(urn -> urn.type == type).map(AllowedUrn::urn).toList();
    }

    public boolean isDefault() {
        return equals(defaultEndpoint);
    }

    public ZoneEndpoint withAuthMethods(List<AuthMethod> authMethods) {
        return new ZoneEndpoint(
                this.isPublicEndpoint,
                this.isPrivateEndpoint,
                authMethods,
                this.allowedUrns
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZoneEndpoint that = (ZoneEndpoint) o;
        return isPublicEndpoint == that.isPublicEndpoint && isPrivateEndpoint == that.isPrivateEndpoint &&
                authMethods.equals(that.authMethods) && allowedUrns.equals(that.allowedUrns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isPublicEndpoint, isPrivateEndpoint, allowedUrns);
    }

    @Override
    public String toString() {
        return "ZoneEndpoint{" +
               "isPublicEndpoint=" + isPublicEndpoint +
               ", isPrivateEndpoint=" + isPrivateEndpoint +
               ", allowedUrns=" + allowedUrns +
               '}';
    }

    public enum AccessType {
        awsPrivateLink,
        gcpServiceConnect,
    }

    /** A URN allowed to access this (private) endpoint, through a {@link AccessType} method. */
    public static class AllowedUrn {

        private final AccessType type;
        private final String urn;

        public AllowedUrn(AccessType type, String urn) {
            this.type = Objects.requireNonNull(type);
            this.urn = Validation.requireNonBlank(urn, "URN");
        }

        /** Type of private connection. */
        public AccessType type() {
            return type;
        }

        /** URN allowed to access this private endpoint. */
        public String urn() {
            return urn;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AllowedUrn that = (AllowedUrn) o;
            return type == that.type && urn.equals(that.urn);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, urn);
        }

        @Override
        public String toString() {
            return "'" + urn + "' through '" +
                   switch (type) {
                       case awsPrivateLink -> "aws-private-link";
                       case gcpServiceConnect -> "gcp-service-connect";
                   } + "'";
        }

    }

}
