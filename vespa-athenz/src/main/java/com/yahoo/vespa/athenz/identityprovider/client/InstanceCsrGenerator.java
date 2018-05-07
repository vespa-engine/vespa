// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;
import com.yahoo.vespa.athenz.tls.Pkcs10CsrBuilder;
import com.yahoo.vespa.athenz.tls.SignatureAlgorithm;
import com.yahoo.vespa.athenz.tls.SubjectAlternativeName;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.util.Set;

import static com.yahoo.vespa.athenz.tls.SubjectAlternativeName.Type.DNS_NAME;
import static com.yahoo.vespa.athenz.tls.SubjectAlternativeName.Type.IP_ADDRESS;

/**
 * Generates a {@link Pkcs10Csr} for an instance.
 *
 * @author bjorncs
 */
public class InstanceCsrGenerator {

    private final String dnsSuffix;

    public InstanceCsrGenerator(String dnsSuffix) {
        this.dnsSuffix = dnsSuffix;
    }

    public Pkcs10Csr generateCsr(AthenzIdentity instanceIdentity,
                                 VespaUniqueInstanceId instanceId,
                                 Set<String> ipAddresses,
                                 KeyPair keyPair) {
        X500Principal subject = new X500Principal("CN=" + instanceIdentity.getFullName());
        // Add SAN dnsname <service>.<domain-with-dashes>.<provider-dnsname-suffix>
        // and SAN dnsname <provider-unique-instance-id>.instanceid.athenz.<provider-dnsname-suffix>
        Pkcs10CsrBuilder pkcs10CsrBuilder = Pkcs10CsrBuilder.fromKeypair(subject, keyPair, SignatureAlgorithm.SHA256_WITH_RSA)
                .addSubjectAlternativeName(
                        DNS_NAME,
                        String.format(
                                "%s.%s.%s",
                                instanceIdentity.getName(),
                                instanceIdentity.getDomainName().replace(".", "-"),
                                dnsSuffix))
                .addSubjectAlternativeName(DNS_NAME, String.format("%s.instanceid.athenz.%s", instanceId.asDottedString(), dnsSuffix));
        ipAddresses.forEach(ip ->  pkcs10CsrBuilder.addSubjectAlternativeName(new SubjectAlternativeName(IP_ADDRESS, ip)));
        return pkcs10CsrBuilder.build();
    }
}
