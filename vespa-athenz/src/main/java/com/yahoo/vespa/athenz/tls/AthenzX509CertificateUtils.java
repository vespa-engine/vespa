// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import com.yahoo.security.SubjectAlternativeName;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import static com.yahoo.security.SubjectAlternativeName.Type;

/**
 * Utility methods for Athenz issued x509 certificates
 *
 * @author bjorncs
 */
public class AthenzX509CertificateUtils {

    private AthenzX509CertificateUtils() {}

    public static AthenzIdentity getIdentityFromRoleCertificate(X509Certificate certificate) {
        List<SubjectAlternativeName> sans = X509CertificateUtils.getSubjectAlternativeNames(certificate);
        return getRoleIdentityFromEmail(sans)
                .or(() -> getRoleIdentityFromUri(sans))
                .orElseThrow(() -> new IllegalArgumentException("Could not find identity in SAN: " + sans));
    }

    private static Optional<AthenzIdentity> getRoleIdentityFromEmail(List<SubjectAlternativeName> sans) {
        return sans.stream()
                .filter(san -> san.getType() == Type.EMAIL)
                .map(com.yahoo.security.SubjectAlternativeName::getValue)
                .map(AthenzX509CertificateUtils::getIdentityFromSanEmail)
                .findFirst();
    }

    private static Optional<AthenzIdentity> getRoleIdentityFromUri(List<SubjectAlternativeName> sans) {
        String uriPrefix = "athenz://principal/";
        return sans.stream()
                .filter(s -> s.getType() == Type.URI && s.getValue().startsWith(uriPrefix))
                .map(san -> {
                    String uriPath = URI.create(san.getValue()).getPath();
                    return AthenzIdentities.from(uriPath.substring(uriPrefix.length()));
                })
                .findFirst();
    }

    public static AthenzRole getRolesFromRoleCertificate(X509Certificate certificate) {
        String commonName = X509CertificateUtils.getSubjectCommonName(certificate).orElseThrow();
        return AthenzRole.fromResourceNameString(commonName);
    }

    private static AthenzIdentity getIdentityFromSanEmail(String email) {
        int separator = email.indexOf('@');
        if (separator == -1) throw new IllegalArgumentException("Invalid SAN email: " + email);
        return AthenzIdentities.from(email.substring(0, separator));
    }

    /** @return Athenz unique instance id from an Athenz X.509 certificate (specified in the Subject Alternative Name extension) */
    public static Optional<String> getInstanceId(X509Certificate cert) {
        return getInstanceId(X509CertificateUtils.getSubjectAlternativeNames(cert));
    }

    /** @return Athenz unique instance id from the Subject Alternative Name extension */
    public static Optional<String> getInstanceId(List<SubjectAlternativeName> sans) {
        // Prefer instance id from SAN URI over the legacy DNS entry
        return getAthenzUniqueInstanceIdFromSanUri(sans)
                .or(() -> getAthenzUniqueInstanceIdFromSanDns(sans));
    }

    private static Optional<String> getAthenzUniqueInstanceIdFromSanUri(List<SubjectAlternativeName> sans) {
        String uriPrefix = "athenz://instanceid/";
        return sans.stream()
                .filter(san -> {
                    if (san.getType() != Type.URI) return false;
                    return san.getValue().startsWith(uriPrefix);
                })
                .map(san -> {
                    String uriPath = URI.create(san.getValue()).getPath();
                    return uriPath.substring(uriPath.lastIndexOf('/') + 1); // last path segment contains instance id
                })
                .findFirst();
    }

    private static Optional<String> getAthenzUniqueInstanceIdFromSanDns(List<SubjectAlternativeName> sans) {
        String dnsNameDelimiter = ".instanceid.athenz.";
        return sans.stream()
                .filter(san -> {
                    if (san.getType() != Type.DNS) return false;
                    return san.getValue().contains(dnsNameDelimiter);
                })
                .map(san -> {
                    String dnsName = san.getValue();
                    return dnsName.substring(0, dnsName.indexOf(dnsNameDelimiter));
                })
                .findFirst();
    }

}
