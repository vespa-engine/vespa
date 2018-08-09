// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.utils.ntoken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yahoo.athenz.auth.util.Crypto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link AthenzTruststore} that is backed by athenz.conf
 *
 * @author bjorncs
 */
public class AthenzConfTruststore implements AthenzTruststore {

    private final Map<String, PublicKey> zmsPublicKeys;
    private final Map<String, PublicKey> ztsPublicKeys;

    public AthenzConfTruststore(Path athenzConfFile) {
        try {
            JsonNode root = new ObjectMapper().readTree(athenzConfFile.toFile());
            this.zmsPublicKeys = loadPublicKeys((ArrayNode) root.get("zmsPublicKeys"));
            this.ztsPublicKeys = loadPublicKeys((ArrayNode) root.get("ztsPublicKeys"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, PublicKey> loadPublicKeys(ArrayNode keysArray) {
        Map<String, PublicKey> publicKeys = new HashMap<>();
        for (JsonNode keyEntry : keysArray) {
            String keyId = keyEntry.get("id").textValue();
            String encodedPublicKey = keyEntry.get("key").textValue();
            PublicKey publicKey = Crypto.loadPublicKey(Crypto.ybase64DecodeString(encodedPublicKey));
            publicKeys.put(keyId, publicKey);
        }
        return publicKeys;
    }

    @Override
    public Optional<PublicKey> getZmsPublicKey(String keyId) {
        return Optional.ofNullable(zmsPublicKeys.get(keyId));
    }

    @Override
    public Optional<PublicKey> getZtsPublicKey(String keyId) {
        return Optional.ofNullable(ztsPublicKeys.get(keyId));
    }
}
