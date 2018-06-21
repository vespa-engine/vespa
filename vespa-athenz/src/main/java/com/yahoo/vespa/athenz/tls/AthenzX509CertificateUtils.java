// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;

import java.security.cert.X509Certificate;
import java.util.List;

import static com.yahoo.vespa.athenz.tls.SubjectAlternativeName.Type.RFC822_NAME;

/**
 * Utility methods for Athenz issued x509 certificates
 *
 * @author bjorncs
 */
public class AthenzX509CertificateUtils {

    private static final String COMMON_NAME_ROLE_DELIMITER = ":role.";

    private AthenzX509CertificateUtils() {}

    public static boolean isAthenzRoleCertificate(X509Certificate certificate) {
        return isAthenzIssuedCertificate(certificate) &&
                X509CertificateUtils.getSubjectCommonNames(certificate).get(0).contains(COMMON_NAME_ROLE_DELIMITER);
    }

    public static boolean isAthenzIssuedCertificate(X509Certificate certificate) {
        return X509CertificateUtils.getIssuerCommonNames(certificate).stream()
                .anyMatch(cn -> cn.equalsIgnoreCase("Yahoo Athenz CA") || cn.equalsIgnoreCase("Athenz AWS CA"));
    }

    public static AthenzIdentity getIdentityFromRoleCertificate(X509Certificate certificate) {
        List<SubjectAlternativeName> sans = X509CertificateUtils.getSubjectAlternativeNames(certificate);
        return sans.stream()
                .filter(san -> san.getType() == RFC822_NAME)
                .map(SubjectAlternativeName::getValue)
                .map(AthenzX509CertificateUtils::getIdentityFromSanEmail)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Could not find identity in SAN: " + sans));
    }

    public static AthenzRole getRolesFromRoleCertificate(X509Certificate certificate) {
        String commonName = X509CertificateUtils.getSubjectCommonNames(certificate).get(0);
        int delimiterIndex = commonName.indexOf(COMMON_NAME_ROLE_DELIMITER);
        String domain = commonName.substring(0, delimiterIndex);
        String roleName = commonName.substring(delimiterIndex + COMMON_NAME_ROLE_DELIMITER.length());
        return new AthenzRole(domain, roleName);
    }

    private static AthenzIdentity getIdentityFromSanEmail(String email) {
        int separator = email.indexOf('@');
        if (separator == -1) throw new IllegalArgumentException("Invalid SAN email: " + email);
        return AthenzIdentities.from(email.substring(0, separator));
    }

}
