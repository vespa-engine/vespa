// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.PooledCertificate;

/**
 * @author mpolden
 */
public class PooledCertificateSerializer {

    private static final String stateKey = "state";
    private static final String certificateKey = "certificate";

    public Slime toSlime(PooledCertificate pooledCertificate) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString(stateKey, pooledCertificate.state().name());
        EndpointCertificateMetadataSerializer.toSlime(pooledCertificate.certificate(), root.setObject(certificateKey));
        return slime;
    }

    public PooledCertificate fromSlime(Slime slime) {
        Cursor root = slime.get();
        PooledCertificate.State state = PooledCertificate.State.valueOf(root.field(stateKey).asString());
        EndpointCertificateMetadata certificate = EndpointCertificateMetadataSerializer.fromSlime(root.field(certificateKey));
        return new PooledCertificate(certificate, state);
    }

}
