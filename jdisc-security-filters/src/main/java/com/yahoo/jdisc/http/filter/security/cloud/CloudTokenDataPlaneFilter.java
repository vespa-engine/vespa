// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cloud;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudTokenDataPlaneFilterConfig;
import com.yahoo.security.token.Token;
import com.yahoo.security.token.TokenCheckHash;
import com.yahoo.security.token.TokenDomain;
import com.yahoo.security.token.TokenFingerprint;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.server.jetty.AccessLoggingRequestHandler.CONTEXT_KEY_ACCESS_LOG_ENTRY;

/**
 * Token data plane filter for Cloud
 *
 * @author bjorncs
 */
public class CloudTokenDataPlaneFilter extends JsonSecurityRequestFilterBase {

    private static final Logger log = Logger.getLogger(CloudTokenDataPlaneFilter.class.getName());
    static final int CHECK_HASH_BYTES = 32;

    private final List<Client> allowedClients;
    private final TokenDomain tokenDomain;
    private final Clock clock;

    @Inject
    public CloudTokenDataPlaneFilter(CloudTokenDataPlaneFilterConfig cfg) {
        this(cfg, Clock.systemUTC());
    }

    CloudTokenDataPlaneFilter(CloudTokenDataPlaneFilterConfig cfg, Clock clock) {
        this.tokenDomain = TokenDomain.of(cfg.tokenContext());
        this.clock = clock;
        this.allowedClients = parseClients(cfg);
    }

    private static List<Client> parseClients(CloudTokenDataPlaneFilterConfig cfg) {
        Set<String> ids = new HashSet<>();
        List<Client> clients = new ArrayList<>(cfg.clients().size());
        for (var c : cfg.clients()) {
            if (ids.contains(c.id()))
                throw new IllegalArgumentException("Clients definition has duplicate id '%s'".formatted(c.id()));
            if (c.tokens().isEmpty())
                throw new IllegalArgumentException("Client '%s' has no tokens configured".formatted(c.id()));
            ids.add(c.id());
            var tokens = new HashMap<TokenCheckHash, TokenVersion>();
            for (var token : c.tokens()) {
                for (int version = 0; version < token.checkAccessHashes().size(); version++) {
                    var tokenVersion = TokenVersion.of(
                            token.id(), token.fingerprints().get(version), token.checkAccessHashes().get(version),
                            token.expirations().get(version));
                    tokens.put(tokenVersion.accessHash(), tokenVersion);
                }
            }
            clients.add(new Client(c.id(), Permission.setOf(c.permissions()), tokens));
        }
        log.fine(() -> "Configured clients with ids %s".formatted(ids));
        return List.copyOf(clients);
    }

    @Override
    protected Optional<ErrorResponse> filter(DiscFilterRequest req) {
        var now = clock.instant();
        var bearerToken = requestBearerToken(req).orElse(null);
        if (bearerToken == null) {
            log.fine("Missing bearer token");
            return Optional.of(new ErrorResponse(Response.Status.UNAUTHORIZED, "Unauthorized"));
        }
        var permission = Permission.getRequiredPermission(req).orElse(null);
        if (permission == null) return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Forbidden"));
        var requestTokenHash = requestTokenHash(bearerToken);
        var clientIds = new TreeSet<String>();
        var permissions = EnumSet.noneOf(Permission.class);
        var matchedTokens = new HashSet<TokenVersion>();
        for (Client c : allowedClients) {
            if (!c.permissions().contains(permission)) continue;
            var matchedToken  = c.tokens().get(requestTokenHash);
            if (matchedToken == null) continue;
            var expiration = matchedToken.expiration().orElse(null);
            if (expiration != null && now.isAfter(expiration)) continue;
            matchedTokens.add(matchedToken);
            clientIds.add(c.id());
            permissions.addAll(c.permissions());
        }
        if (clientIds.isEmpty()) return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Forbidden"));
        if (matchedTokens.size() > 1) {
            log.warning("Multiple tokens matched for request %s"
                                .formatted(matchedTokens.stream().map(TokenVersion::id).toList()));
            return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Forbidden"));
        }
        var matchedToken = matchedTokens.stream().findAny().get();
        addAccessLogEntry(req, "token.id", matchedToken.id());
        addAccessLogEntry(req, "token.hash", matchedToken.fingerprint().toDelimitedHexString());
        addAccessLogEntry(req, "token.exp", matchedToken.expiration().map(Instant::toString).orElse("<none>"));
        ClientPrincipal.attachToRequest(req, clientIds, permissions);
        return Optional.empty();
    }

    private TokenCheckHash requestTokenHash(String bearerToken) {
        return TokenCheckHash.of(Token.of(tokenDomain, bearerToken), CHECK_HASH_BYTES);
    }

    private static Optional<String> requestBearerToken(DiscFilterRequest req) {
        return Optional.ofNullable(req.getHeader("Authorization"))
                .filter(h -> h.startsWith("Bearer "))
                .map(t -> t.substring("Bearer ".length()).trim())
                .filter(t -> !t.isBlank());

    }

    private static void addAccessLogEntry(DiscFilterRequest req, String key, String value) {
        ((AccessLogEntry) req.getAttribute(CONTEXT_KEY_ACCESS_LOG_ENTRY)).addKeyValue(key, value);
    }

    private record TokenVersion(String id, TokenFingerprint fingerprint, TokenCheckHash accessHash, Optional<Instant> expiration) {
        static TokenVersion of(String id, String fingerprint, String accessHash, String expiration) {
            return new TokenVersion(id, TokenFingerprint.ofHex(fingerprint), TokenCheckHash.ofHex(accessHash),
                                    expiration.equals("<none>") ? Optional.empty() : Optional.of(Instant.parse(expiration)));
        }
    }

    private record Client(String id, EnumSet<Permission> permissions, Map<TokenCheckHash, TokenVersion> tokens) {
        Client {
            permissions = EnumSet.copyOf(permissions); tokens = Map.copyOf(tokens);
        }
    }
}
