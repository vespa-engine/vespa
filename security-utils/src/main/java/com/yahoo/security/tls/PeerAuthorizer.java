// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.SubjectAlternativeName;
import com.yahoo.security.X509CertificateUtils;

import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static com.yahoo.security.SubjectAlternativeName.Type.DNS;
import static com.yahoo.security.SubjectAlternativeName.Type.IP;
import static com.yahoo.security.SubjectAlternativeName.Type.URI;

/**
 * Uses rules from {@link AuthorizedPeers} to evaluate X509 certificates
 *
 * @author bjorncs
 */
public class PeerAuthorizer {

    private static final Logger log = Logger.getLogger(PeerAuthorizer.class.getName());

    private final AuthorizedPeers authorizedPeers;

    public PeerAuthorizer(AuthorizedPeers authorizedPeers) {
        this.authorizedPeers = authorizedPeers;
    }


    public ConnectionAuthContext authorizePeer(X509Certificate cert) { return authorizePeer(List.of(cert)); }

    public ConnectionAuthContext authorizePeer(List<X509Certificate> certChain) {
        if (authorizedPeers.isEmpty()) return ConnectionAuthContext.defaultAllCapabilities(certChain);
        X509Certificate cert = certChain.get(0);
        Set<String> matchedPolicies = new HashSet<>();
        Set<CapabilitySet> grantedCapabilities = new HashSet<>();
        String cn = X509CertificateUtils.getSubjectCommonName(cert).orElse(null);
        List<String> sans = getSubjectAlternativeNames(cert);
        log.fine(() -> String.format("Subject info from x509 certificate: CN=[%s], 'SAN=%s", cn, sans));
        for (PeerPolicy peerPolicy : authorizedPeers.peerPolicies()) {
            if (matchesPolicy(peerPolicy, cn, sans)) {
                matchedPolicies.add(peerPolicy.policyName());
                grantedCapabilities.add(peerPolicy.capabilities());
            }
        }
        // TODO Pass this through constructor
        CapabilityMode capabilityMode = TransportSecurityUtils.getCapabilityMode();
        return new ConnectionAuthContext(
                certChain, CapabilitySet.ofSets(grantedCapabilities), matchedPolicies, capabilityMode);
    }

    private static boolean matchesPolicy(PeerPolicy peerPolicy, String cn, List<String> sans) {
        return peerPolicy.requiredCredentials().stream()
                .allMatch(requiredCredential -> matchesRequiredCredentials(requiredCredential, cn, sans));
    }

    private static boolean matchesRequiredCredentials(RequiredPeerCredential requiredCredential, String cn, List<String> sans) {
        switch (requiredCredential.field()) {
            case CN:
                return cn != null && requiredCredential.pattern().matches(cn);
            case SAN_DNS:
            case SAN_URI:
                return sans.stream()
                        .anyMatch(san -> requiredCredential.pattern().matches(san));
            default:
                throw new RuntimeException("Unknown field: " + requiredCredential.field());
        }
    }

    private static List<String> getSubjectAlternativeNames(X509Certificate peerCertificate) {
        return X509CertificateUtils.getSubjectAlternativeNames(peerCertificate).stream()
                .filter(san -> san.getType() == DNS || san.getType() == IP || san.getType() == URI)
                .map(SubjectAlternativeName::getValue)
                .toList();
    }
}
