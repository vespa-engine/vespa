// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.dataplanetoken;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.DataplaneToken;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.DataplaneTokenVersions;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.FingerPrint;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.TokenId;
import com.yahoo.vespa.hosted.controller.api.role.SimplePrincipal;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DataplaneTokenServiceTest {
    private final ControllerTester tester = new ControllerTester(SystemName.Public);
    private final DataplaneTokenService dataplaneTokenService = new DataplaneTokenService(tester.controller());
    private final TenantName tenantName = TenantName.from("tenant");
    Principal principal = new SimplePrincipal("user");
    private final TokenId tokenId = TokenId.of("myTokenId");

    @Test
    void generates_and_persists_token() {
        DataplaneToken dataplaneToken = dataplaneTokenService.generateToken(tenantName, tokenId, principal);
        List<DataplaneTokenVersions> dataplaneTokenVersions = dataplaneTokenService.listTokens(tenantName);
        assertEquals(dataplaneToken.fingerPrint(), dataplaneTokenVersions.get(0).tokenVersions().get(0).fingerPrint());
    }

    @Test
    void generating_new_token_appends() {
        DataplaneToken dataplaneToken1 = dataplaneTokenService.generateToken(tenantName, tokenId, principal);
        DataplaneToken dataplaneToken2 = dataplaneTokenService.generateToken(tenantName, tokenId, principal);
        assertNotEquals(dataplaneToken1.fingerPrint(), dataplaneToken2.fingerPrint());

        List<DataplaneTokenVersions> dataplaneTokenVersions = dataplaneTokenService.listTokens(tenantName);
        List<FingerPrint> tokenFingerprints = dataplaneTokenVersions.stream()
                .filter(token -> token.tokenId().equals(tokenId))
                .map(DataplaneTokenVersions::tokenVersions)
                .flatMap(Collection::stream)
                .map(DataplaneTokenVersions.Version::fingerPrint)
                .toList();
        assertThat(tokenFingerprints).containsExactlyInAnyOrder(dataplaneToken1.fingerPrint(), dataplaneToken2.fingerPrint());
    }

    @Test
    void delete_last_fingerprint_deletes_token() {
        DataplaneToken dataplaneToken1 = dataplaneTokenService.generateToken(tenantName, tokenId, principal);
        DataplaneToken dataplaneToken2 = dataplaneTokenService.generateToken(tenantName, tokenId, principal);
        dataplaneTokenService.deleteToken(tenantName, tokenId, dataplaneToken1.fingerPrint());
        dataplaneTokenService.deleteToken(tenantName, tokenId, dataplaneToken2.fingerPrint());
        assertEquals(List.of(), dataplaneTokenService.listTokens(tenantName));
    }

    @Test
    void deleting_nonexistent_fingerprint_throws() {
        DataplaneToken dataplaneToken = dataplaneTokenService.generateToken(tenantName, tokenId, principal);
        DataplaneToken dataplaneToken2 = dataplaneTokenService.generateToken(tenantName, tokenId, principal);
        dataplaneTokenService.deleteToken(tenantName, tokenId, dataplaneToken.fingerPrint());

        // Token currently contains value of "dataplaneToken2"
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> dataplaneTokenService.deleteToken(tenantName, tokenId, dataplaneToken.fingerPrint()));
        assertEquals("Fingerprint does not exist: " + dataplaneToken.fingerPrint(), exception.getMessage());
    }

    @Test
    void deleting_nonexistent_token_throws() {
        DataplaneToken dataplaneToken = dataplaneTokenService.generateToken(tenantName, tokenId, principal);
        dataplaneTokenService.deleteToken(tenantName, tokenId, dataplaneToken.fingerPrint());

        // Token is created and deleted above, no longer exists
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> dataplaneTokenService.deleteToken(tenantName, tokenId, dataplaneToken.fingerPrint()));
        assertEquals("Token does not exist: " + tokenId, exception.getMessage());
    }
}
