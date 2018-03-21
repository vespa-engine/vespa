// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.utils;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.AthenzUser;

import javax.naming.NamingException;
import javax.naming.ldap.LdapName;
import java.security.cert.X509Certificate;

/**
 * @author bjorncs
 */
public class AthenzIdentities {

    private AthenzIdentities() {}

    public static final AthenzDomain USER_PRINCIPAL_DOMAIN = new AthenzDomain("user");
    public static final AthenzService ZMS_ATHENZ_SERVICE = new AthenzService("sys.auth", "zms");

    public static AthenzIdentity from(AthenzDomain domain, String identityName) {
        if (domain.equals(USER_PRINCIPAL_DOMAIN)) {
            return AthenzUser.fromUserId(identityName);
        } else {
            return new AthenzService(domain, identityName);
        }
    }

    public static AthenzIdentity from(String fullName) {
        int domainIdentityNameSeparatorIndex = fullName.lastIndexOf('.');
        if (domainIdentityNameSeparatorIndex == -1
                || domainIdentityNameSeparatorIndex == 0
                || domainIdentityNameSeparatorIndex == fullName.length() - 1) {
            throw new IllegalArgumentException("Invalid Athenz identity: " + fullName);
        }
        AthenzDomain domain = new AthenzDomain(fullName.substring(0, domainIdentityNameSeparatorIndex));
        String identityName = fullName.substring(domainIdentityNameSeparatorIndex + 1, fullName.length());
        return from(domain, identityName);
    }

    public static AthenzIdentity from(X509Certificate certificate) {
        String commonName = getCommonName(certificate);
        if (isAthenzRoleIdentity(commonName)) {
            throw new IllegalArgumentException("Athenz role certificate not supported");
        }
        return from(commonName);
    }

    private static boolean isAthenzRoleIdentity(String commonName) {
        return commonName.contains(":role.");
    }

    private static String getCommonName(X509Certificate certificate) {
        try {
            String subjectPrincipal = certificate.getSubjectX500Principal().getName();
            return new LdapName(subjectPrincipal).getRdns().stream()
                    .filter(rdn -> rdn.getType().equalsIgnoreCase("cn"))
                    .map(rdn -> rdn.getValue().toString())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Could not find CN in certificate: " + subjectPrincipal));
        } catch (NamingException e) {
            throw new IllegalArgumentException("Invalid CN: " + e, e);
        }
    }
}
