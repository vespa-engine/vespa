// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.component.annotation.Inject;
import com.yahoo.collections.ListMap;
import com.yahoo.collections.Pair;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.yolean.concurrent.ConcurrentResourcePool;
import io.questdb.cairo.CairoEngine;
import io.questdb.cairo.CairoException;
import io.questdb.cairo.DefaultCairoConfiguration;
import io.questdb.cairo.TableWriter;
import io.questdb.cairo.sql.Record;
import io.questdb.cairo.sql.RecordCursor;
import io.questdb.cairo.sql.RecordCursorFactory;
import io.questdb.griffin.CompiledQuery;
import io.questdb.griffin.QueryFuture;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * An implementation of the metrics Db backed by Quest:
 * This provides local persistent storage of metrics with fast, multi-threaded lookup and write,
 * suitable for production.
 *
 * @author bratseth
 */
public class QuestMetricsDb extends AbstractComponent implements MetricsDb {

    private static final Logger log = Logger.getLogger(QuestMetricsDb.class.getName());

    private final Table nodeTable;
    private final Table clusterTable;

    private final Clock clock;
    private final String dataDir;
    private final CairoEngine engine;
    private final ConcurrentResourcePool<SqlCompiler> sqlCompilerPool;
    private final AtomicBoolean closed = new AtomicBoolean(false);


    @Inject
    public QuestMetricsDb() {
        this(getDefaults().underVespaHome("var/db/vespa/autoscaling"), Clock.systemUTC());
    }

    public QuestMetricsDb(String dataDir, Clock clock) {
        this.clock = clock;

        if (dataDir.startsWith(getDefaults().vespaHome())
            && ! new File(getDefaults().vespaHome()).exists())
            dataDir = "data"; // We're injected, but not on a node with Vespa installed

        // silence Questdb's custom logging system
        String logConfig = dataDir + "/quest-log.conf";
        IOUtils.createDirectory(logConfig);
        IOUtils.writeFile(new File(logConfig), new byte[0]);
        System.setProperty("out", logConfig);

        this.dataDir = dataDir;
        engine = new CairoEngine(new DefaultCairoConfiguration(dataDir));
        sqlCompilerPool = new ConcurrentResourcePool<>(() -> new SqlCompiler(engine()));
        nodeTable = new Table(dataDir, "metrics", clock);
        clusterTable = new Table(dataDir, "clusterMetrics", clock);
        ensureTablesExist();
    }

    private CairoEngine engine() {
        if (closed.get())
            throw new IllegalStateException("Attempted to access QuestDb after calling close");
        return engine;
    }

    @Override
    public Clock clock() { return clock; }

    @Override
    public void addNodeMetrics(Collection<Pair<String, NodeMetricSnapshot>> snapshots) {
        try {
            addNodeMetricsBody(snapshots);
        }
        catch (CairoException e) {
            if (e.getMessage().contains("Cannot read offset")) {
                // This error seems non-recoverable
                nodeTable.repair(e);
                addNodeMetricsBody(snapshots);
            }
        }
    }

    private void addNodeMetricsBody(Collection<Pair<String, NodeMetricSnapshot>> snapshots) {
        synchronized (nodeTable.writeLock) {
            try (TableWriter writer = nodeTable.getWriter()) {
                for (var snapshot : snapshots) {
                    Optional<Long> atMillis = nodeTable.adjustOrDiscard(snapshot.getSecond().at());
                    if (atMillis.isEmpty()) continue;
                    TableWriter.Row row = writer.newRow(atMillis.get() * 1000); // in microseconds
                    row.putStr(0, snapshot.getFirst());
                    // (1 is timestamp)
                    row.putFloat(2, (float) snapshot.getSecond().load().cpu());
                    row.putFloat(3, (float) snapshot.getSecond().load().memory());
                    row.putFloat(4, (float) snapshot.getSecond().load().disk());
                    row.putLong(5, snapshot.getSecond().generation());
                    row.putBool(6, snapshot.getSecond().inService());
                    row.putBool(7, snapshot.getSecond().stable());
                    row.putFloat(8, (float) snapshot.getSecond().queryRate());
                    row.append();
                }
                writer.commit();
            }
        }
    }

    @Override
    public void addClusterMetrics(ApplicationId application, Map<ClusterSpec.Id, ClusterMetricSnapshot> snapshots) {
        try {
            addClusterMetricsBody(application, snapshots);
        }
        catch (CairoException e) {
            if (e.getMessage().contains("Cannot read offset")) {
                // This error seems non-recoverable
                clusterTable.repair(e);
                addClusterMetricsBody(application, snapshots);
            }
        }
    }

