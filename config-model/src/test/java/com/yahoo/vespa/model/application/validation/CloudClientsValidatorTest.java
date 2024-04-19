package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
class CloudClientsValidatorTest {

    @Test
    void logs_deployment_warning_on_certificate_with_empty_sequence_of_extensions() {
        var logger = new DeployLoggerStub();
        var state = new DeployState.Builder().deployLogger(logger).build();
        var cert = readTestCertificate("cert-with-empty-sequence-of-extensions.pem");
        CloudClientsValidator.validateCertificate("default", "my-feed-client", cert,
                                                  (msg, cause) -> { throw new IllegalArgumentException(msg, cause); },
                                                  state);
        var expected = "Client **my-feed-client** defined for cluster **default** contains an invalid certificate: " +
                "The certificate's ASN.1 structure contains an empty sequence of extensions, " +
                "which is a violation of the ASN.1 specification. " +
                "Please update the application package with a new certificate, " +
                "e.g by generating a new one using the Vespa CLI `$ vespa auth cert`. ";
        assertEquals(expected, logger.getLast().message);
    }

    @Test
    void accepts_valid_certificate() {
        var logger = new DeployLoggerStub();
        var state = new DeployState.Builder().deployLogger(logger).build();
        var cert = readTestCertificate("valid-cert.pem");
        assertDoesNotThrow(() -> CloudClientsValidator.validateCertificate("default", "my-feed-client", cert,
                                                                           (msg, cause) -> { throw new IllegalArgumentException(msg, cause); },
                                                                           state));
        assertEquals(0, logger.entries.size());
    }

    private static X509Certificate readTestCertificate(String filename) {
        return X509CertificateUtils.fromPem(new String(uncheck(
                () -> CloudClientsValidatorTest.class.getResourceAsStream(
                        "/cloud-clients-validator/%s".formatted(filename)).readAllBytes())));
    }
}
