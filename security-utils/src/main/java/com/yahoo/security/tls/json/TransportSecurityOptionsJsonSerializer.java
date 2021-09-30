// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.security.tls.TransportSecurityOptions;
import com.yahoo.security.tls.json.TransportSecurityOptionsEntity.AuthorizedPeer;
import com.yahoo.security.tls.json.TransportSecurityOptionsEntity.CredentialField;
import com.yahoo.security.tls.json.TransportSecurityOptionsEntity.Files;
import com.yahoo.security.tls.json.TransportSecurityOptionsEntity.RequiredCredential;
import com.yahoo.security.tls.policy.AuthorizedPeers;
import com.yahoo.security.tls.policy.PeerPolicy;
import com.yahoo.security.tls.policy.RequiredPeerCredential;
import com.yahoo.security.tls.policy.Role;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * @author bjorncs
 */
public class TransportSecurityOptionsJsonSerializer {

    private static final ObjectMapper mapper = new ObjectMapper();

    public TransportSecurityOptions deserialize(InputStream in) {
        try {
            TransportSecurityOptionsEntity entity = mapper.readValue(in, TransportSecurityOptionsEntity.class);
            return toTransportSecurityOptions(entity);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void serialize(OutputStream out, TransportSecurityOptions options) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(out, toTransportSecurityOptionsEntity(options));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static TransportSecurityOptions toTransportSecurityOptions(TransportSecurityOptionsEntity entity) {
        TransportSecurityOptions.Builder builder = new TransportSecurityOptions.Builder();
        Files files = entity.files;
        if (files != null) {
            if (files.certificatesFile != null && files.privateKeyFile != null) {
                builder.withCertificates(Paths.get(files.certificatesFile), Paths.get(files.privateKeyFile));
            } else if (files.certificatesFile != null || files.privateKeyFile != null) {
                throw new IllegalArgumentException("Both 'private-key' and 'certificates' must be configured together");
            }
            if (files.caCertificatesFile != null) {
                builder.withCaCertificates(Paths.get(files.caCertificatesFile));
            }
        }
        List<AuthorizedPeer> authorizedPeersEntity = entity.authorizedPeers;
        if (authorizedPeersEntity != null) {
            if (authorizedPeersEntity.size() == 0) {
                throw new IllegalArgumentException("'authorized-peers' cannot be empty");
            }
            builder.withAuthorizedPeers(new AuthorizedPeers(toPeerPolicies(authorizedPeersEntity)));
        }
        if (entity.acceptedCiphers != null) {
            if (entity.acceptedCiphers.isEmpty()) {
                throw new IllegalArgumentException("'accepted-ciphers' cannot be empty");
            }
            builder.withAcceptedCiphers(entity.acceptedCiphers);
        }
        if (entity.acceptedProtocols != null) {
            if (entity.acceptedProtocols.isEmpty()) {
                throw new IllegalArgumentException("'accepted-protocols' cannot be empty");
            }
            builder.withAcceptedProtocols(entity.acceptedProtocols);
        }
        if (entity.isHostnameValidationDisabled != null) {
            builder.withHostnameValidationDisabled(entity.isHostnameValidationDisabled);
        }
        return builder.build();
    }

    private static Set<PeerPolicy> toPeerPolicies(List<AuthorizedPeer> authorizedPeersEntity) {
        return authorizedPeersEntity.stream()
                .map(TransportSecurityOptionsJsonSerializer::toPeerPolicy)
                .collect(toSet());
    }

    private static PeerPolicy toPeerPolicy(AuthorizedPeer authorizedPeer) {
        if (authorizedPeer.name == null) {
            throw missingFieldException("name");
        }
        if (authorizedPeer.requiredCredentials == null) {
            throw missingFieldException("required-credentials");
        }
        return new PeerPolicy(authorizedPeer.name, authorizedPeer.description, toRoles(authorizedPeer.roles), toRequestPeerCredentials(authorizedPeer.requiredCredentials));
    }

    private static Set<Role> toRoles(List<String> roles) {
        if (roles == null) return Collections.emptySet();
        return roles.stream()
                .map(Role::new)
                .collect(toSet());
    }

    private static List<RequiredPeerCredential> toRequestPeerCredentials(List<RequiredCredential> requiredCredentials) {
        return requiredCredentials.stream()
                .map(TransportSecurityOptionsJsonSerializer::toRequiredPeerCredential)
                .collect(toList());
    }

    private static RequiredPeerCredential toRequiredPeerCredential(RequiredCredential requiredCredential) {
        if (requiredCredential.field == null) {
            throw missingFieldException("field");
        }
        if (requiredCredential.matchExpression == null) {
            throw missingFieldException("must-match");
        }
        return RequiredPeerCredential.of(toField(requiredCredential.field), requiredCredential.matchExpression);
    }

    private static RequiredPeerCredential.Field toField(CredentialField field) {
        switch (field) {
            case CN: return RequiredPeerCredential.Field.CN;
            case SAN_DNS: return RequiredPeerCredential.Field.SAN_DNS;
            case SAN_URI: return RequiredPeerCredential.Field.SAN_URI;
            default: throw new IllegalArgumentException("Invalid field type: " + field);
        }
    }

    private static TransportSecurityOptionsEntity toTransportSecurityOptionsEntity(TransportSecurityOptions options) {
        TransportSecurityOptionsEntity entity = new TransportSecurityOptionsEntity();
        entity.files = new Files();
        options.getCaCertificatesFile().ifPresent(value -> entity.files.caCertificatesFile = value.toString());
        options.getCertificatesFile().ifPresent(value -> entity.files.certificatesFile = value.toString());
        options.getPrivateKeyFile().ifPresent(value -> entity.files.privateKeyFile = value.toString());
        options.getAuthorizedPeers().ifPresent( authorizedPeers -> entity.authorizedPeers =
                authorizedPeers.peerPolicies().stream()
                        // Makes tests stable
                        .sorted(Comparator.comparing(PeerPolicy::policyName))
                        .map(peerPolicy -> {
                            AuthorizedPeer authorizedPeer = new AuthorizedPeer();
                            authorizedPeer.name = peerPolicy.policyName();
                            authorizedPeer.requiredCredentials = new ArrayList<>();
                            authorizedPeer.description = peerPolicy.description().orElse(null);
                            for (RequiredPeerCredential requiredPeerCredential : peerPolicy.requiredCredentials()) {
                                RequiredCredential requiredCredential = new RequiredCredential();
                                requiredCredential.field = toField(requiredPeerCredential.field());
                                requiredCredential.matchExpression = requiredPeerCredential.pattern().asString();
                                authorizedPeer.requiredCredentials.add(requiredCredential);
                            }
                            if (!peerPolicy.assumedRoles().isEmpty()) {
                                authorizedPeer.roles = new ArrayList<>();
                                peerPolicy.assumedRoles().forEach(role -> authorizedPeer.roles.add(role.name()));
                            }

                            return authorizedPeer;
                        })
                        .collect(toList()));
        if (!options.getAcceptedCiphers().isEmpty()) {
            entity.acceptedCiphers = options.getAcceptedCiphers();
        }
        if (!options.getAcceptedProtocols().isEmpty()) {
            entity.acceptedProtocols = options.getAcceptedProtocols();
        }
        if (options.isHostnameValidationDisabled()) {
            entity.isHostnameValidationDisabled = true;
        }
        return entity;
    }

    private static CredentialField toField(RequiredPeerCredential.Field field) {
        switch (field) {
            case CN: return CredentialField.CN;
            case SAN_DNS: return CredentialField.SAN_DNS;
            case SAN_URI: return CredentialField.SAN_URI;
            default: throw new IllegalArgumentException("Invalid field type: " + field);
        }
    }

    private static IllegalArgumentException missingFieldException(String fieldName) {
        return new IllegalArgumentException(String.format("'%s' missing", fieldName));
    }
}
