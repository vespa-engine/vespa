// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.SignedIdentityDocumentEntity;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import static com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId.fromDottedString;

/**
 * Utility class for mapping objects model types and their Jackson binding versions.
 *
 * @author bjorncs
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
        return new SignedIdentityDocument(
                entity.signature(),
                entity.signingKeyVersion(),
                fromDottedString(entity.providerUniqueId()),
                new AthenzService(entity.providerService()),
                entity.documentVersion(),
                entity.configServerHostname(),
                entity.instanceHostname(),
                entity.createdAt(),
                entity.ipAddresses(),
                IdentityType.fromId(entity.identityType()),
                Optional.ofNullable(entity.clusterType()).map(ClusterType::from).orElse(null),
                entity.ztsUrl(),
                Optional.ofNullable(entity.serviceIdentity()).map(AthenzIdentities::from).orElse(null),
                entity.unknownAttributes());
    }

    public static SignedIdentityDocumentEntity toSignedIdentityDocumentEntity(SignedIdentityDocument model) {
        return new SignedIdentityDocumentEntity(
                model.signature(),
                model.signingKeyVersion(),
                model.providerUniqueId().asDottedString(),
                model.providerService().getFullName(),
                model.documentVersion(),
                model.configServerHostname(),
                model.instanceHostname(),
                model.createdAt(),
                model.ipAddresses(),
                model.identityType().id(),
                Optional.ofNullable(model.clusterType()).map(ClusterType::toConfigValue).orElse(null),
                model.ztsUrl(),
                Optional.ofNullable(model.serviceIdentity()).map(AthenzIdentity::getFullName).orElse(null),
                model.unknownAttributes());
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

}
