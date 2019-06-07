// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.ApplicationId;
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
    private final Curator curator;

    public TlsSecretsKeys(Curator curator, Path tenantPath) {
        this.curator = curator;
        this.path = tenantPath.append("tlsSecretsKeys/");
    }

    public Optional<String> readTlsSecretsKeyFromZookeeper(ApplicationId application) {
        try {
            Optional<byte[]> data = curator.getData(tlsSecretsKeyOf(application));
            if (data.isEmpty() || data.get().length == 0) return Optional.empty();
            String tlsSecretsKey = new ObjectMapper().readValue(data.get(), new TypeReference<String>() {});
            return Optional.of(tlsSecretsKey);
        } catch (Exception e) {
            throw new RuntimeException("Error reading TLS secret key of " + application, e);
        }
    }

    public void writeTlsSecretsKeyToZooKeeper(ApplicationId application, String tlsSecretsKey) {
        if (tlsSecretsKey == null) return;
        try {
            byte[] data = new ObjectMapper().writeValueAsBytes(tlsSecretsKey);
            curator.set(tlsSecretsKeyOf(application), data);
        } catch (Exception e) {
            throw new RuntimeException("Could not write TLS secret key of " + application, e);
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

}
