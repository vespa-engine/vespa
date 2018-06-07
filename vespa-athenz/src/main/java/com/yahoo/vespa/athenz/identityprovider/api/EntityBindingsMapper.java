// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.IdentityDocumentEntity;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.SignedIdentityDocumentEntity;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.VespaUniqueInstanceIdEntity;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Base64;

import static com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId.fromDottedString;

/**
 * Utility class for mapping objects model types and their Jackson binding versions.
 *
 * @author bjorncs
 */
public class EntityBindingsMapper {

    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private EntityBindingsMapper() {}

    public static String toAttestationData(SignedIdentityDocument model) {
        try {
            return mapper.writeValueAsString(toSignedIdentityDocumentEntity(model));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static VespaUniqueInstanceId toVespaUniqueInstanceId(VespaUniqueInstanceIdEntity entity) {
        return new VespaUniqueInstanceId(
                entity.clusterIndex, entity.clusterId, entity.instance, entity.application, entity.tenant, entity.region, entity.environment, entity.type != null ? IdentityType.fromId(entity.type) : null); // TODO Remove support for legacy representation without type
    }

    public static IdentityDocument toIdentityDocument(IdentityDocumentEntity entity) {
        return new IdentityDocument(
                toVespaUniqueInstanceId(entity.providerUniqueId),
                entity.configServerHostname,
                entity.instanceHostname,
                entity.createdAt,
                entity.ipAddresses);
    }

    public static SignedIdentityDocument toSignedIdentityDocument(SignedIdentityDocumentEntity entity) {
        return new SignedIdentityDocument(
                toIdentityDocument(entity.identityDocument),
                entity.signature,
                entity.signingKeyVersion,
                fromDottedString(entity.providerUniqueId),
                entity.dnsSuffix,
                (AthenzService) AthenzIdentities.from(entity.providerService),
                entity.ztsEndpoint,
                entity.documentVersion,
                entity.configServerHostname,
                entity.instanceHostname,
                entity.createdAt,
                entity.ipAddresses,
                entity.identityType != null ? IdentityType.fromId(entity.identityType) : null); // TODO Remove support for legacy representation without type
    }

    public static VespaUniqueInstanceIdEntity toVespaUniqueInstanceIdEntity(VespaUniqueInstanceId model) {
        return new VespaUniqueInstanceIdEntity(
                model.tenant(), model.application(), model.environment(), model.region(),
                model.instance(), model.clusterId(), model.clusterIndex(), model.type() != null ? model.type().id() : null); // TODO Remove support for legacy representation without type
    }

    public static IdentityDocumentEntity toIdentityDocumentEntity(IdentityDocument model) {
        return new IdentityDocumentEntity(
                toVespaUniqueInstanceIdEntity(model.providerUniqueId()),
                model.configServerHostname(),
                model.instanceHostname(),
                model.createdAt(),
                model.ipAddresses());
    }

    public static SignedIdentityDocumentEntity toSignedIdentityDocumentEntity(SignedIdentityDocument model) {
        try {
            IdentityDocumentEntity identityDocumentEntity = toIdentityDocumentEntity(model.identityDocument());
            String rawDocument = Base64.getEncoder().encodeToString(mapper.writeValueAsString(identityDocumentEntity).getBytes());
            return new SignedIdentityDocumentEntity(
                    rawDocument,
                    model.signature(),
                    model.signingKeyVersion(),
                    model.providerUniqueId().asDottedString(),
                    model.dnsSuffix(),
                    model.providerService().getFullName(),
                    model.ztsEndpoint(),
                    model.documentVersion(),
                    model.configServerHostname(),
                    model.instanceHostname(),
                    model.createdAt(),
                    model.ipAddresses(),
                    model.identityType() != null ? model.identityType().id() : null); // TODO Remove support for legacy representation without type
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static SignedIdentityDocument readSignedIdentityDocumentFromFile(Path file) {
        try {
            SignedIdentityDocumentEntity entity = mapper.readValue(file.toFile(), SignedIdentityDocumentEntity.class);
            return EntityBindingsMapper.toSignedIdentityDocument(entity);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeSignedIdentityDocumentToFile(Path file, SignedIdentityDocument document) {
        try {
            SignedIdentityDocumentEntity entity = EntityBindingsMapper.toSignedIdentityDocumentEntity(document);
            mapper.writeValue(file.toFile(), entity);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
