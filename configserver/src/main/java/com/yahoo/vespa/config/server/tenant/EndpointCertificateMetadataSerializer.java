// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.EndpointCertificateMetadata;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.slime.Type;

import static com.yahoo.config.model.api.EndpointCertificateMetadata.Provider.digicert;
import static com.yahoo.config.model.api.EndpointCertificateMetadata.Provider.globalsign;
import static com.yahoo.config.model.api.EndpointCertificateMetadata.Provider.letsencrypt;
import static com.yahoo.config.model.api.EndpointCertificateMetadata.Provider.zerossl;

/**
 * (de)serializes endpoint certificate metadata
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
    private final static String issuerField = "issuer";

    public static void toSlime(EndpointCertificateMetadata metadata, Cursor object) {
        object.setString(keyNameField, metadata.keyName());
        object.setString(certNameField, metadata.certName());
        object.setLong(versionField, metadata.version());
        object.setString(issuerField, serializedValue(metadata.issuer()));
    }

    public static EndpointCertificateMetadata fromSlime(Inspector inspector) {
        if (inspector.type() == Type.OBJECT) {
            return new EndpointCertificateMetadata(
                    inspector.field(keyNameField).asString(),
                    inspector.field(certNameField).asString(),
                    Math.toIntExact(inspector.field(versionField).asLong()),
                    providerOf(SlimeUtils.optionalString(inspector.field(issuerField)).orElse("")));
        }
        throw new IllegalArgumentException("Unknown format encountered for endpoint certificate metadata!");
    }

    private static EndpointCertificateMetadata.Provider providerOf(String name) {
        return switch (name) {
            case "digicert" -> digicert;
            case "globalsign" -> globalsign;
            case "zerossl" -> zerossl;
            case "letsencrypt" -> letsencrypt;
            default -> digicert;
        };
    }

    private static String serializedValue(EndpointCertificateMetadata.Provider provider) {
        return switch (provider) {
            case digicert -> "digicert";
            case globalsign -> "globalsign";
            case zerossl -> "zerossl";
            case letsencrypt -> "letsencrypt";
        };
    }

}
