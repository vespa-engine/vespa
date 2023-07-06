// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.slime.Type;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificate;

import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Serializer for {@link EndpointCertificate}.
 *
 * @author andreer
 */
public class EndpointCertificateSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster, and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private final static String keyNameField = "keyName";
    private final static String certNameField = "certName";
    private final static String versionField = "version";
    private final static String lastRequestedField = "lastRequested";
    private final static String rootRequestIdField = "requestId";
    private final static String leafRequestIdField = "leafRequestId";
    private final static String requestedDnsSansField = "requestedDnsSans";
    private final static String issuerField = "issuer";
    private final static String expiryField = "expiry";
    private final static String lastRefreshedField = "lastRefreshed";
    private final static String randomizedIdField = "randomizedId";

    public static Slime toSlime(EndpointCertificate cert) {
        Slime slime = new Slime();
        Cursor object = slime.setObject();
        toSlime(cert, object);
        return slime;
    }

    public static void toSlime(EndpointCertificate cert, Cursor object) {
        object.setString(keyNameField, cert.keyName());
        object.setString(certNameField, cert.certName());
        object.setLong(versionField, cert.version());
        object.setLong(lastRequestedField, cert.lastRequested());
        object.setString(rootRequestIdField, cert.rootRequestId());
        cert.leafRequestId().ifPresent(leafRequestId -> object.setString(leafRequestIdField, leafRequestId));
        var cursor = object.setArray(requestedDnsSansField);
        cert.requestedDnsSans().forEach(cursor::addString);
        object.setString(issuerField, cert.issuer());
        cert.expiry().ifPresent(expiry -> object.setLong(expiryField, expiry));
        cert.lastRefreshed().ifPresent(refreshTime -> object.setLong(lastRefreshedField, refreshTime));
        cert.randomizedId().ifPresent(randomizedId -> object.setString(randomizedIdField, randomizedId));
    }

    public static EndpointCertificate fromSlime(Inspector inspector) {
        if (inspector.type() != Type.OBJECT)
            throw new IllegalArgumentException("Invalid format encountered for endpoint certificate");

        return new EndpointCertificate(
                inspector.field(keyNameField).asString(),
                inspector.field(certNameField).asString(),
                Math.toIntExact(inspector.field(versionField).asLong()),
                inspector.field(lastRequestedField).asLong(),
                inspector.field(rootRequestIdField).asString(),
                SlimeUtils.optionalString(inspector.field(leafRequestIdField)),
                IntStream.range(0, inspector.field(requestedDnsSansField).entries())
                        .mapToObj(i -> inspector.field(requestedDnsSansField).entry(i).asString()).toList(),
                inspector.field(issuerField).asString(),
                inspector.field(expiryField).valid() ?
                        Optional.of(inspector.field(expiryField).asLong()) :
                        Optional.empty(),
                inspector.field(lastRefreshedField).valid() ?
                        Optional.of(inspector.field(lastRefreshedField).asLong()) :
                        Optional.empty(),
                inspector.field(randomizedIdField).valid() ?
                        Optional.of(inspector.field(randomizedIdField).asString()) :
                        Optional.empty());
    }

    public static EndpointCertificate fromJsonString(String zkData) {
        return fromSlime(SlimeUtils.jsonToSlime(zkData).get());
    }

}
