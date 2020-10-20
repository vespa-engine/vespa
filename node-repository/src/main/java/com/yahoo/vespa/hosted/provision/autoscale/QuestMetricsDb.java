// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.io.IOUtils;
import io.questdb.cairo.CairoConfiguration;
import io.questdb.cairo.CairoEngine;
import io.questdb.cairo.DefaultCairoConfiguration;
import io.questdb.cairo.TableWriter;
import io.questdb.cairo.sql.Record;
import io.questdb.cairo.sql.RecordCursor;
import io.questdb.cairo.sql.RecordCursorFactory;
import io.questdb.griffin.SqlCompiler;
import io.questdb.griffin.SqlException;
import io.questdb.griffin.SqlExecutionContext;
import io.questdb.griffin.SqlExecutionContextImpl;
import io.questdb.std.Os;
import io.questdb.std.str.Path;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class QuestMetricsDb implements AutoCloseable {

    private static final String tableName = "metrics";

    private final String dataDir;
    private final CairoEngine engine;

    public QuestMetricsDb() {
        System.setProperty("questdbLog", "etc/quest-log.conf"); // silence Questdb's custom logging system
        dataDir = "data";
        IOUtils.createDirectory(dataDir + "/" + tableName);
        CairoConfiguration configuration = new DefaultCairoConfiguration(dataDir);
        engine = new CairoEngine(configuration);
        ensureExists(tableName);
    }

    @Override
    public void close() {
        if (engine != null)
            engine.close();
    }

    public void addMetrics() {
        try (TableWriter writer = engine.getWriter(newContext().getCairoSecurityContext(), tableName)) {
            for (int i = 0; i < 10; i++) {
                TableWriter.Row row = writer.newRow(Os.currentTimeMicros());
                row.putStr(0, "host" + i);
                row.putTimestamp(1, Instant.now().toEpochMilli());
                row.putFloat(2, i * 1.1F);
                row.putFloat(3, i * 2.2F);
                row.putFloat(4, i * 3.3F);
                row.putFloat(5, i); // really a long, but keep this uniform?
                row.append();
            }
            writer.commit();
        }
    }

    private void ensureExists(String tableName) {
        SqlExecutionContext context = newContext();
        if (0 == engine.getStatus(context.getCairoSecurityContext(), new Path(), tableName)) return;

        try (SqlCompiler compiler = new SqlCompiler(engine)) {
            compiler.compile("create table " + tableName +
                             " (host string, at timestamp, cpu_util float, mem_total_util float, disk_util float, application_generation float)" +
                             " timestamp(at)" +
                             "PARTITION BY DAY;",
                             context);
        }
        catch (SqlException e) {
            throw new IllegalStateException("Could not create Quest db table '" + tableName + "'", e);
        }
    }

    private void readData(String tableName, CairoEngine engine, SqlExecutionContextImpl context) throws SqlException {
        try (SqlCompiler compiler = new SqlCompiler(engine)) {
            try (RecordCursorFactory factory = compiler.compile(tableName, context).getRecordCursorFactory()) {
                try (RecordCursor cursor = factory.getCursor(context)) {
                    Record record = cursor.getRecord();
                    double cpuUtilSum = 0;
                    int rowCount = 0;
                    while (cursor.hasNext()) {
                        cpuUtilSum += record.getFloat(2);
                        rowCount++;
                    }
                }
            }
        }
    }

    private void gc() throws SqlException {
        int maxAgeDays = 3;
        SqlExecutionContext context = newContext();
        try (SqlCompiler compiler = new SqlCompiler(engine)) {
            File tableRoot = new File(dataDir, tableName);
            List<String> removeList = new ArrayList<>();
            for (String dirEntry : tableRoot.list()) {
                File partitionDir = new File(tableRoot, dirEntry);
                if ( ! partitionDir.isDirectory()) continue;
                DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC"));
                Instant partitionDay = Instant.from(formatter.parse(dirEntry + "T00:00:00"));
                if (partitionDay.isBefore(Instant.now().minus(Duration.ofDays(maxAgeDays))))
                    removeList.add(dirEntry);
            }
            compiler.compile("alter table " + tableName + " drop partition " +
                             removeList.stream().map(dir -> "'" + dir + "'").collect(Collectors.joining(",")),
                             context);
        }
    }

    private SqlExecutionContext newContext() {
        return new SqlExecutionContextImpl(engine, 1);
    }

}
