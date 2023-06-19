// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cloud;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.AclMapping;
import com.yahoo.container.jdisc.RequestHandlerSpec;
import com.yahoo.container.jdisc.RequestView;
import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudDataPlaneFilterConfig;
import com.yahoo.jdisc.http.server.jetty.DataplaneProxyCredentials;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.token.Token;
import com.yahoo.security.token.TokenCheckHash;
import com.yahoo.security.token.TokenDomain;
import com.yahoo.security.token.TokenFingerprint;

import java.security.Principal;
import java.security.cert.X509Certificate;
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
import java.util.stream.Collectors;

import static com.yahoo.jdisc.http.filter.security.cloud.CloudDataPlaneFilter.Permission.READ;
import static com.yahoo.jdisc.http.filter.security.cloud.CloudDataPlaneFilter.Permission.WRITE;
import static com.yahoo.jdisc.http.server.jetty.AccessLoggingRequestHandler.CONTEXT_KEY_ACCESS_LOG_ENTRY;

/**
 * Data plane filter for Cloud
 * <p>
 * Legacy mode is the original mode of configuring mTLS where <code>&lt;clients&gt;</code> is not configured in services.xml
 * and trusted certificate authorities are listed in <code>security/clients.pem</code>.
 * </p>
 *
 * @author bjorncs
 */
public class CloudDataPlaneFilter extends JsonSecurityRequestFilterBase {

    private static final Logger log = Logger.getLogger(CloudDataPlaneFilter.class.getName());
    static final int CHECK_HASH_BYTES = 32;

    private final boolean legacyMode;
    private final List<Client> allowedClients;
    private final TokenDomain tokenDomain;

    @Inject
    public CloudDataPlaneFilter(CloudDataPlaneFilterConfig cfg,
                                ComponentRegistry<DataplaneProxyCredentials> optionalReverseProxy) {
        this(cfg, reverseProxyCert(optionalReverseProxy).orElse(null));
    }

    CloudDataPlaneFilter(CloudDataPlaneFilterConfig cfg, X509Certificate reverseProxyCert) {
        this.legacyMode = cfg.legacyMode();
        this.tokenDomain = TokenDomain.of(cfg.tokenContext());
        if (legacyMode) {
            allowedClients = List.of();
            log.fine(() -> "Legacy mode enabled");
        } else {
            allowedClients = parseClients(cfg, reverseProxyCert);
        }
    }

    private static Optional<X509Certificate> reverseProxyCert(
            ComponentRegistry<DataplaneProxyCredentials> optionalReverseProxy) {
        return optionalReverseProxy.allComponents().stream().findAny().map(DataplaneProxyCredentials::certificate);
    }

    private static List<Client> parseClients(CloudDataPlaneFilterConfig cfg, X509Certificate reverseProxyCert) {
        Set<String> ids = new HashSet<>();
        List<Client> clients = new ArrayList<>(cfg.clients().size());
        boolean hasClientRequiringCertificate = false;
        if (cfg.clients().isEmpty()) throw new IllegalArgumentException("Empty clients configuration");
        for (var c : cfg.clients()) {
            if (ids.contains(c.id()))
                throw new IllegalArgumentException("Clients definition has duplicate id '%s'".formatted(c.id()));
            if (!c.certificates().isEmpty() && !c.tokens().isEmpty())
                throw new IllegalArgumentException("Client '%s' has both certificate and token configured".formatted(c.id()));
            if (c.certificates().isEmpty() && c.tokens().isEmpty())
                throw new IllegalArgumentException("Client '%s' has neither certificate nor token configured".formatted(c.id()));
            if (!c.tokens().isEmpty() && reverseProxyCert == null)
                throw new IllegalArgumentException(
                        "Client '%s' has token configured but reverse proxy certificate is missing".formatted(c.id()));
            ids.add(c.id());
            EnumSet<Permission> permissions = c.permissions().stream().map(Permission::of)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
            if (!c.certificates().isEmpty()) {
                List<X509Certificate> certs;
                try {
                    certs = c.certificates().stream()
                            .flatMap(pem -> X509CertificateUtils.certificateListFromPem(pem).stream()).toList();
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Client '%s' contains invalid X.509 certificate PEM: %s".formatted(c.id(), e.toString()), e);
                }
                if (certs.isEmpty()) throw new IllegalArgumentException(
                        "Client '%s' certificate PEM contains no valid X.509 entries".formatted(c.id()));
                clients.add(new Client(c.id(), permissions, certs, Map.of()));
                hasClientRequiringCertificate = true;
            } else {
                var tokens = new HashMap<TokenCheckHash, TokenVersion>();
                for (var token : c.tokens()) {
                    for (int version = 0; version < token.checkAccessHashes().size(); version++) {
                        var tokenVersion = TokenVersion.of(
                                token.id(), token.fingerprints().get(version), token.checkAccessHashes().get(version));
                        tokens.put(tokenVersion.accessHash(), tokenVersion);
                    }
                }
                // Add reverse proxy certificate as required certificate for client definition
                clients.add(new Client(c.id(), permissions, List.of(reverseProxyCert), tokens));
            }
        }
        if (!hasClientRequiringCertificate)
            throw new IllegalArgumentException("At least one client must require a certificate");
        log.fine(() -> "Configured clients with ids %s".formatted(ids));
        return clients;
    }

