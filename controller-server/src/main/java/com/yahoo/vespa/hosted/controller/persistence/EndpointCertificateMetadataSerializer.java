package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.slime.Type;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    private final static String requestIdField = "requestId";
    private final static String requestedDnsSansField = "requestedDnsSans";
    private final static String issuerField = "issuer";

    public static Slime toSlime(EndpointCertificateMetadata metadata) {
        Slime slime = new Slime();
        Cursor object = slime.setObject();
        object.setString(keyNameField, metadata.keyName());
        object.setString(certNameField, metadata.certName());
        object.setLong(versionField, metadata.version());

        metadata.request_id().ifPresent(id -> object.setString(requestIdField, id));
        metadata.requestedDnsSans().ifPresent(sans -> {
            Cursor cursor = object.setArray(requestedDnsSansField);
            sans.forEach(cursor::addString);
        });

        return slime;
    }

    public static EndpointCertificateMetadata fromSlime(Inspector inspector) {
        if (inspector.type() != Type.OBJECT)
            throw new IllegalArgumentException("Unknown format encountered for endpoint certificate metadata!");
        Optional<String> request_id = inspector.field(requestIdField).valid() ?
                Optional.of(inspector.field(requestIdField).asString()) :
                Optional.empty();

        Optional<List<String>> requestedDnsSans = inspector.field(requestedDnsSansField).valid() ?
                Optional.of(IntStream.range(0, inspector.field(requestedDnsSansField).entries())
                        .mapToObj(i -> inspector.field(requestedDnsSansField).entry(i).asString()).collect(Collectors.toList())) :
                Optional.empty();

        Optional<String> issuer = inspector.field(issuerField).valid() ?
                Optional.of(inspector.field(issuerField).asString()) :
                Optional.empty();

        return new EndpointCertificateMetadata(
                inspector.field(keyNameField).asString(),
                inspector.field(certNameField).asString(),
                Math.toIntExact(inspector.field(versionField).asLong()),
                request_id,
                requestedDnsSans,
                issuer);
    }

    public static EndpointCertificateMetadata fromJsonString(String zkdata) {
        return fromSlime(SlimeUtils.jsonToSlime(zkdata).get());
    }
}
