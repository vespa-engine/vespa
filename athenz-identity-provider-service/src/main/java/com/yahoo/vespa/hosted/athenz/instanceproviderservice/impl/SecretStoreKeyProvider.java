// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

import com.google.inject.Inject;
import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.http.SecretStore;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.KeyProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

import static com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils.getZoneConfig;

/**
 * @author mortent
 */
@SuppressWarnings("unused") //  Injected component
public class SecretStoreKeyProvider implements KeyProvider {

    private final SecretStore secretStore;
    private final String secretName;
    private final Map<Integer, KeyPair> secrets;

    @Inject
    public SecretStoreKeyProvider(SecretStore secretStore,
                                  Zone zone,
                                  AthenzProviderServiceConfig config) {
        this.secretStore = secretStore;
        this.secretName = getZoneConfig(config, zone).secretName();
        this.secrets = new HashMap<>();
    }

    @Override
    public PrivateKey getPrivateKey(int version) {
        return getKeyPair(version).getPrivate();
    }

    @Override
    public PublicKey getPublicKey(int version) {
        return getKeyPair(version).getPublic();
    }

    private KeyPair getKeyPair(int version) {
        synchronized (secrets) {
            KeyPair keyPair = secrets.get(version);
            if (keyPair == null) {
                keyPair = readKeyPair(version);
                secrets.put(version, keyPair);
            }
            return keyPair;
        }
    }

    // TODO: Consider moving to cryptoutils
    private KeyPair readKeyPair(int version) {
        PrivateKey privateKey = Crypto.loadPrivateKey(secretStore.getSecret(secretName, version));
        PublicKey publicKey = Crypto.extractPublicKey(privateKey);
        return new KeyPair(publicKey, privateKey);
    }
}
