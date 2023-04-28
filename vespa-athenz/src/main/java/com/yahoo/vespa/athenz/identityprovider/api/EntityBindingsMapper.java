// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.DefaultSignedIdentityDocumentEntity;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.IdentityDocumentEntity;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.LegacySignedIdentityDocumentEntity;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.SignedIdentityDocumentEntity;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId.fromDottedString;

/**
 * Utility class for mapping objects model types and their Jackson binding versions.
 *
 * @author bjorncs
 * @author mortent
 */
public class EntityBindingsMapper {

    static final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private EntityBindingsMapper() {}

    public static String toAttestationData(SignedIdentityDocument model) {
        try {
            return mapper.writeValueAsString(toSignedIdentityDocumentEntity(model));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static SignedIdentityDocument fromInputStream(InputStream in) throws IOException {
        return EntityBindingsMapper.toSignedIdentityDocument(mapper.readValue(in, SignedIdentityDocumentEntity.class));
    }

    public static SignedIdentityDocument fromString(String json) throws IOException {
        return EntityBindingsMapper.toSignedIdentityDocument(mapper.readValue(json, SignedIdentityDocumentEntity.class));
    }

    public static SignedIdentityDocument toSignedIdentityDocument(SignedIdentityDocumentEntity entity) {
        if (entity instanceof LegacySignedIdentityDocumentEntity docEntity) {
            IdentityDocument doc = new IdentityDocument(
                    fromDottedString(docEntity.providerUniqueId()),
                    new AthenzService(docEntity.providerService()),
                    docEntity.configServerHostname(),
                    docEntity.instanceHostname(),
                    docEntity.createdAt(),
                    docEntity.ipAddresses(),
                    IdentityType.fromId(docEntity.identityType()),
                    Optional.ofNullable(docEntity.clusterType()).map(ClusterType::from).orElse(null),
                    docEntity.ztsUrl(),
                    Optional.ofNullable(docEntity.serviceIdentity()).map(AthenzIdentities::from).orElse(null),
                    docEntity.unknownAttributes());
            return new LegacySignedIdentityDocument(
                    docEntity.signature(),
                    docEntity.signingKeyVersion(),
                    entity.documentVersion(),
                    doc);
        } else if (entity instanceof DefaultSignedIdentityDocumentEntity docEntity) {
            return new DefaultSignedIdentityDocument(docEntity.signature(),
                                                     docEntity.signingKeyVersion(),
                                                     docEntity.documentVersion(),
                                                     docEntity.data());
        } else {
            throw new IllegalArgumentException("Unknown signed identity document type: " + entity.getClass().getName());
        }
    }

    public static SignedIdentityDocumentEntity toSignedIdentityDocumentEntity(SignedIdentityDocument model) {
        if (model instanceof LegacySignedIdentityDocument legacyModel) {
            IdentityDocument idDoc = legacyModel.identityDocument();
            return new LegacySignedIdentityDocumentEntity(
                    legacyModel.signature(),
                    legacyModel.signingKeyVersion(),
                    idDoc.providerUniqueId().asDottedString(),
                    idDoc.providerService().getFullName(),
                    legacyModel.documentVersion(),
                    idDoc.configServerHostname(),
                    idDoc.instanceHostname(),
                    idDoc.createdAt(),
                    idDoc.ipAddresses(),
                    idDoc.identityType().id(),
                    Optional.ofNullable(idDoc.clusterType()).map(ClusterType::toConfigValue).orElse(null),
                    idDoc.ztsUrl(),
                    Optional.ofNullable(idDoc.serviceIdentity()).map(AthenzIdentity::getFullName).orElse(null),
                    idDoc.unknownAttributes());
        } else if (model instanceof DefaultSignedIdentityDocument defaultModel){
            return new DefaultSignedIdentityDocumentEntity(defaultModel.signature(),
                                                           defaultModel.signingKeyVersion(),
                                                           defaultModel.documentVersion(),
                                                           defaultModel.data());
        } else {
            throw new IllegalArgumentException("Unsupported model type: " + model.getClass().getName());
        }
    }

    public static SignedIdentityDocument readSignedIdentityDocumentFromFile(Path file) {
        try (InputStream inputStream = Files.newInputStream(file)) {
            return fromInputStream(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeSignedIdentityDocumentToFile(Path file, SignedIdentityDocument document) {
        try {
            SignedIdentityDocumentEntity entity = EntityBindingsMapper.toSignedIdentityDocumentEntity(document);
            Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
                mapper.writeValue(outputStream, entity);
            }
            Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static IdentityDocument fromIdentityDocumentData(String data) {
        byte[] decoded = Base64.getDecoder().decode(data);
        IdentityDocumentEntity docEntity = Exceptions.uncheck(() -> mapper.readValue(decoded, IdentityDocumentEntity.class));
        return new IdentityDocument(
                fromDottedString(docEntity.providerUniqueId()),
                new AthenzService(docEntity.providerService()),
                docEntity.configServerHostname(),
                docEntity.instanceHostname(),
                docEntity.createdAt(),
                docEntity.ipAddresses(),
                IdentityType.fromId(docEntity.identityType()),
                Optional.ofNullable(docEntity.clusterType()).map(ClusterType::from).orElse(null),
                docEntity.ztsUrl(),
                Optional.ofNullable(docEntity.serviceIdentity()).map(AthenzIdentities::from).orElse(null),
                docEntity.unknownAttributes());
    }

    public static String toIdentityDocmentData(IdentityDocument identityDocument) {
        IdentityDocumentEntity documentEntity = new IdentityDocumentEntity(
                identityDocument.providerUniqueId().asDottedString(),
                identityDocument.providerService().getFullName(),
                identityDocument.configServerHostname(),
                identityDocument.instanceHostname(),
                identityDocument.createdAt(),
                identityDocument.ipAddresses(),
                identityDocument.identityType().id(),
                Optional.ofNullable(identityDocument.clusterType()).map(ClusterType::toConfigValue).orElse(null),
                identityDocument.ztsUrl(),
                identityDocument.serviceIdentity().getFullName());
        try {
            byte[] bytes = mapper.writeValueAsBytes(documentEntity);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error during serialization of identity document.", e);
        }
    }
}
