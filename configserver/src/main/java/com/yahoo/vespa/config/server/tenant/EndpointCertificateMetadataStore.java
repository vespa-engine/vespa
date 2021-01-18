// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.EndpointCertificateMetadata;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.path.Path;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;

import java.util.Optional;

/**
 * Stores the endpoint certificate metadata for an application.
 * This metadata is then used to retrieve the actual secrets from {@link EndpointCertificateRetriever}.
 *
 * @author andreer
 */
public class EndpointCertificateMetadataStore {

    private final Path path;
    private final Curator curator;

    public EndpointCertificateMetadataStore(Curator curator, Path tenantPath) {
        this.curator = curator;
        this.path = tenantPath.append("tlsSecretsKeys/");
    }

    /** Reads the endpoint certificate metadata from ZooKeeper, if it exists */
    public Optional<EndpointCertificateMetadata> readEndpointCertificateMetadata(ApplicationId application) {
        try {
            Optional<byte[]> data = curator.getData(endpointCertificateMetadataPathOf(application));
            if (data.isEmpty() || data.get().length == 0) return Optional.empty();
            Slime slime = SlimeUtils.jsonToSlime(data.get());
            EndpointCertificateMetadata endpointCertificateMetadata = EndpointCertificateMetadataSerializer.fromSlime(slime.get());
            return Optional.of(endpointCertificateMetadata);
        } catch (Exception e) {
            throw new RuntimeException("Error reading endpoint certificate metadata for " + application, e);
        }
    }

    /** Writes the endpoint certificate metadata to ZooKeeper */
    public void writeEndpointCertificateMetadata(ApplicationId application, EndpointCertificateMetadata endpointCertificateMetadata) {
        try {
            Slime slime = new Slime();
            EndpointCertificateMetadataSerializer.toSlime(endpointCertificateMetadata, slime.setObject());
            curator.set(endpointCertificateMetadataPathOf(application), SlimeUtils.toJsonBytes(slime));
        } catch (Exception e) {
            throw new RuntimeException("Could not write endpoint certificate metadata for " + application, e);
        }
    }

    /** Returns a transaction which deletes endpoint certificate metadata if it exists */
    public CuratorTransaction delete(ApplicationId application) {
        if (!curator.exists(endpointCertificateMetadataPathOf(application))) return CuratorTransaction.empty(curator);
        return CuratorTransaction.from(CuratorOperations.delete(endpointCertificateMetadataPathOf(application).getAbsolute()), curator);
    }

    /** Returns the path storing the endpoint certificate metadata for an application */
    private Path endpointCertificateMetadataPathOf(ApplicationId application) {
        return path.append(application.serializedForm());
    }
}
