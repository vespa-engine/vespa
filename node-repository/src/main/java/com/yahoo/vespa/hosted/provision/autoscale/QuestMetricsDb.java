// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.google.inject.Inject;
import com.yahoo.collections.ListMap;
import com.yahoo.collections.Pair;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.defaults.Defaults;
import io.questdb.cairo.CairoConfiguration;
import io.questdb.cairo.CairoEngine;
import io.questdb.cairo.CairoException;
import io.questdb.cairo.DefaultCairoConfiguration;
import io.questdb.cairo.TableWriter;
import io.questdb.cairo.sql.Record;
import io.questdb.cairo.sql.RecordCursor;
import io.questdb.cairo.sql.RecordCursorFactory;
import io.questdb.griffin.SqlCompiler;
import io.questdb.griffin.SqlException;
import io.questdb.griffin.SqlExecutionContext;
import io.questdb.griffin.SqlExecutionContextImpl;
import io.questdb.std.str.Path;

import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * An implementation of the metrics Db backed by Quest:
 * This provides local persistent storage of metrics with fast, multi-threaded lookup and write,
 * suitable for production.
 *
 * @author bratseth
 */
public class QuestMetricsDb extends AbstractComponent implements MetricsDb {

    private static final Logger log = Logger.getLogger(QuestMetricsDb.class.getName());
    private static final String nodeTable    = "metrics";
    private static final String clusterTable = "clusterMetrics";

    private final Clock clock;
    private final String dataDir;
    private CairoEngine engine;

    private long highestTimestampAdded = 0;

    @Inject
    public QuestMetricsDb() {
        this(Defaults.getDefaults().underVespaHome("var/db/vespa/autoscaling"), Clock.systemUTC());
    }

    public QuestMetricsDb(String dataDir, Clock clock) {
        this.clock = clock;

        if (dataDir.startsWith(Defaults.getDefaults().vespaHome())
            && ! new File(Defaults.getDefaults().vespaHome()).exists())
            dataDir = "data"; // We're injected, but not on a node with Vespa installed
        this.dataDir = dataDir;
        initializeDb();
    }

    private void initializeDb() {
        IOUtils.createDirectory(dataDir + "/" + nodeTable);
        IOUtils.createDirectory(dataDir + "/" + clusterTable);

        // silence Questdb's custom logging system
        IOUtils.writeFile(new File(dataDir, "quest-log.conf"), new byte[0]);
        System.setProperty("questdbLog", dataDir + "/quest-log.conf");
        System.setProperty("org.jooq.no-logo", "true");

        CairoConfiguration configuration = new DefaultCairoConfiguration(dataDir);
        engine = new CairoEngine(configuration);
        ensureTablesExist();
    }

    @Override
    public Clock clock() { return clock; }

    @Override
    public void addNodeMetrics(Collection<Pair<String, NodeMetricSnapshot>> snapshots) {
        try (TableWriter writer = engine.getWriter(newContext().getCairoSecurityContext(), nodeTable)) {
            addNodeMetrics(snapshots, writer);
        }
        catch (CairoException e) {
            if (e.getMessage().contains("Cannot read offset")) {
                // This error seems non-recoverable
                repair(e);
                try (TableWriter writer = engine.getWriter(newContext().getCairoSecurityContext(), nodeTable)) {
                    addNodeMetrics(snapshots, writer);
                }
            }
        }
    }

    private void addNodeMetrics(Collection<Pair<String, NodeMetricSnapshot>> snapshots, TableWriter writer) {
        for (var snapshot : snapshots) {
            long atMillis = adjustIfRecent(snapshot.getSecond().at().toEpochMilli(), highestTimestampAdded);
            if (atMillis < highestTimestampAdded) continue; // Ignore old data
            highestTimestampAdded = atMillis;
            TableWriter.Row row = writer.newRow(atMillis * 1000); // in microseconds
            row.putStr(0, snapshot.getFirst());
            // (1 is timestamp)
            row.putFloat(2, (float)snapshot.getSecond().cpu());
            row.putFloat(3, (float)snapshot.getSecond().memory());
            row.putFloat(4, (float)snapshot.getSecond().disk());
            row.putLong(5, snapshot.getSecond().generation());
            row.putBool(6, snapshot.getSecond().inService());
            row.putBool(7, snapshot.getSecond().stable());
            row.putFloat(8, (float)snapshot.getSecond().queryRate());
            row.append();
        }
        writer.commit();
    }