    private void addClusterMetricsBody(ApplicationId applicationId, Map<ClusterSpec.Id, ClusterMetricSnapshot> snapshots) {
        synchronized (clusterTable.writeLock) {
            try (TableWriter writer = clusterTable.getWriter()) {
                for (var snapshot : snapshots.entrySet()) {
                    Optional<Long> atMillis = clusterTable.adjustOrDiscard(snapshot.getValue().at());
                    if (atMillis.isEmpty()) continue;
                    TableWriter.Row row = writer.newRow(atMillis.get() * 1000); // in microseconds
                    row.putStr(0, applicationId.serializedForm());
                    row.putStr(1, snapshot.getKey().value());
                    // (2 is timestamp)
                    row.putFloat(3, (float) snapshot.getValue().queryRate());
                    row.putFloat(4, (float) snapshot.getValue().writeRate());
                    row.append();
                }
                writer.commit();
            }
        }
    }

    @Override
    public List<NodeTimeseries> getNodeTimeseries(Duration period, Set<String> hostnames) {
        try {
            var snapshots = getNodeSnapshots(clock.instant().minus(period), hostnames, newContext());
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
        try {
            return getClusterSnapshots(applicationId, clusterId);
        }
        catch (SqlException e) {
            throw new IllegalStateException("Could not read cluster timeseries data in Quest stored in " + dataDir, e);
        }
    }

    @Override
    public void gc() {
        nodeTable.gc();
        clusterTable.gc();
    }

    @Override
    public void deconstruct() { close(); }

    @Override
    public void close() {
        if (closed.getAndSet(true)) return;
        synchronized (nodeTable.writeLock) {
            synchronized (clusterTable.writeLock) {
                for (SqlCompiler sqlCompiler : sqlCompilerPool)
                    sqlCompiler.close();
                engine.close();
            }
        }
    }

    private void ensureTablesExist() {
        if (nodeTable.exists())
            ensureNodeTableIsUpdated();
        else
            createNodeTable();

        if (clusterTable.exists())
            ensureClusterTableIsUpdated();
        else
            createClusterTable();
    }

    private void ensureNodeTableIsUpdated() {
        try {
            // Example: nodeTable.ensureColumnExists("write_rate", "float");
        } catch (Exception e) {
            nodeTable.repair(e);
        }
    }

    private void ensureClusterTableIsUpdated() {
        try {
            if (0 == engine().getStatus(newContext().getCairoSecurityContext(), new Path(), clusterTable.name)) {
                // Example: clusterTable.ensureColumnExists("write_rate", "float");
            }
        } catch (Exception e) {
            clusterTable.repair(e);
        }
    }

    private void createNodeTable() {
        try {
            issue("create table " + nodeTable.name +
                  " (hostname string, at timestamp, cpu_util float, mem_total_util float, disk_util float," +
                  "  application_generation long, inService boolean, stable boolean, queries_rate float)" +
                  " timestamp(at)" +
                  "PARTITION BY DAY;",
                  newContext());
            // We should do this if we get a version where selecting on strings work embedded, see below
            // compiler.compile("alter table " + tableName + " alter column hostname add index", context);
        }
        catch (SqlException e) {
            throw new IllegalStateException("Could not create Quest db table '" + nodeTable.name + "'", e);
        }
    }

    private void createClusterTable() {
        try {
            issue("create table " + clusterTable.name +
                  " (application string, cluster string, at timestamp, queries_rate float, write_rate float)" +
                  " timestamp(at)" +
                  "PARTITION BY DAY;",
                  newContext());
            // We should do this if we get a version where selecting on strings work embedded, see below
            // compiler.compile("alter table " + tableName + " alter column cluster add index", context);
        }
        catch (SqlException e) {
            throw new IllegalStateException("Could not create Quest db table '" + clusterTable.name + "'", e);
        }
    }

    private ListMap<String, NodeMetricSnapshot> getNodeSnapshots(Instant startTime,
                                                                 Set<String> hostnames,
                                                                 SqlExecutionContext context) throws SqlException {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC"));
        String from = formatter.format(startTime).substring(0, 19) + ".000000Z";
        String to = formatter.format(clock.instant()).substring(0, 19) + ".000000Z";
        String sql = "select * from " + nodeTable.name + " where at between('" + from + "', '" + to + "');";

        // WHERE clauses does not work:
        // String sql = "select * from " + tableName + " where hostname in('host1', 'host2', 'host3');";

        try (RecordCursorFactory factory = issue(sql, context).getRecordCursorFactory()) {
            ListMap<String, NodeMetricSnapshot> snapshots = new ListMap<>();
            try (RecordCursor cursor = factory.getCursor(context)) {
                Record record = cursor.getRecord();
                while (cursor.hasNext()) {
                    String hostname = record.getStr(0).toString();
                    if (hostnames.isEmpty() || hostnames.contains(hostname)) {
                        snapshots.put(hostname,
                                      new NodeMetricSnapshot(Instant.ofEpochMilli(record.getTimestamp(1) / 1000),
                                                             new Load(record.getFloat(2),
                                                                      record.getFloat(3),
                                                                      record.getFloat(4)),
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

    private ClusterTimeseries getClusterSnapshots(ApplicationId application, ClusterSpec.Id cluster) throws SqlException {
        String sql = "select * from " + clusterTable.name;
        var context = newContext();
        try (RecordCursorFactory factory = issue(sql, context).getRecordCursorFactory()) {
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

    /** Issues an SQL statement against the QuestDb engine */
    private CompiledQuery issue(String sql, SqlExecutionContext context) throws SqlException {
        SqlCompiler sqlCompiler = sqlCompilerPool.alloc();
        try {
            return sqlCompiler.compile(sql, context);
        } catch (SqlException e) {
            log.log(Level.WARNING, "Could not execute SQL statement '" + sql + "'");
            throw e;
        } finally {
            sqlCompilerPool.free(sqlCompiler);
        }
    }

    /**
     * Issues and wait for an SQL statement to be executed against the QuestDb engine.
     * Needs to be done for some queries, e.g. 'alter table' queries, see https://github.com/questdb/questdb/issues/1846
     */
    private void issueAsync(String sql, SqlExecutionContext context) throws SqlException {
        try (QueryFuture future = issue(sql, context).execute(null)) {
            future.await();
        }
    }

    private SqlExecutionContext newContext() {
        return new SqlExecutionContextImpl(engine(), 1);
    }

    /** A questDb table */
    private class Table {

        private final Object writeLock = new Object();
        private final String name;
        private final Clock clock;
        private final File dir;
        private long highestTimestampAdded = 0;

        Table(String dataDir, String name, Clock clock) {
            this.name = name;
            this.clock = clock;
            this.dir = new File(dataDir, name);
            IOUtils.createDirectory(dir.getPath());
            // https://stackoverflow.com/questions/67785629/what-does-max-txn-txn-inflight-limit-reached-in-questdb-and-how-to-i-avoid-it
            new File(dir + "/_txn_scoreboard").delete();
        }

        boolean exists() {
            return 0 == engine().getStatus(newContext().getCairoSecurityContext(), new Path(), name);
        }

        TableWriter getWriter() {
            return engine().getWriter(newContext().getCairoSecurityContext(), name, "getWriter");
        }

        void gc() {
            synchronized (writeLock) {
                try {
                    issueAsync("alter table " + name + " drop partition where at < dateadd('d', -4, now());", newContext());
                }
                catch (SqlException e) {
                    log.log(Level.WARNING, "Failed to gc old metrics data in " + dir + " table " + name, e);
                }
            }
        }

        /**
         * Repairs this db on corruption.
         *
         * @param e the exception indicating corruption
         */
        private void repair(Exception e) {
            log.log(Level.WARNING, "QuestDb seems corrupted, wiping data and starting over", e);
            IOUtils.recursiveDeleteDir(dir);
            IOUtils.createDirectory(dir.getPath());
            ensureTablesExist();
        }

        void ensureColumnExists(String column, String columnType) throws SqlException {
            if (columnNames().contains(column)) return;
            issueAsync("alter table " + name + " add column " + column + " " + columnType, newContext());
        }

        private Optional<Long> adjustOrDiscard(Instant at) {
            long timestamp = at.toEpochMilli();
            if (timestamp >= highestTimestampAdded) {
                highestTimestampAdded = timestamp;
                return Optional.of(timestamp);
            }

            // We cannot add old data to QuestDb, but we want to use all recent information
            if (timestamp >= highestTimestampAdded - 60 * 1000) return Optional.of(highestTimestampAdded);

            // Too old; discard
            return Optional.empty();
        }

        private List<String> columnNames() throws SqlException {
            var context = newContext();
            List<String> columns = new ArrayList<>();
            try (RecordCursorFactory factory = issue("show columns from " + name, context).getRecordCursorFactory()) {
                try (RecordCursor cursor = factory.getCursor(context)) {
                    Record record = cursor.getRecord();
                    while (cursor.hasNext()) {
                        columns.add(record.getStr(0).toString());
                    }
                }
            }
            return columns;
        }

    }

}
