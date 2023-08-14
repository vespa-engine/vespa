// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cloud;

import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.security.base.JsonSecurityRequestFilterBase;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudDataPlaneFilterConfig;
import com.yahoo.security.X509CertificateUtils;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.filter.security.cloud.Permission.READ;
import static com.yahoo.jdisc.http.filter.security.cloud.Permission.WRITE;


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
        if (cfg.clients().isEmpty()) throw new IllegalArgumentException("Empty clients configuration");
        for (var c : cfg.clients()) {
            if (ids.contains(c.id()))
                throw new IllegalArgumentException("Clients definition has duplicate id '%s'".formatted(c.id()));
            if (c.certificates().isEmpty())
                throw new IllegalArgumentException("Client '%s' has no certificate configured".formatted(c.id()));
            ids.add(c.id());
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
            clients.add(new Client(c.id(), Permission.setOf(c.permissions()), certs));
        }
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
            ClientPrincipal.attachToRequest(req, Set.of(), Set.of(READ, WRITE));
            return Optional.empty();
        }
        var permission = Permission.getRequiredPermission(req).orElse(null);
        if (permission == null) return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Forbidden"));
        var clientCert = certs.get(0);
        var clientIds = new TreeSet<String>();
        var permissions = new TreeSet<Permission>();
        for (Client c : allowedClients) {
            if (!c.permissions().contains(permission)) continue;
            if (!c.certificates().contains(clientCert)) continue;
            clientIds.add(c.id());
            permissions.addAll(c.permissions());
        }
        if (clientIds.isEmpty()) return Optional.of(new ErrorResponse(Response.Status.FORBIDDEN, "Forbidden"));
        ClientPrincipal.attachToRequest(req, clientIds, permissions);
        return Optional.empty();
    }

    private record Client(String id, EnumSet<Permission> permissions, List<X509Certificate> certificates) {
        Client {
            permissions = EnumSet.copyOf(permissions); certificates = List.copyOf(certificates);
        }
    }
}