    @Override
    public void addClusterMetrics(ApplicationId application, Map<ClusterSpec.Id, ClusterMetricSnapshot> snapshots) {
        try (TableWriter writer = engine.getWriter(newContext().getCairoSecurityContext(), clusterTable)) {
            addClusterMetrics(application, snapshots, writer);
        }
        catch (CairoException e) {
            if (e.getMessage().contains("Cannot read offset")) {
                // This error seems non-recoverable
                repair(e);
                try (TableWriter writer = engine.getWriter(newContext().getCairoSecurityContext(), clusterTable)) {
                    addClusterMetrics(application, snapshots, writer);
                }
            }
        }
    }

    private void addClusterMetrics(ApplicationId applicationId, Map<ClusterSpec.Id, ClusterMetricSnapshot> snapshots, TableWriter writer) {
        for (var snapshot : snapshots.entrySet()) {
            long atMillis = adjustIfRecent(snapshot.getValue().at().toEpochMilli(), highestTimestampAdded);
            if (atMillis < highestTimestampAdded) continue; // Ignore old data
            highestTimestampAdded = atMillis;
            TableWriter.Row row = writer.newRow(atMillis * 1000); // in microseconds
            row.putStr(0, applicationId.serializedForm());
            row.putStr(1, snapshot.getKey().value());
            // (2 is timestamp)
            row.putFloat(3, (float)snapshot.getValue().queryRate());
            row.putFloat(4, (float)snapshot.getValue().writeRate());
            row.append();
        }
        writer.commit();
    }

    @Override
    public List<NodeTimeseries> getNodeTimeseries(Duration period, Set<String> hostnames) {
        try (SqlCompiler compiler = new SqlCompiler(engine)) {
            SqlExecutionContext context = newContext();
            var snapshots = getNodeSnapshots(clock.instant().minus(period), hostnames, compiler, context);
            return snapshots.entrySet().stream()
                            .map(entry -> new NodeTimeseries(entry.getKey(), entry.getValue()))
                            .collect(Collectors.toList());
        }
        catch (SqlException e) {
            throw new IllegalStateException("Could not read node timeseries data in Quest stored in " + dataDir, e);
        }
    }

    @Override
    public ClusterTimeseries getClusterTimeseries(ApplicationId applicationId, ClusterSpec.Id clusterId) {
        try (SqlCompiler compiler = new SqlCompiler(engine)) {
            SqlExecutionContext context = newContext();
            return getClusterSnapshots(applicationId, clusterId, compiler, context);
        }
        catch (SqlException e) {
            throw new IllegalStateException("Could not read cluster timeseries data in Quest stored in " + dataDir, e);
        }
    }

    @Override
    public void gc() {
        gc(nodeTable);
        gc(clusterTable);
    }

