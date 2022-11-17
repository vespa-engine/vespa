// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cloud;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.AclMapping;
import com.yahoo.container.jdisc.RequestHandlerSpec;
import com.yahoo.container.jdisc.RequestView;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.security.X509CertificateUtils;

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.jdisc.http.filter.security.cloud.CloudDataPlaneFilter.Permission.READ;
import static com.yahoo.jdisc.http.filter.security.cloud.CloudDataPlaneFilter.Permission.WRITE;

/**
 * @author bjorncs
 */
public class CloudDataPlaneFilter extends JsonSecurityRequestFilterBase {

    private static final Logger log = Logger.getLogger(CloudDataPlaneFilter.class.getName());

    private final boolean legacyMode;
    private final List<Client> allowedClients;

    @Inject
    public CloudDataPlaneFilter(CloudDataPlaneFilterConfig cfg) {
        this.legacyMode = cfg.legacyMode();
        if (legacyMode) {
            allowedClients = List.of();
            log.fine(() -> "Legacy mode enabled");
        } else {
            allowedClients = parseClients(cfg);
        }
    }

    private static List<Client> parseClients(CloudDataPlaneFilterConfig cfg) {
        Set<String> ids = new HashSet<>();
        List<Client> clients = new ArrayList<>(cfg.clients().size());
        for (var c : cfg.clients()) {
            if (ids.contains(c.id()))
                throw new IllegalArgumentException("Clients definition has duplicate id '%s'".formatted(c.id()));
            ids.add(c.id());
            List<X509Certificate> certs;
            try {
                certs = c.certificates().stream().map(X509CertificateUtils::fromPem).toList();
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Client '%s' contains invalid X.509 certificate PEM: %s".formatted(c.id(), e.toString()), e);
            }
            EnumSet<Permission> permissions = c.permissions().stream().map(Permission::of)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
            clients.add(new Client(c.id(), permissions, certs));
        }
        if (clients.isEmpty()) throw new IllegalArgumentException("Empty clients configuration");
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
        var clientIds = new TreeSet<String>();
        var permissions = new TreeSet<Permission>();
        for (Client c : allowedClients) {
            if (c.permissions().contains(permission) && c.certificates().contains(clientCert)) {
                clientIds.add(c.id());
                permissions.addAll(c.permissions());
            }
        }
        log.fine(() -> "Client with ids=%s, permissions=%s"
                .formatted(clientIds, permissions.stream().map(Permission::asString).toList()));
        if (clientIds.isEmpty()) return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Forbidden"));
        req.setUserPrincipal(new ClientPrincipal(clientIds, permissions));
        return Optional.empty();
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

    private record Client(String id, EnumSet<Permission> permissions, List<X509Certificate> certificates) {
        Client { permissions = EnumSet.copyOf(permissions); certificates = List.copyOf(certificates); }
    }
}
