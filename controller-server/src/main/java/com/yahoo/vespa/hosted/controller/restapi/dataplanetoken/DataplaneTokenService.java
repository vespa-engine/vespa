// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.dataplanetoken;

import com.yahoo.config.provision.TenantName;
import com.yahoo.security.token.Token;
import com.yahoo.security.token.TokenCheckHash;
import com.yahoo.security.token.TokenDomain;
import com.yahoo.security.token.TokenGenerator;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.DataplaneToken;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.DataplaneTokenVersions;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.FingerPrint;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.TokenId;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Service to list, generate and delete data plane tokens
 *
 * @author mortent
 */
public class DataplaneTokenService {

    private static final String TOKEN_PREFIX = "vespa_cloud_";
    private static final int TOKEN_BYTES = 32;
    private static final int CHECK_HASH_BYTES = 32;


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

    /**
     * Generates a token using tenant name as the check access context.
     * Persists the token fingerprint and check access hash, but not the token value
     *
     * @param tenantName name of the tenant to connect the token to
     * @param tokenId The user generated name/id of the token
     * @param principal The principal making the request
     * @return a DataplaneToken containing the secret generated token
     */
    public DataplaneToken generateToken(TenantName tenantName, TokenId tokenId, Principal principal) {
        TokenDomain tokenDomain = TokenDomain.of("Vespa Cloud tenant data plane:%s".formatted(tenantName.value()));
        Token token = TokenGenerator.generateToken(tokenDomain, TOKEN_PREFIX, TOKEN_BYTES);
        TokenCheckHash checkHash = TokenCheckHash.of(token, CHECK_HASH_BYTES);
        DataplaneTokenVersions.Version newTokenVersion = new DataplaneTokenVersions.Version(
                FingerPrint.of(token.fingerprint().toDelimitedHexString()),
                checkHash.toHexString(),
                controller.clock().instant(),
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
            return new DataplaneToken(tokenId, FingerPrint.of(token.fingerprint().toDelimitedHexString()), token.secretTokenString());
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
