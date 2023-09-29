// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.dataplanetoken;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.security.token.Token;
import com.yahoo.security.token.TokenCheckHash;
import com.yahoo.security.token.TokenDomain;
import com.yahoo.security.token.TokenGenerator;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.DataplaneToken;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.DataplaneTokenVersions;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.DataplaneTokenVersions.Version;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.FingerPrint;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.TokenId;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

/**
 * Service to list, generate and delete data plane tokens
 *
 * @author mortent
 */
public class DataplaneTokenService {

    private static final String TOKEN_PREFIX = "vespa_cloud_";
    private static final int TOKEN_BYTES = 32;
    private static final int CHECK_HASH_BYTES = 32;
    public static final Duration DEFAULT_TTL = Duration.ofDays(30);

    private final ExecutorService executor = Executors.newCachedThreadPool(new DaemonThreadFactory("dataplane-token-service-"));
    private final Controller controller;

    public DataplaneTokenService(Controller controller) {
        this.controller = controller;
    }

    /**
     * List valid tokens for a tenant
     */
    public List<DataplaneTokenVersions> listTokens(TenantName tenantName) {
        return controller.curator().readDataplaneTokens(tenantName);
    }

    public enum State { UNUSED, DEPLOYING, ACTIVE, REVOKING }

    /** List all known tokens for a tenant, with the state of each token version (both current and deactivating). */
    public Map<DataplaneTokenVersions, Map<FingerPrint, State>> listTokensWithState(TenantName tenantName) {
        List<DataplaneTokenVersions> currentTokens = listTokens(tenantName);
        Set<TokenId> usedTokens = new HashSet<>();
        Map<HostName, Map<TokenId, List<FingerPrint>>> activeTokens = listActiveTokens(tenantName, usedTokens);
        Map<TokenId, Map<FingerPrint, Boolean>> activeFingerprints = computeStates(activeTokens);
        Map<DataplaneTokenVersions, Map<FingerPrint, State>> tokens = new TreeMap<>(comparing(DataplaneTokenVersions::tokenId));
        for (DataplaneTokenVersions token : currentTokens) {
            Map<FingerPrint, State> states = new TreeMap<>();
            // Current tokens are active iff. they are active everywhere.
            for (Version version : token.tokenVersions()) {
                // If the token was not seen anywhere, it is deploying or unused.
                // Otherwise, it is active iff. it is active everywhere.
                Boolean isActive = activeFingerprints.getOrDefault(token.tokenId(), Map.of()).get(version.fingerPrint());
                states.put(version.fingerPrint(),
                           isActive == null ? usedTokens.contains(token.tokenId()) ? State.DEPLOYING : State.UNUSED
                                            : isActive ? State.ACTIVE : State.DEPLOYING);
            }
            // Active, non-current token versions are deactivating.
            for (FingerPrint print : activeFingerprints.getOrDefault(token.tokenId(), Map.of()).keySet()) {
                states.putIfAbsent(print, State.REVOKING);
            }
            tokens.put(token, states);
        }
        // Active, non-current tokens are also deactivating.
        activeFingerprints.forEach((id, prints) -> {
            if (currentTokens.stream().noneMatch(token -> token.tokenId().equals(id))) {
                Map<FingerPrint, State> states = new TreeMap<>();
                for (FingerPrint print : prints.keySet()) states.put(print, State.REVOKING);
                tokens.put(new DataplaneTokenVersions(id, List.of()), states);
            }
        });
        return tokens;
    }

    private Map<HostName, Map<TokenId, List<FingerPrint>>> listActiveTokens(TenantName tenantName, Set<TokenId> usedTokens) {
        Map<HostName, Map<TokenId, List<FingerPrint>>> tokens = new ConcurrentHashMap<>();
        Phaser phaser = new Phaser(1);
        for (Application application : controller.applications().asList(tenantName)) {
            for (Instance instance : application.instances().values()) {
                instance.deployments().forEach((zone, deployment) -> {
                    DeploymentId id = new DeploymentId(instance.id(), zone);
                    usedTokens.addAll(deployment.dataPlaneTokens());
                    phaser.register();
                    executor.execute(() -> {
                        try { tokens.putAll(controller.serviceRegistry().configServer().activeTokenFingerprints(id)); }
                        finally { phaser.arrive(); }
                    });
                });
            }
        }
        phaser.arriveAndAwaitAdvance();
        return tokens;
    }

    /** Computes whether each print is active on all hosts where its token is present. */
    private Map<TokenId, Map<FingerPrint, Boolean>> computeStates(Map<HostName, Map<TokenId, List<FingerPrint>>> activeTokens) {
        Map<TokenId, Map<FingerPrint, Boolean>> states = new HashMap<>();
        for (Map<TokenId, List<FingerPrint>> token : activeTokens.values()) {
            token.forEach((id, prints) -> {
                states.merge(id,
                             prints.stream().collect(toMap(print -> print, __ -> true)),
                             (a, b) -> new HashMap<>() {{ // true iff. present in both, false iff. present in one.
                                 a.forEach((p, s) -> put(p, s && b.getOrDefault(p, false)));
                                 b.forEach((p, s) -> putIfAbsent(p, false));
                             }});
            });
        }
        return states;
    }

