// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.TlsSecrets;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.path.Path;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
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

            Slime slime = SlimeUtils.jsonToSlime(data.get());
            final var inspector = slime.get();

            switch (inspector.type()) {
                case STRING: // TODO: Remove once all are stored as JSON
                    return readFromSecretStore(Optional.ofNullable(inspector.asString()));
                case OBJECT:
                    var tlsSecretsInfo = new TlsSecretsMetadata();
                    tlsSecretsInfo.certName = inspector.field("certName").asString();
                    tlsSecretsInfo.keyName = inspector.field("keyName").asString();
                    tlsSecretsInfo.version = Math.toIntExact(inspector.field("version").asLong());
                    return Optional.of(readFromSecretStore(tlsSecretsInfo));
                default:
                    throw new IllegalArgumentException("Unknown format encountered for TLS secrets metadata!");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reading TLS secret key of " + application, e);
        }
    }

    public void writeTlsSecretsKeyToZooKeeper(ApplicationId application, String tlsSecretsKey) {
        if (tlsSecretsKey == null) return;
        writeTlsSecretsAsString(application, tlsSecretsKey);
    }

    private void writeTlsSecretsAsString(ApplicationId application, String tlsSecretsKey) {
        try {
            Slime slime = new Slime();
            slime.setString(tlsSecretsKey);
            curator.set(tlsSecretsKeyOf(application), SlimeUtils.toJsonBytes(slime));
        } catch (Exception e) {
            throw new RuntimeException("Could not write TLS secret key of " + application, e);
        }
    }

    void writeTlsSecretsMetadata(ApplicationId application, TlsSecretsMetadata tlsSecretsMetadata) {
        try {
            Slime slime = new Slime();
            Cursor cursor = slime.setObject();
            cursor.setString(TlsSecretsMetadata.certNameField, tlsSecretsMetadata.certName);
            cursor.setString(TlsSecretsMetadata.keyNameField, tlsSecretsMetadata.keyName);
            cursor.setLong(TlsSecretsMetadata.versionField, tlsSecretsMetadata.version);
            curator.set(tlsSecretsKeyOf(application), SlimeUtils.toJsonBytes(slime));
        } catch (Exception e) {
            throw new RuntimeException("Could not write TLS secret key of " + application, e);
        }
    }

    public Optional<TlsSecrets> getTlsSecrets(Optional<String> secretKeyname, ApplicationId applicationId) {
        if (secretKeyname == null || secretKeyname.isEmpty()) {
            return readTlsSecretsKeyFromZookeeper(applicationId);
        }
        return readFromSecretStore(secretKeyname);
    }

    private Optional<TlsSecrets> readFromSecretStore(Optional<String> secretKeyname) {
        if (secretKeyname.isEmpty()) return Optional.empty();
        try {
            String cert = secretStore.getSecret(secretKeyname.get() + "-cert");
            String key = secretStore.getSecret(secretKeyname.get() + "-key");
            return Optional.of(new TlsSecrets(cert, key));
        } catch (RuntimeException e) {
            // Assume not ready yet
            return Optional.of(TlsSecrets.MISSING);
        }
    }

    private TlsSecrets readFromSecretStore(TlsSecretsMetadata tlsSecretsMetadata) {
        try {
            String cert = secretStore.getSecret(tlsSecretsMetadata.certName, tlsSecretsMetadata.version);
            String key = secretStore.getSecret(tlsSecretsMetadata.keyName, tlsSecretsMetadata.version);
            return new TlsSecrets(cert, key);
        } catch (RuntimeException e) {
            // Assume not ready yet
            return TlsSecrets.MISSING;
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

    static class TlsSecretsMetadata {
        final static String keyNameField = "keyName";
        final static String certNameField = "certName";
        final static String versionField = "version";
        String keyName;
        String certName;
        int version;
    }
}
