package com.yahoo.security.tls;

import com.yahoo.security.SubjectAlternativeName;
import com.yahoo.security.X509CertificateUtils;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.security.SubjectAlternativeName.Type.DNS;
import static com.yahoo.security.SubjectAlternativeName.Type.URI;

/**
 * @author bjorncs
 */
public record ConnectionAuthContext(List<X509Certificate> peerCertificateChain,
                                    CapabilitySet capabilities,
                                    Set<String> matchedPolicies) {

    public ConnectionAuthContext {
        if (peerCertificateChain.isEmpty()) throw new IllegalArgumentException("Peer certificate chain is empty");
        peerCertificateChain = List.copyOf(peerCertificateChain);
        matchedPolicies = Set.copyOf(matchedPolicies);
    }

    public boolean authorized() { return !capabilities.hasNone(); }

    public Optional<X509Certificate> peerCertificate() {
        return peerCertificateChain.isEmpty() ? Optional.empty() : Optional.of(peerCertificateChain.get(0));
    }

    public Optional<String> peerCertificateString() {
        X509Certificate cert = peerCertificate().orElse(null);
        if (cert == null) return Optional.empty();
        StringBuilder b = new StringBuilder("X.509Cert{");
        String cn = X509CertificateUtils.getSubjectCommonName(cert).orElse(null);
        if (cn != null) {
            b.append("CN='").append(cn).append("'");
        }
        var sans = X509CertificateUtils.getSubjectAlternativeNames(cert);
        List<String> dnsNames = sans.stream()
                .filter(s -> s.getType() == DNS)
                .map(SubjectAlternativeName::getValue)
                .toList();
        if (!dnsNames.isEmpty()) {
            if (cn != null) b.append(", ");
            b.append("SAN_DNS=").append(dnsNames);
        }
        List<String> uris = sans.stream()
                .filter(s -> s.getType() == URI)
                .map(SubjectAlternativeName::getValue)
                .toList();
        if (!uris.isEmpty()) {
            if (cn != null || !dnsNames.isEmpty()) b.append(", ");
            b.append("SAN_URI=").append(uris);
        }
        return Optional.of(b.append("}").toString());
    }

}
