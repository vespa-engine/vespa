package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;

/**
 * (de)serializes endpoint certificate metadata
 * <p>
 * A copy of package com.yahoo.vespa.config.server.tenant.EndpointCertificateMetadata,
 * but will soon be extended as we need to store some more information in the controller.
 *
 * @author andreer
 */
public class EndpointCertificateMetadataSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private final static String keyNameField = "keyName";
    private final static String certNameField = "certName";
    private final static String versionField = "version";

    public static Slime toSlime(EndpointCertificateMetadata metadata) {
        Slime slime = new Slime();
        Cursor object = slime.setObject();
        object.setString(keyNameField, metadata.keyName());
        object.setString(certNameField, metadata.certName());
        object.setLong(versionField, metadata.version());
        return slime;
    }

    public static EndpointCertificateMetadata fromSlime(Inspector inspector) {
        switch (inspector.type()) {
            case STRING: // TODO: Remove once all are transmitted and stored as JSON
                return new EndpointCertificateMetadata(
                        inspector.asString() + "-key",
                        inspector.asString() + "-cert",
                        0
                );
            case OBJECT:
                return new EndpointCertificateMetadata(
                        inspector.field(keyNameField).asString(),
                        inspector.field(certNameField).asString(),
                        Math.toIntExact(inspector.field(versionField).asLong())
                );

            default:
                throw new IllegalArgumentException("Unknown format encountered for endpoint certificate metadata!");
        }
    }

    public static EndpointCertificateMetadata fromTlsSecretsKeysString(String tlsSecretsKeys) {
        return fromSlime(new Slime().setString(tlsSecretsKeys));
    }

    public static EndpointCertificateMetadata fromJsonOrTlsSecretsKeysString(String zkdata) {
        if(zkdata.strip().startsWith("{")) {
            return fromSlime(SlimeUtils.jsonToSlime(zkdata).get());
        } else {
            return fromTlsSecretsKeysString(zkdata);
        }
    }
}
