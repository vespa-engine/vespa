package com.yahoo.config.provision;

import ai.vespa.validation.Validation;

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
     * Bump this to provision new services, whenever we change regional endpoint names.
     * This will cause new endpoint services to be provisioned, with new domain names.
     * TODO: wire multiple service IDs to and through the controller.
     */
    public static final int generation = 0;
    public static final ZoneEndpoint defaultEndpoint = new ZoneEndpoint(true, false, List.of());
    public static final ZoneEndpoint privateEndpoint = new ZoneEndpoint(false, false, List.of());

    private final boolean isPublicEndpoint;
    private final boolean isPrivateEndpoint;
    private final List<AllowedUrn> allowedUrns;

    public ZoneEndpoint(boolean isPublicEndpoint, boolean isPrivateEndpoint, List<AllowedUrn> allowedUrns) {
        this.isPublicEndpoint = isPublicEndpoint;
        this.isPrivateEndpoint = isPrivateEndpoint;
        this.allowedUrns = List.copyOf(allowedUrns);
    }

    /** Whether this has an endpoint which is visible from the public internet. */
    public boolean isPublicEndpoint() {
        return isPublicEndpoint;
    }

    /** Whether this has an endpoint which is visible through private DNS of the cloud. */
    public boolean isPrivateEndpoint() {
        return isPrivateEndpoint;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZoneEndpoint that = (ZoneEndpoint) o;
        return isPublicEndpoint == that.isPublicEndpoint && isPrivateEndpoint == that.isPrivateEndpoint && allowedUrns.equals(that.allowedUrns);
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
