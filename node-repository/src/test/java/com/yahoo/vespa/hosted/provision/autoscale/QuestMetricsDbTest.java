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
import io.questdb.griffin.SqlExecutionContextImpl;
import io.questdb.std.str.Path;
import org.junit.Test;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * Standalone test of setting up a Quest Db partitioned by days,
 * writing data over the days and then removing old entries.
 *
 * @author bratseth
 */
public class QuestMetricsDbTest {

    private final Instant now = Instant.from(DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC"))
                                       .parse("2020-10-05T00:00:00"));


    @Test
    public void testQuestMetricsDb() throws SqlException {
        System.setProperty("questdbLog", "etc/quest-log.conf"); // silence Questdb's custom logging system
        String dataDir = "data/QuestMetricsDbTest";
        IOUtils.recursiveDeleteDir(new File(dataDir)); // Without this, dropping partitions sometimes fail
        IOUtils.createDirectory(dataDir + "/metrics");
        CairoConfiguration configuration = new DefaultCairoConfiguration(dataDir);
        try (CairoEngine engine = new CairoEngine(configuration)) { // process-wide singleton
            try (SqlCompiler compiler = new SqlCompiler(engine)) {
                SqlExecutionContextImpl context = new SqlExecutionContextImpl(engine, 1); // for single thread
                initDb("metrics", engine, context, compiler);

                assertEquals(0, readRows("metrics", context, compiler));

                writeRows(1000, 10, "metrics", engine, context);
                assertEquals(1000, readRows("metrics", context, compiler));

                deleteData(3, "metrics", dataDir, context, compiler);
                assertEquals(300, readRows("metrics", context, compiler));
            }
        }
    }

    private void initDb(String tableName, CairoEngine engine, SqlExecutionContextImpl context, SqlCompiler compiler) throws SqlException {
        if ( ! exists(tableName, engine, context))
            create(tableName, context, compiler);
        else
            clear(tableName, context, compiler);
    }

    private void writeRows(int rows, int days, String tableName, CairoEngine engine, SqlExecutionContextImpl context) {
        long oldest = now.minus(Duration.ofDays(days)).toEpochMilli();
        long timeStep = (now.toEpochMilli() - oldest) / rows;

        try (TableWriter writer = engine.getWriter(context.getCairoSecurityContext(), tableName)) {
            for (int i = 0; i < rows; i++) {
                long time = oldest + i * timeStep;
                TableWriter.Row row = writer.newRow(time * 1000); // in microseconds
                row.putStr(0, "host" + i);
                row.putTimestamp(1, time);
                row.putFloat(2, i * 1.1F);
                row.putFloat(3, i * 2.2F);
                row.putFloat(4, i * 3.3F);
                row.putFloat(5, i); // really a long, but keep this uniform?
                row.append();
            }
            writer.commit();
        }
    }

    private boolean exists(String tableName, CairoEngine engine, SqlExecutionContextImpl context) {
        return 0 == engine.getStatus(context.getCairoSecurityContext(), new Path(), tableName);
    }

    private void create(String tableName, SqlExecutionContextImpl context, SqlCompiler compiler) throws SqlException {
        compiler.compile("create table " + tableName +
                         " (host string, at timestamp, cpu_util float, mem_total_util float, disk_util float, application_generation float)" +
                         " timestamp(at)" +
                         "PARTITION BY DAY;",
                         context);
    }

    private void clear(String tableName, SqlExecutionContextImpl context, SqlCompiler compiler) throws SqlException {
        compiler.compile("truncate table " + tableName, context);
    }

    private int readRows(String tableName, SqlExecutionContextImpl context, SqlCompiler compiler) throws SqlException {
        try (RecordCursorFactory factory = compiler.compile(tableName, context).getRecordCursorFactory()) {
            try (RecordCursor cursor = factory.getCursor(context)) {
                Record record = cursor.getRecord();
                int rowCount = 0;
                while (cursor.hasNext()) {
                    rowCount++;
                }
                return rowCount;
            }
        }
    }

    private void deleteData(int maxAgeDays, String tableName, String dataDir, SqlExecutionContextImpl context, SqlCompiler compiler) throws SqlException {
        File tableRoot = new File(dataDir, tableName);
        List<String> removeList = new ArrayList<>();
        for (String dirEntry : tableRoot.list()) {
            File partitionDir = new File(tableRoot, dirEntry);
            if ( ! partitionDir.isDirectory()) continue;
            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC"));
            Instant partitionDay = Instant.from(formatter.parse(dirEntry + "T00:00:00"));
            if (partitionDay.isBefore(now.minus(Duration.ofDays(maxAgeDays))))
                removeList.add(dirEntry);
        }
        compiler.compile("alter table " + tableName + " drop partition " +
                         removeList.stream().map(dir -> "'" + dir + "'").collect(Collectors.joining(",")),
                         context);
    }

}
