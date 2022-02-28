// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * This class is used when requesting additional metadata about an application's endpoint certificate from the provider.
 *
 * @author andreer
 */
public class EndpointCertificateDetails {

    private final String request_id;
    private final String requestor;
    private final String status;
    private final String ticket_id;
    private final String athenz_domain;
    private final List<EndpointCertificateRequestMetadata.DnsNameStatus> dnsnames;
    private final String duration_sec;
    private final String expiry;
    private final String private_key_kgname;
    private final String private_key_keyname;
    private final String private_key_version;
    private final String cert_key_kgname;
    private final String cert_key_keyname;
    private final String cert_key_version;
    private final String create_time;
    private final boolean expiry_protection;
    private final String public_key_algo;
    private final String issuer;
    private final String serial;

    public EndpointCertificateDetails(String request_id,
                                      String requestor,
                                      String status,
                                      String ticket_id,
                                      String athenz_domain,
                                      List<EndpointCertificateRequestMetadata.DnsNameStatus> dnsnames,
                                      String duration_sec,
                                      String expiry,
                                      String private_key_kgname,
                                      String private_key_keyname,
                                      String private_key_version,
                                      String cert_key_kgname,
                                      String cert_key_keyname,
                                      String cert_key_version,
                                      String create_time,
                                      boolean expiry_protection,
                                      String public_key_algo,
                                      String issuer,
                                      String serial) {
        this.request_id = request_id;
        this.requestor = requestor;
        this.status = status;
        this.ticket_id = ticket_id;
        this.athenz_domain = athenz_domain;
        this.dnsnames = dnsnames;
        this.duration_sec = duration_sec;
        this.expiry = expiry;
        this.private_key_kgname = private_key_kgname;
        this.private_key_keyname = private_key_keyname;
        this.private_key_version = private_key_version;
        this.cert_key_kgname = cert_key_kgname;
        this.cert_key_keyname = cert_key_keyname;
        this.cert_key_version = cert_key_version;
        this.create_time = create_time;
        this.expiry_protection = expiry_protection;
        this.public_key_algo = public_key_algo;
        this.issuer = issuer;
        this.serial = serial;
    }

    public String request_id() {
        return request_id;
    }

    public String requestor() {
        return requestor;
    }

    public String status() {
        return status;
    }

    public String ticket_id() {
        return ticket_id;
    }

    public String athenz_domain() {
        return athenz_domain;
    }

    public List<EndpointCertificateRequestMetadata.DnsNameStatus> dnsnames() {
        return dnsnames;
    }

    public String duration_sec() {
        return duration_sec;
    }

    public String expiry() {
        return expiry;
    }

    public String private_key_kgname() {
        return private_key_kgname;
    }

    public String private_key_keyname() {
        return private_key_keyname;
    }

    public String private_key_version() {
        return private_key_version;
    }

    public String cert_key_kgname() {
        return cert_key_kgname;
    }

    public String cert_key_keyname() {
        return cert_key_keyname;
    }

    public String cert_key_version() {
        return cert_key_version;
    }

    public String create_time() {
        return create_time;
    }

    public boolean expiry_protection() {
        return expiry_protection;
    }

    public String public_key_algo() {
        return public_key_algo;
    }

    public String issuer() {
        return issuer;
    }

    public String serial() {
        return serial;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", EndpointCertificateDetails.class.getSimpleName() + "[", "]")
                .add("request_id='" + request_id + "'")
                .add("requestor='" + requestor + "'")
                .add("status='" + status + "'")
                .add("ticket_id='" + ticket_id + "'")
                .add("athenz_domain='" + athenz_domain + "'")
                .add("dnsnames=" + dnsnames)
                .add("duration_sec='" + duration_sec + "'")
                .add("expiry='" + expiry + "'")
                .add("private_key_kgname='" + private_key_kgname + "'")
                .add("private_key_keyname='" + private_key_keyname + "'")
                .add("private_key_version='" + private_key_version + "'")
                .add("cert_key_kgname='" + cert_key_kgname + "'")
                .add("cert_key_keyname='" + cert_key_keyname + "'")
                .add("cert_key_version='" + cert_key_version + "'")
                .add("create_time='" + create_time + "'")
                .add("expiry_protection=" + expiry_protection)
                .add("public_key_algo='" + public_key_algo + "'")
                .add("issuer='" + issuer + "'")
                .add("serial='" + serial + "'")
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndpointCertificateDetails that = (EndpointCertificateDetails) o;
        return expiry_protection == that.expiry_protection
                && request_id.equals(that.request_id)
                && requestor.equals(that.requestor)
                && status.equals(that.status)
                && ticket_id.equals(that.ticket_id)
                && athenz_domain.equals(that.athenz_domain)
                && dnsnames.equals(that.dnsnames)
                && duration_sec.equals(that.duration_sec)
                && expiry.equals(that.expiry)
                && private_key_kgname.equals(that.private_key_kgname)
                && private_key_keyname.equals(that.private_key_keyname)
                && private_key_version.equals(that.private_key_version)
                && cert_key_kgname.equals(that.cert_key_kgname)
                && cert_key_keyname.equals(that.cert_key_keyname)
                && cert_key_version.equals(that.cert_key_version)
                && create_time.equals(that.create_time)
                && public_key_algo.equals(that.public_key_algo)
                && issuer.equals(that.issuer)
                && serial.equals(that.serial);
    }

    @Override
    public int hashCode() {
        return Objects.hash(request_id,
                requestor,
                status,
                ticket_id,
                athenz_domain,
                dnsnames,
                duration_sec,
                expiry,
                private_key_kgname,
                private_key_keyname,
                private_key_version,
                cert_key_kgname,
                cert_key_keyname,
                cert_key_version,
                create_time,
                expiry_protection,
                public_key_algo,
                issuer,
                serial);
    }
}
