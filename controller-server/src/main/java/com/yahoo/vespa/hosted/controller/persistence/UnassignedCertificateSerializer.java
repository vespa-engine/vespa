// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.certificate.UnassignedCertificate;

/**
 * @author mpolden
 */
public class UnassignedCertificateSerializer {

    private static final String stateKey = "state";
    private static final String certificateKey = "certificate";

    public Slime toSlime(UnassignedCertificate unassignedCertificate) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString(stateKey, unassignedCertificate.state().name());
        EndpointCertificateMetadataSerializer.toSlime(unassignedCertificate.certificate(), root.setObject(certificateKey));
        return slime;
    }

    public UnassignedCertificate fromSlime(Slime slime) {
        Cursor root = slime.get();
        UnassignedCertificate.State state = UnassignedCertificate.State.valueOf(root.field(stateKey).asString());
        EndpointCertificateMetadata certificate = EndpointCertificateMetadataSerializer.fromSlime(root.field(certificateKey));
        return new UnassignedCertificate(certificate, state);
    }

}
