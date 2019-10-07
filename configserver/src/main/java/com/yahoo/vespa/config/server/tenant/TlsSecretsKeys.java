// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.model.api.TlsSecrets;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;

import java.util.Optional;

/**
 * TLS Secret keys for applications (used to retrieve actual certificate/key from secret store). Persisted in ZooKeeper.
 *
 * @author andreer
 */
public class TlsSecretsKeys {

    private final Path path;
    private final SecretStore secretStore;
    private final Curator curator;

    public TlsSecretsKeys(Curator curator, Path tenantPath, SecretStore secretStore) {
        this.curator = curator;
        this.path = tenantPath.append("tlsSecretsKeys/");
        this.secretStore = secretStore;
    }

    public Optional<TlsSecrets> readTlsSecretsKeyFromZookeeper(ApplicationId application) {
        try {
            Optional<byte[]> data = curator.getData(tlsSecretsKeyOf(application));
            if (data.isEmpty() || data.get().length == 0) return Optional.empty();
            String tlsSecretsKey = new ObjectMapper().readValue(data.get(), new TypeReference<String>() {});
            Optional<byte[]> versionData = curator.getData(tlsSecretsKeyVersionOf(application));
            Integer tlsSecretsKeyVersion =
                    versionData.isEmpty() ? null :
                    new ObjectMapper().readValue(versionData.get(), new TypeReference<Integer>() {});
            return readFromSecretStore(Optional.ofNullable(tlsSecretsKey), Optional.ofNullable(tlsSecretsKeyVersion));
        } catch (Exception e) {
            throw new RuntimeException("Error reading TLS secret key of " + application, e);
        }
    }

    public void writeTlsSecretsKeyToZooKeeper(ApplicationId application, String tlsSecretsKey, Integer tlsSecretsKeyVersion) {
        if (tlsSecretsKey == null) return;
        try {
            byte[] data = new ObjectMapper().writeValueAsBytes(tlsSecretsKey);
            curator.set(tlsSecretsKeyOf(application), data);
            byte[] versionData = new ObjectMapper().writeValueAsBytes(tlsSecretsKeyVersion);
            curator.set(tlsSecretsKeyVersionOf(application), versionData);
        } catch (Exception e) {
            throw new RuntimeException("Could not write TLS secret key of " + application, e);
        }
    }

    public Optional<TlsSecrets> getTlsSecrets(Optional<String> secretKeyname, Optional<Integer> secretKeyVersion, ApplicationId applicationId) {
        if (secretKeyname == null || secretKeyname.isEmpty() || secretKeyname.get().isEmpty()) {
            return readTlsSecretsKeyFromZookeeper(applicationId);
        }
        return readFromSecretStore(secretKeyname, secretKeyVersion);
    }

    private Optional<TlsSecrets> readFromSecretStore(Optional<String> secretKeyname, Optional<Integer> secretKeyVersion) {
        if (secretKeyname.isEmpty()) return Optional.empty();
        try {
            String cert = secretStore.getSecret(secretKeyname.get() + "-cert", secretKeyVersion.orElse(0));
            String key = secretStore.getSecret(secretKeyname.get() + "-key", secretKeyVersion.orElse(0));
            return Optional.of(new TlsSecrets(cert, key));
        } catch (RuntimeException e) {
            // Assume not ready yet
            return Optional.of(TlsSecrets.MISSING);
        }
    }

    /** Returns a transaction which deletes these tls secrets key if they exist */
    public CuratorTransaction delete(ApplicationId application) {
        if (!curator.exists(tlsSecretsKeyOf(application))) return CuratorTransaction.empty(curator);
        return CuratorTransaction.from(CuratorOperations.delete(tlsSecretsKeyOf(application).getAbsolute()), curator);
    }

    /** Returns the path storing the tls secrets key for an application */
    private Path tlsSecretsKeyOf(ApplicationId application) {
        return path.append(application.serializedForm());
    }

    /** Returns the path storing the tls secrets key for an application */
    private Path tlsSecretsKeyVersionOf(ApplicationId application) {
        return path.append(application.serializedForm()).append(".version");
    }
}
