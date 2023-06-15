// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.model.container.http.Client;
import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author mortent
 */
public class CertificateRemovalChangeValidatorTest {

    private static final String validationOverrides =
            "<validation-overrides>\n" +
            "    <allow until='2000-01-14' comment='test override'>certificate-removal</allow>\n" +
            "</validation-overrides>\n";

    @Test
    void validate() {
        Instant now = LocalDate.parse("2000-01-01", DateTimeFormatter.ISO_DATE).atStartOfDay().atZone(ZoneOffset.UTC).toInstant();

        Client c1 = new Client("c1", List.of(), List.of(certificate("cn=c1")), List.of());
        Client c2 = new Client("c2", List.of(), List.of(certificate("cn=c2")), List.of());
        Client c3 = new Client("c3", List.of(), List.of(certificate("cn=c3")), List.of());
        Client internal = Client.internalClient(List.of(certificate("cn=internal")));

        CertificateRemovalChangeValidator validator = new CertificateRemovalChangeValidator();

        // Adding certs -> ok
        validator.validateClients("clusterId", List.of(c1, c2), List.of(c1, c2, c3), new DeployState.Builder().now(now).build());

        // Removing certs -> fails
        assertThrows(ValidationOverrides.ValidationException.class,
                     () ->validator.validateClients("clusterId", List.of(c1, c2, c3), List.of(c1, c3),
                                                    new DeployState.Builder().now(now).build()));

        // Removing certs with validationoverrides -> ok
        validator.validateClients("clusterId", List.of(c1, c2, c3), List.of(c1, c3),
                                  new DeployState.Builder().now(now).validationOverrides(ValidationOverrides.fromXml(validationOverrides)).build());

        // Adding and removing internal certs are ok:
        validator.validateClients("clusterId", List.of(c1, c2), List.of(c1, c2, internal),
                                  new DeployState.Builder().build());
        validator.validateClients("clusterId", List.of(c1, c2, internal), List.of(c1, c2),
                                  new DeployState.Builder().now(now).build());
    }

    static X509Certificate certificate(String cn) {
        return X509CertificateUtils.createSelfSigned(cn, Duration.ofHours(1)).certificate();
    }

}
