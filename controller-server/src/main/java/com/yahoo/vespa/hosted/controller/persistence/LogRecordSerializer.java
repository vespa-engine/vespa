package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.deployment.Step;

import java.util.List;
import java.util.Map;
import java.util.logging.LogRecord;

/**
 * Serialisation of LogRecord objects. Not all fields are stored!
 *
 * @author jonmv
 */
class LogRecordSerializer {

    private static final String idField = "id";
    private static final String levelField = "level";
    private static final String timestampField = "at";
    private static final String messageField = "message";

    Slime recordsToSlime(Map<Step, List<LogRecord>> stepRecords) {
        Slime root = new Slime();
        Cursor recordsObject = root.setObject();
        stepRecords.forEach((step, records) -> {
            Cursor recordsArray = recordsObject.setArray(RunSerializer.valueOf(step));
            records.forEach(record -> toSlime(record, recordsArray.addObject()));
        });
        return root;
    }

    void toSlime(LogRecord record, Cursor recordObject) {
        recordObject.setLong(idField, record.getSequenceNumber());
        recordObject.setString(levelField, LogLevel.getVespaLogLevel(record.getLevel()).getName());
        recordObject.setLong(timestampField, record.getMillis());
        recordObject.setString(messageField, record.getMessage());
    }

    Map<Step, List<LogRecord>> recordsFromSlime(Slime slime) {
        ImmutableMap.Builder<Step, List<LogRecord>> stepRecords = ImmutableMap.builder();
        slime.get().traverse((ObjectTraverser) (step, recordsArray) -> {
            ImmutableList.Builder<LogRecord> records = ImmutableList.builder();
            recordsArray.traverse((ArrayTraverser) (__, recordObject) -> records.add(fromSlime(recordObject)));
            stepRecords.put(RunSerializer.stepOf(step), records.build());
        });
        return stepRecords.build();
    }

    private LogRecord fromSlime(Inspector recordObject) {
        LogRecord record = new LogRecord(LogLevel.parse(recordObject.field(levelField).asString()),
                                         recordObject.field(messageField).asString());
        record.setSequenceNumber(recordObject.field(idField).asLong());
        record.setMillis(recordObject.field(timestampField).asLong());
        return record;
    }

}
