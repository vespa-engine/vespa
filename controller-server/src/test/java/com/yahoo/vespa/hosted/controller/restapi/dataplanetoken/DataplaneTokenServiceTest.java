// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.dataplanetoken;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.AuthMethod;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.DataplaneToken;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.DataplaneTokenVersions;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.FingerPrint;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.TokenId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.role.SimplePrincipal;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.restapi.dataplanetoken.DataplaneTokenService.State;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DataplaneTokenServiceTest {

    private final ControllerTester tester = new ControllerTester(SystemName.Public);
    private final DataplaneTokenService dataplaneTokenService = new DataplaneTokenService(tester.controller());
    private final TenantName tenantName = TenantName.from("tenant");
    private final Principal principal = new SimplePrincipal("user");
    private final TokenId tokenId = TokenId.of("myTokenId");
    private final Map<HostName, Map<TokenId, List<FingerPrint>>> activeTokens = tester.configServer().activeTokenFingerprints(null);

    @Test
    void triggers_token_redeployments() {
        DeploymentTester deploymentTester = new DeploymentTester(tester);
        DeploymentContext app = deploymentTester.newDeploymentContext(tenantName.value(), "app", "default");
        ApplicationPackage appPackage = new ApplicationPackageBuilder().region("aws-us-east-1c")
                                                                       .container("default", AuthMethod.token, AuthMethod.token)
                                                                       .build();
        app.submit(appPackage).deploy();

        // First token version is added after deployment, so re-trigger.
        dataplaneTokenService.triggerTokenChangeDeployments();
        assertEquals(List.of(), deploymentTester.jobs().active());
        FingerPrint print1 = dataplaneTokenService.generateToken(tenantName, TokenId.of("token-1"), null, principal).fingerPrint();
        dataplaneTokenService.triggerTokenChangeDeployments();
        app.runJob(JobType.prod("aws-us-east-1c"));
        assertEquals(List.of(), deploymentTester.jobs().active());

        // New token version is added, so re-trigger.
        tester.clock().advance(Duration.ofSeconds(1));
        dataplaneTokenService.triggerTokenChangeDeployments();
        assertEquals(List.of(), deploymentTester.jobs().active());
        FingerPrint print2 = dataplaneTokenService.generateToken(tenantName, TokenId.of("token-1"), null, principal).fingerPrint();
        dataplaneTokenService.triggerTokenChangeDeployments();
        app.runJob(JobType.prod("aws-us-east-1c"));
        assertEquals(List.of(), deploymentTester.jobs().active());

        // Another token version is added, so re-trigger.
        tester.clock().advance(Duration.ofSeconds(1));
        dataplaneTokenService.triggerTokenChangeDeployments();
        assertEquals(List.of(), deploymentTester.jobs().active());
        FingerPrint print3 = dataplaneTokenService.generateToken(tenantName, TokenId.of("token-1"), tester.clock().instant().plusSeconds(10), principal).fingerPrint();
        dataplaneTokenService.triggerTokenChangeDeployments();
        app.runJob(JobType.prod("aws-us-east-1c"));
        assertEquals(List.of(), deploymentTester.jobs().active());

        // An expired token version is deleted, so do _not_ re-trigger.
        tester.clock().advance(Duration.ofSeconds(11));
        dataplaneTokenService.triggerTokenChangeDeployments();
        assertEquals(List.of(), deploymentTester.jobs().active());
        dataplaneTokenService.deleteToken(tenantName, TokenId.of("token-1"), print3);
        dataplaneTokenService.triggerTokenChangeDeployments();
        assertEquals(List.of(), deploymentTester.jobs().active());

        // Some unused token version is added, so do _not_ re-trigger.
        tester.clock().advance(Duration.ofSeconds(1));
        dataplaneTokenService.triggerTokenChangeDeployments();
        assertEquals(List.of(), deploymentTester.jobs().active());
        dataplaneTokenService.generateToken(tenantName, TokenId.of("token-3"), null, principal);
        dataplaneTokenService.triggerTokenChangeDeployments();
        assertEquals(List.of(), deploymentTester.jobs().active());

        // One token version is deleted, so re-trigger.
        tester.clock().advance(Duration.ofSeconds(1));
        dataplaneTokenService.triggerTokenChangeDeployments();
        assertEquals(List.of(), deploymentTester.jobs().active());
        dataplaneTokenService.deleteToken(tenantName, TokenId.of("token-1"), print2);
        dataplaneTokenService.triggerTokenChangeDeployments();
        app.runJob(JobType.prod("aws-us-east-1c"));
        assertEquals(List.of(), deploymentTester.jobs().active());

        // Last token version is deleted, the token is no longer known, so re-trigger.
        tester.clock().advance(Duration.ofSeconds(1));
        dataplaneTokenService.triggerTokenChangeDeployments();
        assertEquals(List.of(), deploymentTester.jobs().active());
        dataplaneTokenService.deleteToken(tenantName, TokenId.of("token-1"), print1);
        dataplaneTokenService.triggerTokenChangeDeployments();
        app.runJob(JobType.prod("aws-us-east-1c"));
        assertEquals(List.of(), deploymentTester.jobs().active());
    }

    @Test
    void computes_aggregate_state() {
        DeploymentTester deploymentTester = new DeploymentTester(tester);
        DeploymentContext app = deploymentTester.newDeploymentContext(tenantName.value(), "app", "default");
        app.submit().deploy();

        TokenId[] id = new TokenId[5];
        FingerPrint[][] print = new FingerPrint[5][3];
        for (int i = 0; i < id.length; i++) {
            id[i] = TokenId.of("id" + i);
            for (int j = 0; j < 3; j++) {
                print[i][j] = dataplaneTokenService.generateToken(tenantName, id[i], null, principal).fingerPrint();
            }
        }
        for (int j = 0; j < 2; j++) {
            dataplaneTokenService.deleteToken(tenantName, id[2], print[2][j]);
            dataplaneTokenService.deleteToken(tenantName, id[4], print[4][j]);
        }
        for (int j = 0; j < 3; j++) {
            dataplaneTokenService.deleteToken(tenantName, id[3], print[3][j]);
        }
        // "host1" has all versions of all current tokens, except the first versions of tokens 1 and 2.
        activeTokens.put(HostName.of("host1"),
                         Map.of(id[0], List.of(print[0]),
                                id[1], List.of(print[1][1], print[1][2]),
                                id[2], List.of(print[2][1], print[2][2])));
        // "host2" has all versions of all current tokens, except the last version of token 1.
        activeTokens.put(HostName.of("host2"),
                         Map.of(id[0], List.of(print[0]),
                                id[1], List.of(print[1][0], print[1][1]),
                                id[2], List.of(print[2])));
        // "host3" has no current tokens at all, but has the last version of token 3
        activeTokens.put(HostName.of("host3"),
                         Map.of(id[3], List.of(print[3][2])));

        // All fingerprints of token 0 are active on all hosts where token 0 is found, so they are all active.
        // The first and last fingerprints of token 1 are missing from one host each, so these are activating.
        // The first fingerprints of token 2 are no longer current, but the second is found on a host; both deactivating.
        // The whole of token 3 is forgotten, but the last fingerprint is found on a host; deactivating.
        // Only the last fingerprint of token 4 remains, but this token is not used anywhere; unused.
        assertEquals(new TreeMap<>(Map.of(id[0], new TreeMap<>(Map.of(print[0][0], State.ACTIVE,
                                                                      print[0][1], State.ACTIVE,
                                                                      print[0][2], State.ACTIVE)),
                                          id[1], new TreeMap<>(Map.of(print[1][0], State.DEPLOYING,
                                                                      print[1][1], State.ACTIVE,
                                                                      print[1][2], State.DEPLOYING)),
                                          id[2], new TreeMap<>(Map.of(print[2][0], State.REVOKING,
                                                                      print[2][1], State.REVOKING,
                                                                      print[2][2], State.ACTIVE)),
                                          id[3], new TreeMap<>(Map.of(print[3][2], State.REVOKING)),
                                          id[4], new TreeMap<>(Map.of(print[4][2], State.UNUSED)))),
                     new TreeMap<>(dataplaneTokenService.listTokensWithState(tenantName).entrySet().stream()
                                                        .collect(toMap(tokens -> tokens.getKey().tokenId(),
                                                                       tokens -> new TreeMap<>(tokens.getValue())))));
    }

    @Test
    void generates_and_persists_token() {
        DataplaneToken dataplaneToken = dataplaneTokenService.generateToken(tenantName, tokenId, tester.clock().instant().plus(Duration.ofDays(100)), principal);
        List<DataplaneTokenVersions> dataplaneTokenVersions = dataplaneTokenService.listTokens(tenantName);
        assertEquals(dataplaneToken.fingerPrint(), dataplaneTokenVersions.get(0).tokenVersions().get(0).fingerPrint());
        assertEquals(dataplaneToken.expiration(), dataplaneTokenVersions.get(0).tokenVersions().get(0).expiration());
    }

    @Test
    void generating_new_token_appends() {
        DataplaneToken dataplaneToken1 = dataplaneTokenService.generateToken(tenantName, tokenId, tester.clock().instant().plus(Duration.ofDays(1)), principal);
        DataplaneToken dataplaneToken2 = dataplaneTokenService.generateToken(tenantName, tokenId, null, principal);
        assertNotEquals(dataplaneToken1.fingerPrint(), dataplaneToken2.fingerPrint());

        List<DataplaneTokenVersions> dataplaneTokenVersions = dataplaneTokenService.listTokens(tenantName);
        Set<FingerPrint> tokenFingerprints = dataplaneTokenVersions.stream()
                .filter(token -> token.tokenId().equals(tokenId))
                .map(DataplaneTokenVersions::tokenVersions)
                .flatMap(Collection::stream)
                .map(DataplaneTokenVersions.Version::fingerPrint)
                .collect(toSet());
        assertEquals(tokenFingerprints, Set.of(dataplaneToken1.fingerPrint(), dataplaneToken2.fingerPrint()));
    }

    @Test
    void delete_last_fingerprint_deletes_token() {
        DataplaneToken dataplaneToken1 = dataplaneTokenService.generateToken(tenantName, tokenId, null, principal);
        DataplaneToken dataplaneToken2 = dataplaneTokenService.generateToken(tenantName, tokenId, null, principal);
        dataplaneTokenService.deleteToken(tenantName, tokenId, dataplaneToken1.fingerPrint());
        dataplaneTokenService.deleteToken(tenantName, tokenId, dataplaneToken2.fingerPrint());
        assertEquals(List.of(), dataplaneTokenService.listTokens(tenantName));
    }

    @Test
    void deleting_nonexistent_fingerprint_throws() {
        DataplaneToken dataplaneToken = dataplaneTokenService.generateToken(tenantName, tokenId, null, principal);
        DataplaneToken dataplaneToken2 = dataplaneTokenService.generateToken(tenantName, tokenId, null, principal);
        dataplaneTokenService.deleteToken(tenantName, tokenId, dataplaneToken.fingerPrint());

        // Token currently contains value of "dataplaneToken2"
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> dataplaneTokenService.deleteToken(tenantName, tokenId, dataplaneToken.fingerPrint()));
        assertEquals("Fingerprint does not exist: " + dataplaneToken.fingerPrint(), exception.getMessage());
    }

    @Test
    void deleting_nonexistent_token_throws() {
        DataplaneToken dataplaneToken = dataplaneTokenService.generateToken(tenantName, tokenId, null, principal);
        dataplaneTokenService.deleteToken(tenantName, tokenId, dataplaneToken.fingerPrint());

        // Token is created and deleted above, no longer exists
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> dataplaneTokenService.deleteToken(tenantName, tokenId, dataplaneToken.fingerPrint()));
        assertEquals("Token does not exist: " + tokenId, exception.getMessage());
    }

}