    @Override
    protected Optional<ErrorResponse> filter(DiscFilterRequest req) {
        var certs = req.getClientCertificateChain();
        log.fine(() -> "Certificate chain contains %d elements".formatted(certs.size()));
        if (certs.isEmpty()) {
            log.fine("Missing client certificate");
            return Optional.of(new ErrorResponse(Response.Status.UNAUTHORIZED, "Unauthorized"));
        }
        if (legacyMode) {
            log.fine("Legacy mode validation complete");
            req.setUserPrincipal(new ClientPrincipal(Set.of(), Set.of(READ, WRITE)));
            return Optional.empty();
        }
        RequestView view = req.asRequestView();
        var permission = Optional.ofNullable((RequestHandlerSpec) req.getAttribute(RequestHandlerSpec.ATTRIBUTE_NAME))
                .or(() -> Optional.of(RequestHandlerSpec.DEFAULT_INSTANCE))
                .flatMap(spec -> {
                    var action = spec.aclMapping().get(view);
                    var maybePermission = Permission.of(action);
                    if (maybePermission.isEmpty()) log.fine(() -> "Unknown action '%s'".formatted(action));
                    return maybePermission;
                 }).orElse(null);
        if (permission == null) {
            log.fine(() -> "No valid permission mapping defined for %s @ '%s'".formatted(view.method(), view.uri()));
            return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Forbidden"));
        }
        var clientCert = certs.get(0);
        var requestTokenHash = requestTokenHash(req).orElse(null);
        var clientIds = new TreeSet<String>();
        var permissions = new TreeSet<Permission>();
        var matchedTokens = new HashSet<TokenVersion>();
        for (Client c : allowedClients) {
            if (!c.permissions().contains(permission)) continue;
            if (!c.certificates().contains(clientCert)) continue;
            if (!c.tokens().isEmpty()) {
                if (requestTokenHash == null) continue;
                var matchedToken  = c.tokens().get(requestTokenHash);
                if (matchedToken == null) continue;
                matchedTokens.add(matchedToken);
            }
            clientIds.add(c.id());
            permissions.addAll(c.permissions());
        }
        if (matchedTokens.size() > 1) {
            log.warning("Multiple tokens matched for request %s"
                                .formatted(matchedTokens.stream().map(TokenVersion::id).toList()));
            return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Forbidden"));
        }
        var matchedToken = matchedTokens.stream().findAny().orElse(null);
        if (matchedToken != null) {
            addAccessLogEntry(req, "token.id", matchedToken.id());
            addAccessLogEntry(req, "token.hash", matchedToken.fingerprint().toDelimitedHexString());
        }
        log.fine(() -> "Client with ids=%s, permissions=%s"
                .formatted(clientIds, permissions.stream().map(Permission::asString).toList()));
        if (clientIds.isEmpty()) return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Forbidden"));
        req.setUserPrincipal(new ClientPrincipal(clientIds, permissions));
        return Optional.empty();
    }

    private Optional<TokenCheckHash> requestTokenHash(DiscFilterRequest req) {
        return Optional.ofNullable(req.getHeader("Authorization"))
                .filter(h -> h.startsWith("Bearer "))
                .map(t -> t.substring("Bearer ".length()).trim())
                .map(t -> TokenCheckHash.of(Token.of(tokenDomain, t), CHECK_HASH_BYTES));
    }

    private static void addAccessLogEntry(DiscFilterRequest req, String key, String value) {
        ((AccessLogEntry) req.getAttribute(CONTEXT_KEY_ACCESS_LOG_ENTRY)).addKeyValue(key, value);
    }

    public record ClientPrincipal(Set<String> ids, Set<Permission> permissions) implements Principal {
        public ClientPrincipal { ids = Set.copyOf(ids); permissions = Set.copyOf(permissions); }
        @Override public String getName() {
            return "ids=%s,permissions=%s".formatted(ids, permissions.stream().map(Permission::asString).toList());
        }
    }

    enum Permission { READ, WRITE;
        String asString() {
            return switch (this) {
                case READ -> "read";
                case WRITE -> "write";
            };
        }
        static Permission of(String v) {
            return switch (v) {
                case "read" -> READ;
                case "write" -> WRITE;
                default -> throw new IllegalArgumentException("Invalid permission '%s'".formatted(v));
            };
        }
        static Optional<Permission> of(AclMapping.Action a) {
            if (a.equals(AclMapping.Action.READ)) return Optional.of(READ);
            if (a.equals(AclMapping.Action.WRITE)) return Optional.of(WRITE);
            return Optional.empty();
        }
    }

    private record TokenVersion(String id, TokenFingerprint fingerprint, TokenCheckHash accessHash) {
        static TokenVersion of(String id, String fingerprint, String accessHash) {
            return new TokenVersion(id, TokenFingerprint.ofHex(fingerprint), TokenCheckHash.ofHex(accessHash));
        }
    }

    private record Client(String id, EnumSet<Permission> permissions, List<X509Certificate> certificates,
                          Map<TokenCheckHash, TokenVersion> tokens) {
        Client {
            permissions = EnumSet.copyOf(permissions); certificates = List.copyOf(certificates); tokens = Map.copyOf(tokens);
        }
    }
}
