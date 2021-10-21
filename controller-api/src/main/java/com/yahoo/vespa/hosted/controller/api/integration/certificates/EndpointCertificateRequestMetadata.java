// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.util.List;
import java.util.Objects;

/**
 * This class is used for metadata about an application's endpoint certificate received from the certificate provider.
 *
 * @author andreer
 */
public class EndpointCertificateRequestMetadata {

    public EndpointCertificateRequestMetadata(String requestId,
                                              String requestor,
                                              String ticketId,
                                              String athenzDomain,
                                              List<DnsNameStatus> dnsNames,
                                              long durationSec,
                                              String status,
                                              String createTime,
                                              long expiry,
                                              String issuer,
                                              String publicKeyAlgo) {
        this.requestId = requestId;
        this.requestor = requestor;
        this.ticketId = ticketId;
        this.athenzDomain = athenzDomain;
        this.dnsNames = dnsNames;
        this.durationSec = durationSec;
        this.status = status;
        this.createTime = createTime;
        this.expiry = expiry;
        this.issuer = issuer;
        this.publicKeyAlgo = publicKeyAlgo;
    }

    public static class DnsNameStatus {
        public final String dnsName;
        public final String status;

        public DnsNameStatus(String dnsName, String status) {
            this.dnsName = dnsName;
            this.status = status;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DnsNameStatus that = (DnsNameStatus) o;
            return dnsName.equals(that.dnsName) && status.equals(that.status);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dnsName, status);
        }

        @Override
        public String toString() {
            return "DnsNameStatus{" +
                    "dnsName='" + dnsName + '\'' +
                    ", status='" + status + '\'' +
                    '}';
        }
    }

    private final String requestId;
    private final String requestor;
    private final String ticketId;
    private final String athenzDomain;
    private final List<DnsNameStatus> dnsNames;
    private final long durationSec;
    private final String status;
    private final String createTime; // ISO 8601
    private final long expiry;
    private final String issuer;
    private final String publicKeyAlgo;

    public String requestId() {
        return requestId;
    }

    public String requestor() {
        return requestor;
    }

    public String ticketId() {
        return ticketId;
    }

    public String athenzDomain() {
        return athenzDomain;
    }

    public List<DnsNameStatus> dnsNames() {
        return dnsNames;
    }

    public long durationSec() {
        return durationSec;
    }

    public String status() {
        return status;
    }

    public String createTime() {
        return createTime;
    }

    public long expiry() {
        return expiry;
    }

    public String issuer() {
        return issuer;
    }

    public String publicKeyAlgo() {
        return publicKeyAlgo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndpointCertificateRequestMetadata that = (EndpointCertificateRequestMetadata) o;
        return durationSec == that.durationSec &&
                expiry == that.expiry &&
                requestId.equals(that.requestId) &&
                requestor.equals(that.requestor) &&
                ticketId.equals(that.ticketId) &&
                athenzDomain.equals(that.athenzDomain) &&
                dnsNames.equals(that.dnsNames) &&
                status.equals(that.status) &&
                createTime.equals(that.createTime) &&
                issuer.equals(that.issuer) &&
                publicKeyAlgo.equals(that.publicKeyAlgo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                requestId,
                requestor,
                ticketId,
                athenzDomain,
                dnsNames,
                durationSec,
                status,
                createTime,
                expiry,
                issuer,
                publicKeyAlgo);
    }

    @Override
    public String toString() {
        return "EndpointCertificateRequestMetadata{" +
                "requestId='" + requestId + '\'' +
                ", requestor='" + requestor + '\'' +
                ", ticketId='" + ticketId + '\'' +
                ", athenzDomain='" + athenzDomain + '\'' +
                ", dnsNames=" + dnsNames +
                ", durationSec=" + durationSec +
                ", status='" + status + '\'' +
                ", createTime='" + createTime + '\'' +
                ", expiry=" + expiry +
                ", issuer='" + issuer + '\'' +
                ", publicKeyAlgo='" + publicKeyAlgo + '\'' +
                '}';
    }
}
