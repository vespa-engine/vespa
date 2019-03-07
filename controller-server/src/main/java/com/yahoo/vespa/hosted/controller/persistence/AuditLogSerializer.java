// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLog;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Slime serializer for the audit log.
 *
 * @author mpolden
 */
public class AuditLogSerializer {

    private static final String entriesField = "entries";
    private static final String atField = "at";
    private static final String principalField = "principal";
    private static final String methodField = "method";
    private static final String urlField = "url";
    private static final String dataField = "data";

    public Slime toSlime(AuditLog log) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor entryArray = root.setArray(entriesField);
        log.entries().forEach(entry -> {
            Cursor entryObject = entryArray.addObject();
            entryObject.setLong(atField, entry.at().toEpochMilli());
            entryObject.setString(principalField, entry.principal());
            entryObject.setString(methodField, asString(entry.method()));
            entryObject.setString(urlField, entry.url().toString());
            entry.data().ifPresent(data -> entryObject.setString(dataField, data));
        });
        return slime;
    }

    public AuditLog fromSlime(Slime slime) {
        List<AuditLog.Entry> entries = new ArrayList<>();
        Cursor root = slime.get();
        root.field(entriesField).traverse((ArrayTraverser) (i, entryObject) -> {
            entries.add(new AuditLog.Entry(
                    Instant.ofEpochMilli(entryObject.field(atField).asLong()),
                    entryObject.field(principalField).asString(),
                    methodFrom(entryObject.field(methodField)),
                    URI.create(entryObject.field(urlField).asString()),
                    optionalField(entryObject.field(dataField), Function.identity())
            ));
        });
        return new AuditLog(entries);
    }

    private static String asString(AuditLog.Entry.Method method) {
        switch (method) {
            case POST: return "POST";
            case PATCH: return "PATCH";
            case DELETE: return "DELETE";
            default: throw new IllegalArgumentException("No serialization defined for method " + method);
        }
    }

    private static AuditLog.Entry.Method methodFrom(Inspector field) {
        switch (field.asString()) {
            case "POST": return AuditLog.Entry.Method.POST;
            case "PATCH": return AuditLog.Entry.Method.PATCH;
            case "DELETE": return AuditLog.Entry.Method.DELETE;
            default: throw new IllegalArgumentException("Unknown serialized value '" + field.asString() + "'");
        }
    }

    private static <T> Optional<T> optionalField(Inspector field, Function<String, T> fieldMapper) {
        return Optional.of(field).filter(Inspector::valid).map(Inspector::asString).map(fieldMapper);
    }

}
