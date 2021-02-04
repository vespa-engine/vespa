package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.security.SubjectAlternativeName;
import com.yahoo.security.X509CertificateUtils;

import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class EndpointCertificateValidatorImpl implements EndpointCertificateValidator {
    private final SecretStore secretStore;
    private final Clock clock;

    private static final Logger log = Logger.getLogger(EndpointCertificateValidator.class.getName());

    public EndpointCertificateValidatorImpl(SecretStore secretStore, Clock clock) {
        this.secretStore = secretStore;
        this.clock = clock;
    }

    @Override
    public void validate(EndpointCertificateMetadata endpointCertificateMetadata, String serializedInstanceId, ZoneId zone, List<String> requiredNamesForZone) {
        try {
            var pemEncodedEndpointCertificate = secretStore.getSecret(endpointCertificateMetadata.certName(), endpointCertificateMetadata.version());

            if (pemEncodedEndpointCertificate == null)
                throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Secret store returned null for certificate");

            List<X509Certificate> x509CertificateList = X509CertificateUtils.certificateListFromPem(pemEncodedEndpointCertificate);

            if (x509CertificateList.isEmpty())
                throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Empty certificate list");
            if (x509CertificateList.size() < 2)
                throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Only a single certificate found in chain - intermediate certificates likely missing");

            Instant now = clock.instant();
            Instant firstExpiry = Instant.MAX;
            for (X509Certificate x509Certificate : x509CertificateList) {
                Instant notBefore = x509Certificate.getNotBefore().toInstant();
                Instant notAfter = x509Certificate.getNotAfter().toInstant();
                if (now.isBefore(notBefore))
                    throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Certificate is not yet valid");
                if (now.isAfter(notAfter))
                    throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Certificate has expired");
                if (notAfter.isBefore(firstExpiry)) firstExpiry = notAfter;
            }

            X509Certificate endEntityCertificate = x509CertificateList.get(0);
            Set<String> subjectAlternativeNames = X509CertificateUtils.getSubjectAlternativeNames(endEntityCertificate).stream()
                    .filter(san -> san.getType().equals(SubjectAlternativeName.Type.DNS_NAME))
                    .map(SubjectAlternativeName::getValue).collect(Collectors.toSet());

            if (!subjectAlternativeNames.containsAll(requiredNamesForZone))
                throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Certificate is missing required SANs for zone " + zone.value());

        } catch (SecretNotFoundException s) {
            // Normally because the cert is in the process of being provisioned - this will cause a retry in InternalStepRunner
            throw new EndpointCertificateException(EndpointCertificateException.Type.CERT_NOT_AVAILABLE, "Certificate not found in secret store");
        } catch (EndpointCertificateException e) {
            log.log(Level.WARNING, "Certificate validation failure for " + serializedInstanceId, e);
            throw e;
        } catch (Exception e) {
            log.log(Level.WARNING, "Certificate validation failure for " + serializedInstanceId, e);
            throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Certificate validation failure for app " + serializedInstanceId, e);
        }
    }
}
