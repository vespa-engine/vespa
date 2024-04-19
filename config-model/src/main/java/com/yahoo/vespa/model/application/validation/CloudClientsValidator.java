package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import org.bouncycastle.asn1.x509.TBSCertificate;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.function.BiConsumer;
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
                client.certificates().forEach(cert -> validateCertificate(clusterName, client.id(), cert, ctx::illegal, ctx.deployState()));
            }
        });
    }

    static void validateCertificate(String clusterName, String clientId, X509Certificate cert, BiConsumer<String, Throwable> reporter, DeployState state) {
        try {
            var extensions = TBSCertificate.getInstance(cert.getTBSCertificate()).getExtensions();
            if (extensions == null) return; // Certificate without any extensions is okay
            if (extensions.getExtensionOIDs().length == 0) {
                /*
                    BouncyCastle 1.77 and 1.78 did not accept certificates having an empty sequence of extensions.
                    Earlier releases violated the ASN.1 specification as the specification forbids empty extension sequence.
                    See https://github.com/bcgit/bc-java/issues/1479.
                    The restriction was lifted on 1.78.1 although it's a reasonble to warn users still.
                 */
                var message = "The certificate's ASN.1 structure contains an empty sequence of extensions, " +
                        "which is a violation of the ASN.1 specification. " +
                        "Please update the application package with a new certificate, " +
                        "e.g by generating a new one using the Vespa CLI `$ vespa auth cert`. ";
                state.getDeployLogger()
                        .log(Level.INFO, errorMessage(clusterName, clientId, message));
            }
        } catch (CertificateEncodingException e) {
            reporter.accept(errorMessage(clusterName, clientId, e.getMessage()), e);
        }
    }

    private static String errorMessage(String clusterName, String clientId, String message) {
        return "Client **%s** defined for cluster **%s** contains an invalid certificate: %s"
                .formatted(clientId, clusterName, message);
    }
}
