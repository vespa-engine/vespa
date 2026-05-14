// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.DataplaneToken;
import com.yahoo.vespa.model.container.http.Client;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author hmusum
 */
public class DataplaneTokenRemovalValidatorTest {

    private static final String validationOverrides =
            """
            <validation-overrides>
                <allow until='2000-01-14' comment='test override'>data-plane-token-removal</allow>
            </validation-overrides>
            """;

    @Test
    void validate() {
        Instant now = LocalDate.parse("2000-01-01", DateTimeFormatter.ISO_DATE).atStartOfDay().atZone(ZoneOffset.UTC).toInstant();

        Client c1 = new Client("c1", List.of(), List.of(), List.of(token("token1")));
        Client c2 = new Client("c2", List.of(), List.of(), List.of(token("token2")));

        var validator = new DataplaneTokenRemovalValidator();

        // Adding tokens -> ok
        validator.validateClients("clusterId", List.of(c1), List.of(c1, c2), (id, msg) -> ValidationOverrides.empty.invalid(id, msg, now));

        // Removing tokens -> fails
        assertThrows(ValidationOverrides.ValidationException.class,
                     () ->validator.validateClients("clusterId", List.of(c1, c2), List.of(c1),
                                                    (id, msg) -> ValidationOverrides.empty.invalid(id, msg, now)));

        // Removing tokens with validationoverrides -> ok
        validator.validateClients("clusterId", List.of(c1, c2), List.of(c1),
                                  (id, msg) -> ValidationOverrides.fromXml(validationOverrides).invalid(id, msg, now));
    }

    static DataplaneToken token(String tokenId) {
        return new DataplaneToken(tokenId, List.of(
                new DataplaneToken.Version("fingerprint",
                                           "checkAccessHash",
                                           Optional.of(Instant.now().plus(Duration.ofDays(1))))));
    }

}
