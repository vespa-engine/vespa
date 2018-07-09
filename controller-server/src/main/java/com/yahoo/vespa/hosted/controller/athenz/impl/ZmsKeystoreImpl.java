// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.impl;

import com.google.inject.Inject;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.api.AthenzPublicKey;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZmsException;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZmsKeystore;

import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 *  Downloads and caches public keys for Athens services.
 *
 * @author bjorncs
 */
public class ZmsKeystoreImpl implements ZmsKeystore {
    private static final Logger log = Logger.getLogger(ZmsKeystoreImpl.class.getName());

    private final Map<FullKeyId, PublicKey> cachedKeys = new ConcurrentHashMap<>();
    private final AthenzClientFactory athenzClientFactory;

    @Inject
    public ZmsKeystoreImpl(AthenzClientFactory factory) {
        this.athenzClientFactory = factory;
    }

    @Override
    public Optional<PublicKey> getPublicKey(AthenzService service, String keyId)  {
        FullKeyId fullKeyId = new FullKeyId(service, keyId);
        PublicKey cachedKey = cachedKeys.get(fullKeyId);
        if (cachedKey != null) {
            return Optional.of(cachedKey);
        }
        Optional<PublicKey> downloadedKey = downloadPublicKey(fullKeyId);
        downloadedKey.ifPresent(key -> {
            log.log(LogLevel.INFO, "Adding key " + fullKeyId + " to the cache");
            cachedKeys.put(fullKeyId, key);
        });
        return downloadedKey;
    }

    @Override
    public void preloadKeys(AthenzService service) {
        try {
            log.log(LogLevel.INFO, "Downloading keys for " + service);
            List<AthenzPublicKey> publicKeys = athenzClientFactory.createZmsClientWithServicePrincipal()
                    .getPublicKeys(service);
            for (AthenzPublicKey publicKey : publicKeys) {
                FullKeyId fullKeyId = new FullKeyId(service, publicKey.getKeyId());
                log.log(LogLevel.DEBUG, "Adding key " + fullKeyId + " to the cache");
                cachedKeys.put(fullKeyId, publicKey.getPublicKey());
            }
            log.log(LogLevel.INFO, "Successfully downloaded keys for " + service);
        } catch (ZmsException e) {
            log.log(LogLevel.WARNING, "Failed to download keys for " + service + ": " + e.getMessage());
        }
    }

    private Optional<PublicKey> downloadPublicKey(FullKeyId fullKeyId) {
        try {
            log.log(LogLevel.INFO, "Downloading key " + fullKeyId);
            AthenzPublicKey publicKey = athenzClientFactory.createZmsClientWithServicePrincipal()
                    .getPublicKey(fullKeyId.service, fullKeyId.keyId);
            return Optional.of(publicKey.getPublicKey());
        } catch (ZmsException e) {
            if (e.getCode() == 404) { // Key does not exist
                log.log(LogLevel.INFO, "Key " + fullKeyId + " not found");
                return Optional.empty();
            }
            String msg = String.format("Unable to retrieve public key from Athens (%s): %s", fullKeyId, e.getMessage());
            throw createException(msg, e);
        }
    }

    private static RuntimeException createException(String message, Exception cause) {
        log.log(LogLevel.ERROR, message);
        return new RuntimeException(message, cause);
    }

    private static class FullKeyId {
        private final AthenzService service;
        private final String keyId;

        private FullKeyId(AthenzService service, String keyId) {
            this.service = service;
            this.keyId = keyId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FullKeyId fullKeyId1 = (FullKeyId) o;
            return Objects.equals(service, fullKeyId1.service) &&
                    Objects.equals(keyId, fullKeyId1.keyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(service, keyId);
        }

        @Override
        public String toString() {
            return "FullKeyId{" +
                    "service=" + service +
                    ", keyId='" + keyId + '\'' +
                    '}';
        }
    }
}