    /**
     * Generates a token using tenant name as the check access context.
     * Persists the token fingerprint and check access hash, but not the token value
     *
     * @param tenantName name of the tenant to connect the token to
     * @param tokenId The user generated name/id of the token
     * @param expiration Token expiration
     * @param principal The principal making the request
     * @return a DataplaneToken containing the secret generated token
     */
    public DataplaneToken generateToken(TenantName tenantName, TokenId tokenId, Instant expiration, Principal principal) {
        TokenDomain tokenDomain = TokenDomain.of("Vespa Cloud tenant data plane:%s".formatted(tenantName.value()));
        Token token = TokenGenerator.generateToken(tokenDomain, TOKEN_PREFIX, TOKEN_BYTES);
        TokenCheckHash checkHash = TokenCheckHash.of(token, CHECK_HASH_BYTES);
        DataplaneTokenVersions.Version newTokenVersion = new DataplaneTokenVersions.Version(
                FingerPrint.of(token.fingerprint().toDelimitedHexString()),
                checkHash.toHexString(),
                controller.clock().instant(),
                Optional.ofNullable(expiration),
                principal.getName());

        CuratorDb curator = controller.curator();
        try (Mutex lock = curator.lock(tenantName)) {
            List<DataplaneTokenVersions> dataplaneTokenVersions = curator.readDataplaneTokens(tenantName);
            Optional<DataplaneTokenVersions> existingToken = dataplaneTokenVersions.stream().filter(t -> Objects.equals(t.tokenId(), tokenId)).findFirst();
            if (existingToken.isPresent()) {
                List<DataplaneTokenVersions.Version> versions = existingToken.get().tokenVersions();
                versions = Stream.concat(
                                versions.stream(),
                                Stream.of(newTokenVersion))
                        .toList();
                dataplaneTokenVersions = Stream.concat(
                                dataplaneTokenVersions.stream().filter(t -> !Objects.equals(t.tokenId(), tokenId)),
                                Stream.of(new DataplaneTokenVersions(tokenId, versions)))
                        .toList();
            } else {
                DataplaneTokenVersions newToken = new DataplaneTokenVersions(tokenId, List.of(newTokenVersion));
                dataplaneTokenVersions = Stream.concat(dataplaneTokenVersions.stream(), Stream.of(newToken)).toList();
            }
            curator.writeDataplaneTokens(tenantName, dataplaneTokenVersions);

            // Return the data plane token including the secret token.
            return new DataplaneToken(tokenId, FingerPrint.of(token.fingerprint().toDelimitedHexString()),
                                      token.secretTokenString(), Optional.ofNullable(expiration));
        }
    }

    /**
     * Deletes the token version identitfied by tokenId and tokenFingerPrint
     * @throws IllegalArgumentException if the version could not be found
     */
    public void deleteToken(TenantName tenantName, TokenId tokenId, FingerPrint tokenFingerprint) {
        CuratorDb curator = controller.curator();
        try (Mutex lock = curator.lock(tenantName)) {
            List<DataplaneTokenVersions> dataplaneTokenVersions = curator.readDataplaneTokens(tenantName);
            Optional<DataplaneTokenVersions> existingToken = dataplaneTokenVersions.stream().filter(t -> Objects.equals(t.tokenId(), tokenId)).findFirst();
            if (existingToken.isPresent()) {
                List<DataplaneTokenVersions.Version> versions = existingToken.get().tokenVersions();
                versions = versions.stream().filter(v -> !Objects.equals(v.fingerPrint(), tokenFingerprint)).toList();
                if (versions.isEmpty()) {
                    dataplaneTokenVersions = dataplaneTokenVersions.stream().filter(t -> !Objects.equals(t.tokenId(), tokenId)).toList();
                } else {
                    boolean fingerPrintExists = existingToken.get().tokenVersions().stream().anyMatch(v -> v.fingerPrint().equals(tokenFingerprint));
                    if (fingerPrintExists) {
                        dataplaneTokenVersions = Stream.concat(dataplaneTokenVersions.stream().filter(t -> !Objects.equals(t.tokenId(), tokenId)), Stream.of(new DataplaneTokenVersions(tokenId, versions))).toList();
                    } else {
                        throw new IllegalArgumentException("Fingerprint does not exist: " + tokenFingerprint);
                    }
                }
                curator.writeDataplaneTokens(tenantName, dataplaneTokenVersions);
            } else {
                throw new IllegalArgumentException("Token does not exist: " + tokenId);
            }
        }
    }
}
