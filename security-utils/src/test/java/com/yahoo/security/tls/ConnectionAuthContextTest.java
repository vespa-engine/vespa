// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateBuilder;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author bjorncs
 */
class ConnectionAuthContextTest {

    @Test
    void fails_on_missing_capabilities() {
        ConnectionAuthContext ctx = createConnectionAuthContext();
        assertThrows(MissingCapabilitiesException.class,
                () -> ctx.verifyCapabilities(CapabilitySet.from(Capability.CONTENT__STATUS_PAGES)));
    }

    @Test
    void creates_correct_error_message() {
        ConnectionAuthContext ctx = createConnectionAuthContext();
        CapabilitySet requiredCaps = CapabilitySet.from(Capability.CONTENT__STATUS_PAGES);
        String expectedMessage = """
                Permission denied for 'myaction' on 'myresource'. Peer 'mypeer' with [CN='myidentity'].
                Requires capabilities [vespa.content.status_pages] but peer has
                [vespa.content.document_api, vespa.content.search_api, vespa.slobrok.api].
                """;
        String actualMessage = ctx.createPermissionDeniedErrorMessage(requiredCaps, "myaction", "myresource", "mypeer");
        assertThat(actualMessage).isEqualToIgnoringWhitespace(expectedMessage);
    }

    private static ConnectionAuthContext createConnectionAuthContext() {
        return new ConnectionAuthContext(
                List.of(createCertificate()), CapabilitySet.Predefined.CONTAINER_NODE.capabilities(), Set.of(),
                CapabilityMode.ENFORCE);
    }

    private static X509Certificate createCertificate() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        return X509CertificateBuilder.fromKeypair(
                        keyPair, new X500Principal("CN=myidentity"), Instant.EPOCH,
                        Instant.EPOCH.plus(100000, ChronoUnit.DAYS), SHA256_WITH_ECDSA, BigInteger.ONE)
                .build();
    }


}
