// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.SignedIdentityDocumentEntity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

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

    public static SignedIdentityDocument toSignedIdentityDocument(SignedIdentityDocumentEntity entity) {
        return new SignedIdentityDocument(
                entity.signature,
                entity.signingKeyVersion,
                fromDottedString(entity.providerUniqueId),
                new AthenzService(entity.providerService),
                entity.documentVersion,
                entity.configServerHostname,
                entity.instanceHostname,
                entity.createdAt,
                entity.ipAddresses,
                IdentityType.fromId(entity.identityType));
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
                model.identityType().id());
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
            Path tempFile = Paths.get(file.toAbsolutePath().toString() + ".tmp");
            mapper.writeValue(tempFile.toFile(), entity);
            Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
