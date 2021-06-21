// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry.Type;
import com.yahoo.vespa.hosted.controller.deployment.Step;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serialisation of {@link LogEntry} objects. Not all fields are stored!
 *
 * @author jonmv
 */
class LogSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String idField = "id";
    private static final String typeField = "type";
    private static final String timestampField = "at";
    private static final String messageField = "message";

    byte[] toJson(Map<Step, List<LogEntry>> log) {
        try {
            return SlimeUtils.toJsonBytes(toSlime(log));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Slime toSlime(Map<Step, List<LogEntry>> log) {
        Slime root = new Slime();
        Cursor logObject = root.setObject();
        log.forEach((step, entries) -> {
            Cursor recordsArray = logObject.setArray(RunSerializer.valueOf(step));
            entries.forEach(entry -> toSlime(entry, recordsArray.addObject()));
        });
        return root;
    }

    private void toSlime(LogEntry entry, Cursor entryObject) {
        entryObject.setLong(idField, entry.id());
        entryObject.setLong(timestampField, entry.at().toEpochMilli());
        entryObject.setString(typeField, valueOf(entry.type()));
        entryObject.setString(messageField, entry.message());
    }

    Map<Step, List<LogEntry>> fromJson(byte[] logJson, long after) {
        return fromJson(Collections.singletonList(logJson), after);
    }

    Map<Step, List<LogEntry>> fromJson(List<byte[]> logJsons, long after) {
        return fromSlime(logJsons.stream()
                                 .map(SlimeUtils::jsonToSlime)
                                 .collect(Collectors.toList()),
                         after);
    }

    Map<Step, List<LogEntry>> fromSlime(List<Slime> slimes, long after) {
        Map<Step, List<LogEntry>> log = new HashMap<>();
        slimes.forEach(slime -> slime.get().traverse((ObjectTraverser) (stepName, entryArray) -> {
            Step step = RunSerializer.stepOf(stepName);
            List<LogEntry> entries = log.computeIfAbsent(step, __ -> new ArrayList<>());
            entryArray.traverse((ArrayTraverser) (__, entryObject) -> {
                LogEntry entry = fromSlime(entryObject);
                if (entry.id() > after)
                    entries.add(entry);
            });
        }));
        return log;
    }

    private LogEntry fromSlime(Inspector entryObject) {
        return new LogEntry(entryObject.field(idField).asLong(),
                            SlimeUtils.instant(entryObject.field(timestampField)),
                            typeOf(entryObject.field(typeField).asString()),
                            entryObject.field(messageField).asString());
    }

    static String valueOf(Type type) {
        switch (type) {
            case debug: return "debug";
            case info: return "info";
            case warning: return "warning";
            case error: return "error";
            case html: return "html";
            default: throw new AssertionError("Unexpected log entry type '" + type + "'!");
        }
    }

    static Type typeOf(String type) {
        switch (type) {
            case "debug": return Type.debug;
            case "info": return Type.info;
            case "warning": return Type.warning;
            case "error": return Type.error;
            case "html": return Type.html;
            default: throw new IllegalArgumentException("Unknown log entry type '" + type + "'!");
        }
    }

}
