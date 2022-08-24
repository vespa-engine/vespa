// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLog;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Slime serializer for {@link AuditLog}.
 *
 * @author mpolden
 */
public class AuditLogSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String entriesField = "entries";
    private static final String atField = "at";
    private static final String principalField = "principal";
    private static final String methodField = "method";
    private static final String resourceField = "resource";
    private static final String dataField = "data";
    private static final String clientField = "client";

    public Slime toSlime(AuditLog log) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor entryArray = root.setArray(entriesField);
        log.entries().forEach(entry -> {
            Cursor entryObject = entryArray.addObject();
            entryObject.setLong(atField, entry.at().toEpochMilli());
            entryObject.setString(clientField, asString(entry.client()));
            entryObject.setString(principalField, entry.principal());
            entryObject.setString(methodField, asString(entry.method()));
            entryObject.setString(resourceField, entry.resource());
            entry.data().ifPresent(data -> entryObject.setString(dataField, data));
        });
        return slime;
    }
    public AuditLog fromSlime(Slime slime) {
        List<AuditLog.Entry> entries = new ArrayList<>();
        Cursor root = slime.get();
        root.field(entriesField).traverse((ArrayTraverser) (i, entryObject) -> {
            entries.add(new AuditLog.Entry(
                    SlimeUtils.instant(entryObject.field(atField)),
                    SlimeUtils.optionalString(entryObject.field(clientField))
                              .map(AuditLogSerializer::clientFrom)
                              .orElse(AuditLog.Entry.Client.other),
                    entryObject.field(principalField).asString(),
                    methodFrom(entryObject.field(methodField)),
                    entryObject.field(resourceField).asString(),
                    SlimeUtils.optionalString(entryObject.field(dataField))
                              .map(s -> s.getBytes(StandardCharsets.UTF_8))
                              .orElseGet(() -> new byte[0])
            ));
        });
        return new AuditLog(entries);
    }

    private static String asString(AuditLog.Entry.Method method) {
        return switch (method) {
            case POST -> "POST";
            case PATCH -> "PATCH";
            case PUT -> "PUT";
            case DELETE -> "DELETE";
        };
    }

    private static AuditLog.Entry.Method methodFrom(Inspector field) {
        return switch (field.asString()) {
            case "POST" -> AuditLog.Entry.Method.POST;
            case "PATCH" -> AuditLog.Entry.Method.PATCH;
            case "PUT" -> AuditLog.Entry.Method.PUT;
            case "DELETE" -> AuditLog.Entry.Method.DELETE;
            default -> throw new IllegalArgumentException("Unknown serialized value '" + field.asString() + "'");
        };
    }

    private static String asString(AuditLog.Entry.Client client) {
        return switch (client) {
            case console -> "console";
            case cli -> "cli";
            case hv -> "hv";
            case other -> "other";
        };
    }

    private static AuditLog.Entry.Client clientFrom(String s) {
        return switch (s) {
            case "console" -> AuditLog.Entry.Client.console;
            case "cli" -> AuditLog.Entry.Client.cli;
            case "hv" -> AuditLog.Entry.Client.hv;
            case "other" -> AuditLog.Entry.Client.other;
            default -> throw new IllegalArgumentException("Unknown serialized value '" + s + "'");
        };
    }

}
