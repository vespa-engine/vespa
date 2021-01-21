package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.slime.Type;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * (de)serializes endpoint certificate metadata
 * <p>
 * A copy of package com.yahoo.vespa.config.server.tenant.EndpointCertificateMetadata,
 * but with additional fields as we need to store some more information in the controller.
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
    private final static String lastRequestedField = "lastRequested";
    private final static String requestIdField = "requestId";
    private final static String requestedDnsSansField = "requestedDnsSans";
    private final static String issuerField = "issuer";
    private final static String expiryField = "expiry";
    private final static String lastRefreshedField = "lastRefreshed";

    public static Slime toSlime(EndpointCertificateMetadata metadata) {
        Slime slime = new Slime();
        Cursor object = slime.setObject();
        object.setString(keyNameField, metadata.keyName());
        object.setString(certNameField, metadata.certName());
        object.setLong(versionField, metadata.version());
        object.setLong(lastRequestedField, metadata.lastRequested());
        object.setString(requestIdField, metadata.request_id());
        var cursor = object.setArray(requestedDnsSansField);
        metadata.requestedDnsSans().forEach(cursor::addString);
        object.setString(issuerField, metadata.issuer());
        metadata.expiry().ifPresent(expiry -> object.setLong(expiryField, expiry));
        metadata.lastRefreshed().ifPresent(refreshTime -> object.setLong(lastRefreshedField, refreshTime));

        return slime;
    }

    public static EndpointCertificateMetadata fromSlime(Inspector inspector) {
        if (inspector.type() != Type.OBJECT)
            throw new IllegalArgumentException("Unknown format encountered for endpoint certificate metadata!");

        return new EndpointCertificateMetadata(
                inspector.field(keyNameField).asString(),
                inspector.field(certNameField).asString(),
                Math.toIntExact(inspector.field(versionField).asLong()),
                inspector.field(lastRequestedField).asLong(),
                inspector.field(requestIdField).asString(),
                IntStream.range(0, inspector.field(requestedDnsSansField).entries())
                        .mapToObj(i -> inspector.field(requestedDnsSansField).entry(i).asString()).collect(Collectors.toList()),
                inspector.field(issuerField).asString(),
                inspector.field(expiryField).valid() ?
                        Optional.of(inspector.field(expiryField).asLong()) :
                        Optional.empty(),
                inspector.field(lastRefreshedField).valid() ?
                        Optional.of(inspector.field(lastRefreshedField).asLong()) :
                        Optional.empty());
    }

    public static EndpointCertificateMetadata fromJsonString(String zkData) {
        return fromSlime(SlimeUtils.jsonToSlime(zkData).get());
    }
}