    private void gc(String table) {
        // We remove full days at once and we want to see at least three days to not every only see weekend data
        Instant oldestToKeep = clock.instant().minus(Duration.ofDays(4));
        SqlExecutionContext context = newContext();
        int partitions = 0;
        try (SqlCompiler compiler = new SqlCompiler(engine)) {
            File tableRoot = new File(dataDir, table);
            List<String> removeList = new ArrayList<>();
            for (String dirEntry : tableRoot.list()) {
                File partitionDir = new File(tableRoot, dirEntry);
                if ( ! partitionDir.isDirectory()) continue;

                partitions++;
                DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC"));
                Instant partitionDay = Instant.from(formatter.parse(dirEntry + "T00:00:00"));
                if (partitionDay.isBefore(oldestToKeep))
                    removeList.add(dirEntry);

            }
            // Remove unless all partitions are old: Removing all partitions "will be supported in the future"
            if ( removeList.size() < partitions && ! removeList.isEmpty()) {
                compiler.compile("alter table " + table + " drop partition list " +
                                 removeList.stream().map(dir -> "'" + dir + "'").collect(Collectors.joining(",")),
                                 context);
            }
        }
        catch (SqlException e) {
            log.log(Level.WARNING, "Failed to gc old metrics data in " + dataDir + " table " + table, e);
        }
    }

    @Override
    public void deconstruct() { close(); }

    @Override
    public void close() {
        if (engine != null)
            engine.close();
    }

    /**
     * Repairs this db on corruption.
     *
     * @param e the exception indicating corruption
     */
    private void repair(Exception e) {
        log.log(Level.WARNING, "QuestDb seems corrupted, wiping data and starting over", e);
        IOUtils.recursiveDeleteDir(new File(dataDir));
        initializeDb();
    }

    private boolean exists(String table, SqlExecutionContext context) {
        return 0 == engine.getStatus(context.getCairoSecurityContext(), new Path(), table);
    }

    private void ensureTablesExist() {
        SqlExecutionContext context = newContext();
        if (exists(nodeTable, context))
            ensureNodeTableIsUpdated(context);
        else
            createNodeTable(context);

        if (exists(clusterTable, context))
            ensureClusterTableIsUpdated(context);
        else
            createClusterTable(context);
    }

    private void createNodeTable(SqlExecutionContext context) {
        try (SqlCompiler compiler = new SqlCompiler(engine)) {
            compiler.compile("create table " + nodeTable +
                             " (hostname string, at timestamp, cpu_util float, mem_total_util float, disk_util float," +
                             "  application_generation long, inService boolean, stable boolean, queries_rate float)" +
                             " timestamp(at)" +
                             "PARTITION BY DAY;",
                             context);
            // We should do this if we get a version where selecting on strings work embedded, see below
            // compiler.compile("alter table " + tableName + " alter column hostname add index", context);
        }
        catch (SqlException e) {
            throw new IllegalStateException("Could not create Quest db table '" + nodeTable + "'", e);
        }
    }

    private void createClusterTable(SqlExecutionContext context) {
        try (SqlCompiler compiler = new SqlCompiler(engine)) {
            compiler.compile("create table " + clusterTable +
                             " (application string, cluster string, at timestamp, queries_rate float, write_rate float)" +
                             " timestamp(at)" +
                             "PARTITION BY DAY;",
                             context);
            // We should do this if we get a version where selecting on strings work embedded, see below
            // compiler.compile("alter table " + tableName + " alter column cluster add index", context);
        }
        catch (SqlException e) {
            throw new IllegalStateException("Could not create Quest db table '" + clusterTable + "'", e);
        }
    }

    private void ensureNodeTableIsUpdated(SqlExecutionContext context) {
        try (SqlCompiler compiler = new SqlCompiler(engine)) {
            if (0 == engine.getStatus(context.getCairoSecurityContext(), new Path(), nodeTable)) {
                ensureColumnExists("queries_rate", "float", nodeTable, compiler, context); // TODO: Remove after March 2021
            }
        } catch (SqlException e) {
            repair(e);
        }
    }

    private void ensureClusterTableIsUpdated(SqlExecutionContext context) {
        try (SqlCompiler compiler = new SqlCompiler(engine)) {
            if (0 == engine.getStatus(context.getCairoSecurityContext(), new Path(), nodeTable)) {
                ensureColumnExists("write_rate", "float", nodeTable, compiler, context); // TODO: Remove after March 2021
            }
        } catch (SqlException e) {
            repair(e);
        }
    }

