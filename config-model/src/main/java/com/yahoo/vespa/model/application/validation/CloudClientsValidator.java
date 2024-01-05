package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.model.application.validation.Validation.Context;
import org.bouncycastle.asn1.x509.TBSCertificate;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;

/**
 * Validates that trusted data plane certificates are valid
 *
 * @author bjorncs
 */
public class CloudClientsValidator implements Validator {

    @Override
    public void validate(Validation.Context ctx) {
        if (!ctx.deployState().isHosted()) return;
        ctx.model().getContainerClusters().forEach((clusterName, cluster) -> {
            for (var client : cluster.getClients()) {
                client.certificates().forEach(cert -> validateCertificate(clusterName, client.id(), cert, ctx));
            }
        });
    }

    static void validateCertificate(String clusterName, String clientId, X509Certificate cert, Context ctx) {
        try {
            var extensions = TBSCertificate.getInstance(cert.getTBSCertificate()).getExtensions();
            if (extensions == null) return; // Certificate without any extensions is okay
            if (extensions.getExtensionOIDs().length == 0) {
                /*
                    BouncyCastle 1.77 no longer accepts certificates having an empty sequence of extensions.
                    Earlier releases violated the ASN.1 specification as the specification forbids empty extension sequence.
                    See https://github.com/bcgit/bc-java/issues/1479.

                    Detect such certificates and issue a warning for now.
                    Validation will be implicitly enforced once we upgrade BouncyCastle past 1.76.
                 */
                var message = "The certificate's ASN.1 structure contains an empty sequence of extensions, " +
                        "which is a violation of the ASN.1 specification. " +
                        "Please update the application package with a new certificate, " +
                        "e.g by generating a new one using the Vespa CLI `$ vespa auth cert`. " +
                        "Such certificate will no longer be accepted in near future.";
                ctx.deployState().getDeployLogger().log(Level.WARNING, errorMessage(clusterName, clientId, message));
            }
        } catch (CertificateEncodingException e) {
            ctx.illegal(errorMessage(clusterName, clientId, e.getMessage()), e);
        }
    }

    private static String errorMessage(String clusterName, String clientId, String message) {
        return "Client **%s** defined for cluster **%s** contains an invalid certificate: %s"
                .formatted(clientId, clusterName, message);
    }
}
