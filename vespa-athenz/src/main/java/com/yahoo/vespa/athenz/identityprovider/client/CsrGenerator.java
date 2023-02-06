// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.Pkcs10CsrBuilder;
import com.yahoo.security.SubjectAlternativeName;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.identityprovider.api.ClusterType;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.util.Set;

import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_RSA;
import static com.yahoo.security.SubjectAlternativeName.Type.DNS;
import static com.yahoo.security.SubjectAlternativeName.Type.EMAIL;
import static com.yahoo.security.SubjectAlternativeName.Type.IP;
import static com.yahoo.security.SubjectAlternativeName.Type.URI;

/**
 * Generates a {@link Pkcs10Csr} for an instance.
 *
 * @author bjorncs
 */
public class CsrGenerator {

    private final String dnsSuffix;
    private final String providerService;

    public CsrGenerator(String dnsSuffix, String providerService) {
        this.dnsSuffix = dnsSuffix;
        this.providerService = providerService;
    }

    public Pkcs10Csr generateInstanceCsr(AthenzIdentity instanceIdentity,
                                         VespaUniqueInstanceId instanceId,
                                         Set<String> ipAddresses,
                                         ClusterType clusterType,
                                         KeyPair keyPair) {
        X500Principal subject = new X500Principal(String.format("OU=%s, CN=%s", providerService, instanceIdentity.getFullName()));
        // Add SAN dnsname <service>.<domain-with-dashes>.<provider-dnsname-suffix>
        // and SAN dnsname <provider-unique-instance-id>.instanceid.athenz.<provider-dnsname-suffix>
        Pkcs10CsrBuilder pkcs10CsrBuilder = Pkcs10CsrBuilder.fromKeypair(subject, keyPair, SHA256_WITH_RSA)
                .addSubjectAlternativeName(
                        DNS,
                        String.format(
                                "%s.%s.%s",
                                instanceIdentity.getName(),
                                instanceIdentity.getDomainName().replace(".", "-"),
                                dnsSuffix))
                .addSubjectAlternativeName(DNS, getIdentitySAN(instanceId));
        if (clusterType != null) pkcs10CsrBuilder.addSubjectAlternativeName(URI, clusterType.asCertificateSanUri().toString());
        ipAddresses.forEach(ip ->  pkcs10CsrBuilder.addSubjectAlternativeName(new SubjectAlternativeName(IP, ip)));
        return pkcs10CsrBuilder.build();
    }

    public Pkcs10Csr generateRoleCsr(AthenzIdentity identity,
                                     AthenzRole role,
                                     VespaUniqueInstanceId instanceId,
                                     ClusterType clusterType,
                                     KeyPair keyPair) {
        X500Principal principal = new X500Principal(String.format("OU=%s, cn=%s:role.%s", providerService, role.domain().getName(), role.roleName()));
        var b = Pkcs10CsrBuilder.fromKeypair(principal, keyPair, SHA256_WITH_RSA)
                .addSubjectAlternativeName(DNS, getIdentitySAN(instanceId))
                .addSubjectAlternativeName(EMAIL, String.format("%s.%s@%s", identity.getDomainName(), identity.getName(), dnsSuffix));
        if (clusterType != null) b.addSubjectAlternativeName(URI, clusterType.asCertificateSanUri().toString());
        return b.build();
    }

    private String getIdentitySAN(VespaUniqueInstanceId instanceId) {
        return String.format("%s.instanceid.athenz.%s", instanceId.asDottedString(), dnsSuffix);
    }
}