    private void ensureColumnExists(String column, String columnType,
                                    String table, SqlCompiler compiler, SqlExecutionContext context) throws SqlException {
        if (columnNamesOf(table, compiler, context).contains(column)) return;
        compiler.compile("alter table " + table + " add column " + column + " " + columnType, context);
    }

    private List<String> columnNamesOf(String tableName, SqlCompiler compiler, SqlExecutionContext context) throws SqlException {
        List<String> columns = new ArrayList<>();
        try (RecordCursorFactory factory = compiler.compile("show columns from " + tableName, context).getRecordCursorFactory()) {
            try (RecordCursor cursor = factory.getCursor(context)) {
                Record record = cursor.getRecord();
                while (cursor.hasNext()) {
                    columns.add(record.getStr(0).toString());
                }
            }
        }
        return columns;
    }

    private long adjustIfRecent(long timestamp, long highestTimestampAdded) {
        if (timestamp >= highestTimestampAdded) return timestamp;

        // We cannot add old data to QuestDb, but we want to use all recent information
        long oneMinute = 60 * 1000;
        if (timestamp >= highestTimestampAdded - oneMinute) return highestTimestampAdded;

        // Too old; discard
        return timestamp;
    }

    private ListMap<String, NodeMetricSnapshot> getNodeSnapshots(Instant startTime,
                                                                 Set<String> hostnames,
                                                                 SqlCompiler compiler,
                                                                 SqlExecutionContext context) throws SqlException {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC"));
        String from = formatter.format(startTime).substring(0, 19) + ".000000Z";
        String to = formatter.format(clock.instant()).substring(0, 19) + ".000000Z";
        String sql = "select * from " + nodeTable + " where at in('" + from + "', '" + to + "');";

        // WHERE clauses does not work:
        // String sql = "select * from " + tableName + " where hostname in('host1', 'host2', 'host3');";

        try (RecordCursorFactory factory = compiler.compile(sql, context).getRecordCursorFactory()) {
            ListMap<String, NodeMetricSnapshot> snapshots = new ListMap<>();
            try (RecordCursor cursor = factory.getCursor(context)) {
                Record record = cursor.getRecord();
                while (cursor.hasNext()) {
                    String hostname = record.getStr(0).toString();
                    if (hostnames.contains(hostname)) {
                        snapshots.put(hostname,
                                      new NodeMetricSnapshot(Instant.ofEpochMilli(record.getTimestamp(1) / 1000),
                                                             record.getFloat(2),
                                                             record.getFloat(3),
                                                             record.getFloat(4),
                                                             record.getLong(5),
                                                             record.getBool(6),
                                                             record.getBool(7),
                                                             record.getFloat(8)));
                    }
                }
            }
            return snapshots;
        }
    }

    private ClusterTimeseries getClusterSnapshots(ApplicationId application,
                                                  ClusterSpec.Id cluster,
                                                  SqlCompiler compiler,
                                                  SqlExecutionContext context) throws SqlException {
        String sql = "select * from " + clusterTable;
        try (RecordCursorFactory factory = compiler.compile(sql, context).getRecordCursorFactory()) {
            List<ClusterMetricSnapshot> snapshots = new ArrayList<>();
            try (RecordCursor cursor = factory.getCursor(context)) {
                Record record = cursor.getRecord();
                while (cursor.hasNext()) {
                    String applicationIdString = record.getStr(0).toString();
                    if ( ! application.serializedForm().equals(applicationIdString)) continue;
                    String clusterId = record.getStr(1).toString();
                    if (cluster.value().equals(clusterId)) {
                        snapshots.add(new ClusterMetricSnapshot(Instant.ofEpochMilli(record.getTimestamp(2) / 1000),
                                                                record.getFloat(3),
                                                                record.getFloat(4)));
                    }
                }
            }
            return new ClusterTimeseries(cluster, snapshots);
        }
    }

    private SqlExecutionContext newContext() {
        return new SqlExecutionContextImpl(engine, 1);
    }

}
