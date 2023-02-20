package com.yahoo.security.tls;

import com.yahoo.security.SubjectAlternativeName;
import com.yahoo.security.X509CertificateUtils;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static com.yahoo.security.SubjectAlternativeName.Type.DNS;
import static com.yahoo.security.SubjectAlternativeName.Type.URI;
import static com.yahoo.security.tls.CapabilityMode.DISABLE;
import static com.yahoo.security.tls.CapabilityMode.LOG_ONLY;

/**
 * @author bjorncs
 */
public record ConnectionAuthContext(List<X509Certificate> peerCertificateChain,
                                    CapabilitySet capabilities,
                                    Set<String> matchedPolicies,
                                    CapabilityMode capabilityMode) {

    private static final Logger log = Logger.getLogger(ConnectionAuthContext.class.getName());

    public ConnectionAuthContext {
        peerCertificateChain = List.copyOf(peerCertificateChain);
        matchedPolicies = Set.copyOf(matchedPolicies);
    }

    private ConnectionAuthContext(List<X509Certificate> certs, CapabilityMode capabilityMode) {
        this(certs, CapabilitySet.all(), Set.of(), capabilityMode);
    }

    public boolean authorized() { return !capabilities.hasNone(); }

    /** Throws checked exception to force caller to handle verification failed. */
    public void verifyCapabilities(CapabilitySet requiredCapabilities) throws MissingCapabilitiesException {
        verifyCapabilities(requiredCapabilities, null, null, null);
    }

    /**
     * Throws checked exception to force caller to handle verification failed.
     * Provided strings are used for improved logging only
     * */
    public void verifyCapabilities(CapabilitySet requiredCapabilities, String action, String resource, String peer)
            throws MissingCapabilitiesException {
        if (capabilityMode == DISABLE) return;
        boolean hasCapabilities = capabilities.has(requiredCapabilities);
        if (!hasCapabilities) {
            TlsMetrics.instance().incrementCapabilitiesFailed();
            String msg = createPermissionDeniedErrorMessage(requiredCapabilities, action, resource, peer);
            if (capabilityMode == LOG_ONLY) {
                log.info(msg);
            } else {
                // Ideally log as warning, but we have no mechanism for de-duplicating repeated log spamming.
                log.fine(msg);
                throw new MissingCapabilitiesException(msg);
            }
        } else {
            TlsMetrics.instance().incrementCapabilitiesSucceeded();
        }
    }

    String createPermissionDeniedErrorMessage(
            CapabilitySet required, String action, String resource, String peer) {
        StringBuilder b = new StringBuilder();
        if (capabilityMode == LOG_ONLY) b.append("Dry-run: ");
        b.append("Permission denied");
        if (resource != null) {
            b.append(" for '");
            if (action != null) {
                b.append(action).append("' on '");
            }
            b.append(resource).append("'");
        }
        b.append(". Peer ");
        if (peer != null) b.append("'").append(peer).append("' ");
        return b.append("with ").append(peerCertificateString().orElse("<missing-certificate>")).append(". Requires capabilities ")
                .append(required.toNames()).append(" but peer has ").append(capabilities.toNames())
                .append(".").toString();
    }

    public Optional<X509Certificate> peerCertificate() {
        return peerCertificateChain.isEmpty() ? Optional.empty() : Optional.of(peerCertificateChain.get(0));
    }

    public Optional<String> peerCertificateString() {
        X509Certificate cert = peerCertificate().orElse(null);
        if (cert == null) return Optional.empty();
        StringBuilder b = new StringBuilder("[");
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
        return Optional.of(b.append("]").toString());
    }

    /** Construct instance with all capabilities */
    public static ConnectionAuthContext defaultAllCapabilities() { return new ConnectionAuthContext(List.of(), DISABLE); }

    /** Construct instance with all capabilities */
    public static ConnectionAuthContext defaultAllCapabilities(List<X509Certificate> certs) {
        return new ConnectionAuthContext(certs, DISABLE);
    }

}
