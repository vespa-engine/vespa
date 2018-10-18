// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

import com.yahoo.collections.Pair;
import com.yahoo.text.XMLWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

/**
 * A metrics-enabled application should have a single MetricManager. You can register a number of MetricSets in the
 * MetricManager. Each metric in the metrics sets can be used by zero or more consumers, configurable using
 * readConfig().
 *
 * The consumers get their data by calling the getMetrics() method, which gives them a snapshot of all the current
 * metrics which are configured for the given name.
 *
 * Locking strategy:
 *
 * There are three locks in this class:
 *
 * Config lock: - This protects the class on config changes. It protects the _config and _consumerConfig members.
 *
 * Thread monitor (waiter): - This lock is kept by the worker thread while it is doing a work cycle, and it uses this
 * monitor to sleep. It is used to make shutdown quick by interrupting thread, and to let functions called by clients be
 * able to do a change while the worker thread is idle. - The log period is protected by the thread monitor. - The
 * update hooks is protected by the thread monitor.
 *
 * Metric lock: - The metric log protects the active metric set when adding or removing metrics. Clients need to grab
 * this lock before altering active metrics. The metric manager needs to grab this lock everytime it visits active
 * metrics. - The metric log protects the snapshots. The snapshot writer is the metric worker thread and will grab the
 * lock while editing them. Readers that aren't the worker thread itself must grab lock to be sure.
 *
 * If multiple locks is taken, the allowed locking order is: 1. Thread monitor. 2. Metric lock. 3. Config lock.
 */
public class MetricManager implements Runnable {

    private static final int STATE_CREATED = 0;
    private static final int STATE_RUNNING = 1;
    private static final int STATE_STOPPED = 2;
    private static final Logger log = Logger.getLogger(MetricManager.class.getName());
    private final CountDownLatch termination = new CountDownLatch(1);
    private final MetricSnapshot activeMetrics = new MetricSnapshot("Active metrics showing updates since " +
                                                                    "last snapshot");
    private final Map<String, ConsumerSpec> consumerConfig = new HashMap<>();
    private final List<UpdateHook> periodicUpdateHooks = new ArrayList<>();
    private final List<UpdateHook> snapshotUpdateHooks = new ArrayList<>();
    private final Timer timer;
    private Pair<Integer, Integer> logPeriod;
    private List<MetricSnapshotSet> snapshots = new ArrayList<>();
    private MetricSnapshot totalMetrics = new MetricSnapshot("Empty metrics before init", 0,
                                                             activeMetrics.getMetrics(), false);
    private int state = STATE_CREATED;
    private int lastProcessedTime = 0;
    private boolean forceEventLogging = false;
    private boolean snapshotUnsetMetrics = false; // TODO: add to config

    public MetricManager() {
        this(new Timer());
    }

    MetricManager(Timer timer) {
        this.timer = timer;
        initializeSnapshots();
        logPeriod = new Pair<>(snapshots.get(0).getPeriod(), 0);
    }

    void initializeSnapshots() {
        int currentTime = timer.secs();

        List<Pair<Integer, String>> snapshotPeriods = new ArrayList<>();
        snapshotPeriods.add(new Pair<>(60 * 5, "5 minute"));
        snapshotPeriods.add(new Pair<>(60 * 60, "1 hour"));
        snapshotPeriods.add(new Pair<>(60 * 60 * 24, "1 day"));
        snapshotPeriods.add(new Pair<>(60 * 60 * 24 * 7, "1 week"));

        int count = 1;
        for (int i = 0; i < snapshotPeriods.size(); ++i) {
            int nextCount = 1;
            if (i + 1 < snapshotPeriods.size()) {
                nextCount = snapshotPeriods.get(i + 1).getFirst()
                            / snapshotPeriods.get(i).getFirst();
                if (snapshotPeriods.get(i + 1).getFirst() % snapshotPeriods.get(i).getFirst() != 0) {
                    throw new IllegalStateException("Snapshot periods must be multiplum of each other");
                }
            }
            snapshots.add(new MetricSnapshotSet(snapshotPeriods.get(i).getSecond(),
                                                snapshotPeriods.get(i).getFirst(),
                                                count,
                                                activeMetrics.getMetrics(),
                                                snapshotUnsetMetrics));
            count = nextCount;
        }
        // Add all time snapshot.
        totalMetrics = new MetricSnapshot("All time snapshot",
                                          0, activeMetrics.getMetrics(),
                                          snapshotUnsetMetrics);
        totalMetrics.reset(currentTime);
    }

    public void stop() {
        synchronized (this) {
            int prevState = state;
            state = STATE_STOPPED;
            if (prevState == STATE_CREATED) {
                return;
            }
            notifyAll();
        }
        try {
            termination.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    void setSnapshotUnsetMetrics(boolean value) {
        snapshotUnsetMetrics = value;
    }

    /**
     * Add a metric update hook. This will always be called prior to snapshotting and metric logging, to make the
     * metrics the best as they can be at those occasions.
     *
     * @param hook   The hook to add.
     * @param period Period in seconds for how often callback should be called. The default value of 0, means only
     *               before snapshotting or logging, while another value will give callbacks each period seconds.
     *               Expensive metrics to calculate will typically only want to do it before snapshotting, while
     *               inexpensive metrics might want to log their value every 5 seconds or so. Any value of period &gt;= the
     *               smallest snapshot time will behave identically as if period is set to 0.
     */
    @SuppressWarnings("UnusedDeclaration")
    public synchronized void addMetricUpdateHook(UpdateHook hook, int period) {
        hook.period = period;

        // If we've already initialized manager, log period has been set.
        // In this case. Call first time after period
        hook.nextCall = (logPeriod.getSecond() == 0 ? 0 : timer.secs() + period);
        if (period == 0) {
            if (!snapshotUpdateHooks.contains(hook)) {
                snapshotUpdateHooks.add(hook);
            }
        } else {
            if (!periodicUpdateHooks.contains(hook)) {
                periodicUpdateHooks.add(hook);
            }
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public synchronized void removeMetricUpdateHook(UpdateHook hook) {
        if (hook.period == 0) {
            snapshotUpdateHooks.remove(hook);
        } else {
            periodicUpdateHooks.remove(hook);
        }
    }

    /**
     * Force a metric update for all update hooks. Useful if you want to ensure nice values before reporting something.
     * This function can not be called from an update hook callback.
     *
     * @param includeSnapshotOnlyHooks True to also run snapshot hooks.
     */
    @SuppressWarnings("UnusedDeclaration")
    public synchronized void updateMetrics(boolean includeSnapshotOnlyHooks) {
        log.fine("Giving " + periodicUpdateHooks.size() + " periodic update hooks.");

        updatePeriodicMetrics(0, true);

        if (includeSnapshotOnlyHooks) {
            log.fine("Giving " + snapshotUpdateHooks.size() + " snapshot update hooks.");
            updateSnapshotMetrics();
        }
    }

    /**
     * Force event logging to happen now. This function can not be called from an update hook callback.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void forceEventLogging() {
        log.fine("Forcing event logging to happen.");
        // Ensure background thread is not in a current cycle during change.

        synchronized (this) {
            forceEventLogging = true;
            this.notifyAll();
        }
    }

    /**
     * Register a new metric to be included in the active metric set. You need to have grabbed the metric lock in order
     * to do this. (You also need to grab that lock if you alter registration of already registered metric set.) This
     * function can not be called from an update hook callback.
     *
     * @param m The metric to register.
     */
    public void registerMetric(Metric m) {
        activeMetrics.getMetrics().registerMetric(m);
    }

    /**
     * Unregister a metric from the active metric set. You need to have grabbed the metric lock in order to do this.
     * (You also need to grab that lock if you alter registration of already registered metric set.) This function can
     * not be called from an update hook callback.
     *
     * @param m The Metric to unregister.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void unregisterMetric(Metric m) {
        activeMetrics.getMetrics().unregisterMetric(m);
    }

    /**
     * Reset all metrics including all snapshots. This function can not be called from an update hook callback.
     *
     * @param currentTime The current time.
     */
    public synchronized void reset(int currentTime) {
        activeMetrics.reset(currentTime);

        for (MetricSnapshotSet m : snapshots) {
            m.reset(currentTime);
        }
        totalMetrics.reset(currentTime);
    }

    /**
     * Read configuration. Before reading config, all metrics should be set up first. By doing this, the metrics manager
     * can optimize reporting of consumers. readConfig() will start a config subscription. It should not be called
     * multiple times.
     */
/*    public synchronized void init(String configId, ThreadPool pool)  {
        log.fine("Initializing metric manager")

    LOG(debug, "Initializing metric manager.");
    _configSubscription = Config::subscribe(configId, *this);
    LOG(debug, "Starting worker thread, waiting for first "
               "iteration to complete.");
    Runnable::start(pool);
        // Wait for first iteration to have completed, such that it is safe
        // to access snapshots afterwards.
    vespalib::MonitorGuard sync(_waiter);
    while (_lastProcessedTime == 0) {
        sync.wait(1);
    }
    LOG(debug, "Metric manager completed initialization.");
}

*/

    class ConsumerMetricVisitor extends MetricVisitor {

        ConsumerSpec metricsToMatch;
        MetricVisitor clientVisitor;

        ConsumerMetricVisitor(ConsumerSpec spec,
                              MetricVisitor clientVisitor)
        {
            metricsToMatch = spec;
            this.clientVisitor = clientVisitor;
            log.fine("Consuming metrics: " + spec.includedMetrics);
        }

        public boolean visitMetricSet(MetricSet metricSet, boolean autoGenerated) {
            if (metricSet.getOwner() == null) {
                return true;
            }

            if (!metricsToMatch.contains(metricSet)) {
                log.fine("Metric doesn't match " + metricSet.getPath());
                return false;
            }

            return clientVisitor.visitMetricSet(metricSet, autoGenerated);
        }

        public void doneVisitingMetricSet(MetricSet metricSet) {
            if (metricSet.getOwner() != null) {
                clientVisitor.doneVisitingMetricSet(metricSet);
            }
        }

        public boolean visitPrimitiveMetric(Metric metric, boolean autoGenerated) {
            if (metricsToMatch.contains(metric)) {
                return clientVisitor.visitPrimitiveMetric(metric, autoGenerated);
            } else {
                log.fine("Metric doesn't match " + metric.getPath());
            }
            return true;
        }
    }

    public synchronized void visit(MetricSet metrics, MetricVisitor visitor, String consumer) {
        if (consumer.isEmpty()) {
            metrics.visit(visitor, false);
            return;
        }

        ConsumerSpec spec = getConsumerSpec(consumer);

        if (spec != null) {
            ConsumerMetricVisitor consumerVis = new ConsumerMetricVisitor(spec, visitor);
            metrics.visit(consumerVis, false);
        } else {
            log.warning("Requested metrics for non-defined consumer " + consumer);
        }
    }

    class XmlWriterMetricVisitor extends MetricVisitor {

        int period;
        XMLWriter writer;
        int verbosity;

        XmlWriterMetricVisitor(XMLWriter writer, int period, int verbosity) {
            this.period = period;
            this.verbosity = verbosity;
            this.writer = writer;
        }

        public boolean visitMetricSet(MetricSet set, boolean autoGenerated) {
            if (set.used() || verbosity >= 2) {
                set.openXMLTag(writer, verbosity);
                return true;
            }
            return false;
        }

        public void doneVisitingMetricSet(MetricSet set) {
            writer.closeTag();
        }

        public boolean visitPrimitiveMetric(Metric metric, boolean autoGenerated) {
            metric.printXml(writer, period, verbosity);
            return true;
        }
    }

    void printXml(MetricSet set, XMLWriter writer, int period, String consumer, int verbosity) {
        visit(set, new XmlWriterMetricVisitor(writer, period, verbosity), consumer);
    }

    /**
     * Synchronize over this while the returned object
     *
     * @return The MetricSnapshot of all active metrics.
     */
    public MetricSnapshot getActiveMetrics() {
        return activeMetrics;
    }

    /**
     * Synchronize over this while the returned object
     *
     * @return The MetricSnapshot for the total metric.
     */
    public MetricSnapshot getTotalMetricSnapshot() {
        return totalMetrics;
    }

    public synchronized List<Integer> getSnapshotPeriods() {
        List<Integer> retVal = new ArrayList<Integer>();

        for (MetricSnapshotSet m : snapshots) {
            retVal.add(m.getPeriod());
        }
        return retVal;
    }

    /**
     * While accessing snapshots you should synchronize over this
     *
     * @param period           The id of the snapshot period to access.
     * @param getInProgressSet True to retrieve the snapshot currently being built.
     * @return The appropriate MetricSnapshot.
     */
    MetricSnapshot getMetricSnapshot(int period, boolean getInProgressSet) {
        return getMetricSnapshotSet(period).getSnapshot(getInProgressSet);
    }

    MetricSnapshot getMetricSnapshot(int period) {
        return getMetricSnapshot(period, false);
    }

    public MetricSnapshotSet getMetricSnapshotSet(int period) {
        for (MetricSnapshotSet m : snapshots) {
            if (m.getPeriod() == period) {
                return m;
            }
        }

        throw new IllegalArgumentException("No snapshot for period of length " + period + " exists.");
    }

    @SuppressWarnings("UnusedDeclaration")
    public synchronized boolean hasTemporarySnapshot(int period) {
        return getMetricSnapshotSet(period).hasTemporarySnapshot();
    }

    public synchronized void addMetricToConsumer(String consumerName, String metricPath) {
        ConsumerSpec spec = getConsumerSpec(consumerName);
        if (spec == null) {
            spec = new ConsumerSpec();
            consumerConfig.put(consumerName, spec);
        }
        spec.register(metricPath);
    }

    public synchronized ConsumerSpec getConsumerSpec(String consumer) {
        return consumerConfig.get(consumer);
    }

    /**
     * If you join or remove metrics from the active metric sets, normally, snapshots will be recreated next snapshot
     * period. However, if you want to see the effects of such changes in status pages ahead of that, you can call this
     * function in order to check whether snapshots needs to be regenerated and regenerate them if needed.
     */
    public synchronized void checkMetricsAltered() {
        if (activeMetrics.getMetrics().isRegistrationAltered()) {
            handleMetricsAltered();
        }
    }

    /**
     * Used by unit tests to verify that we have processed for a given time.
     *
     * @return Returns the timestamp of the previous tick.
     */
    @SuppressWarnings("UnusedDeclaration")
    public int getLastProcessedTime() {
        return lastProcessedTime;
    }

    class LogMetricVisitor extends MetricVisitor {

        boolean total;
        EventLogger logger;

        LogMetricVisitor(boolean totalVals, EventLogger logger) {
            total = totalVals;
            this.logger = logger;
        }

        public boolean visitPrimitiveMetric(Metric metric, boolean autoGenerated) {
            if (metric.logFromTotalMetrics() == total) {
                String logName = metric.getPath().replace('.', '_');
                metric.logEvent(logger, logName);
            }
            return true;
        }
    }

    public void logTotal(int currentTime, EventLogger logger) {
        LogMetricVisitor totalVisitor = new LogMetricVisitor(true, logger);
        LogMetricVisitor fiveMinVisitor = new LogMetricVisitor(false, logger);

        if (logPeriod.getSecond() <= currentTime) {
            log.fine("Logging total metrics.");
            visit(totalMetrics.getMetrics(), totalVisitor, "log");
            visit(snapshots.get(0).getSnapshot().getMetrics(), fiveMinVisitor, "log");
            if (logPeriod.getSecond() + logPeriod.getFirst() < currentTime) {
                logPeriod = new Pair<Integer, Integer>(logPeriod.getFirst(),
                                                       snapshots.get(0).getFromTime() + logPeriod.getFirst());
            } else {
                logPeriod =
                        new Pair<Integer, Integer>(logPeriod.getFirst(), logPeriod.getSecond() + logPeriod.getFirst());
            }
        }
    }

    public void logOutOfSequence(int currentTime, EventLogger logger) {
        LogMetricVisitor totalVisitor = new LogMetricVisitor(true, logger);
        LogMetricVisitor fiveMinVisitor = new LogMetricVisitor(false, logger);

        log.fine("Logging total metrics out of sequence.");
        MetricSnapshot snapshot = new MetricSnapshot(
                "Total out of sequence metrics from start until current time",
                0,
                totalMetrics.getMetrics(),
                snapshotUnsetMetrics);

        activeMetrics.addToSnapshot(snapshot, currentTime, false);
        snapshot.setFromTime(totalMetrics.getFromTime());
        visit(snapshot.getMetrics(), totalVisitor, "log");
        visit(snapshot.getMetrics(), fiveMinVisitor, "log");
    }

    /**
     * Runs one iteration of the thread activity.
     *
     * @param logger An event logger to use for any new events generated.
     * @return The number of milliseconds to sleep until waking up again
     */
    public synchronized int tick(EventLogger logger) {
        int currentTime = timer.secs();

        log.finest("Worker thread starting to process for time " + currentTime);

        boolean firstIteration = (logPeriod.getSecond() == 0);
        // For a slow system to still be doing metrics tasks each n'th
        // second, rather than each n'th + time to do something seconds,
        // we constantly join next time to do something from the last timer.
        // For that to work, we need to initialize timers on first iteration
        // to set them to current time.
        if (firstIteration) {
            // Setting next log period to now, such that we log metrics
            // straight away
            logPeriod = new Pair<Integer, Integer>(logPeriod.getFirst(), currentTime);
            for (MetricSnapshotSet m : snapshots) {
                m.setFromTime(currentTime);
            }
            for (UpdateHook h : periodicUpdateHooks) {
                h.nextCall = currentTime;
            }
        }

        // If metrics have changed since last time we did a snapshot,
        // work that out before taking the snapshot, such that new
        // metric can be included
        checkMetricsAltered();

        // Set next work time to the time we want to take next snapshot.
        int nextWorkTime = snapshots.get(0).getPeriod() + snapshots.get(0).getFromTime();

        int nextUpdateHookTime;

        if (nextWorkTime <= currentTime) {
            log.fine("Time to do snapshot. Calling update hooks");
            nextUpdateHookTime = updatePeriodicMetrics(currentTime, true);
            updateSnapshotMetrics();
            takeSnapshots(nextWorkTime);
        } else if (forceEventLogging) {
            log.fine("Out of sequence event logging. Calling update hooks");
            nextUpdateHookTime = updatePeriodicMetrics(currentTime, true);
            updateSnapshotMetrics();
        } else {
            // If not taking a new snapshot. Only give update hooks to
            // periodic hooks wanting it.
            nextUpdateHookTime = updatePeriodicMetrics(currentTime, false);
        }

        // Log if it is time
        if (logPeriod.getSecond() <= currentTime || forceEventLogging) {
            logTotal(currentTime, logger);
        } else {
            logOutOfSequence(currentTime, logger);
        }

        forceEventLogging = false;
        lastProcessedTime = (nextWorkTime <= currentTime ? nextWorkTime : currentTime);
        log.fine("Worker thread done with processing for time " + lastProcessedTime);

        int next = Math.min(logPeriod.getSecond(), snapshots.get(0).getPeriod() + snapshots.get(0).getFromTime());
        next = Math.min(next, nextUpdateHookTime);
        if (currentTime < next) {
            return (next - currentTime) * 1000;
        }

        return 0;
    }

    @Override
    public synchronized void run() {
        if (state != STATE_CREATED) {
            throw new IllegalStateException();
        }
        try {
            for (state = STATE_RUNNING; state == STATE_RUNNING; ) {
                int timeout = tick(new VespaLogEventLogger());
                if (timeout > 0) {
                    wait(timeout);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            termination.countDown();
        }
    }

    public synchronized void takeSnapshots(int timeToProcess) {
        // If not time to do dump data from active snapshot yet, nothing to do
        if (!snapshots.get(0).timeForAnotherSnapshot(timeToProcess)) {
            return;
        }

        log.fine("Updating " + snapshots.get(0).getName() + " snapshot from active metrics");
        //  int fromTime = snapshots.get(0).getSnapshot().getToTime();
        MetricSnapshot firstTarget = (snapshots.get(0).getNextTarget());
        firstTarget.reset(timeToProcess);
        activeMetrics.addToSnapshot(firstTarget, timeToProcess, false);
        log.fine("Updating total metrics with five minute period of active metrics");
        activeMetrics.addToSnapshot(totalMetrics, timeToProcess, false);
        activeMetrics.reset(timeToProcess);

        for (int i = 1; i < snapshots.size(); ++i) {
            MetricSnapshotSet s = snapshots.get(i);

            log.fine("Adding data from last snapshot to building snapshot of " +
                     "next period snapshot " + s.getName());

            MetricSnapshot target = s.getNextTarget();
            snapshots.get(i - 1).getSnapshot().addToSnapshot(target, timeToProcess, false);
            target.setToTime(timeToProcess);

            if (!snapshots.get(i).haveCompletedNewPeriod(timeToProcess)) {
                log.fine("Not time to roll snapshot " + s.getName() + " yet. " +
                         s.getBuilderCount() + " of " + s.getCount() + " snapshot " +
                         "taken at time" + (s.getBuilderCount() * s.getPeriod() + s.getFromTime()) +
                         ", and period of " + s.getPeriod() + " is not up " +
                         "yet as we're currently processing for time " + timeToProcess);
                break;
            } else {
                log.fine("Rolled snapshot " + s.getName() + " at time " + timeToProcess);
            }
        }
    }

    /**
     * Utility function for updating periodic metrics.
     *
     * @param updateTime    Update metrics timed to update at this time.
     * @param outOfSchedule Force calls to all hooks. Don't screw up normal schedule though. If not time to update yet,
     *                      update without adjusting schedule for next update.
     * @return Time of next hook to be called in the future.
     */
    int updatePeriodicMetrics(int updateTime, boolean outOfSchedule) {
        int nextUpdateTime = Integer.MAX_VALUE;
        for (UpdateHook h : periodicUpdateHooks) {
            if (h.nextCall <= updateTime) {
                h.updateMetrics();
                if (h.nextCall + h.period < updateTime) {
                    h.nextCall = updateTime + h.period;
                } else {
                    h.nextCall += h.period;
                }
            } else if (outOfSchedule) {
                h.updateMetrics();
            }
            nextUpdateTime = Math.min(nextUpdateTime, h.nextCall);
        }
        return nextUpdateTime;
    }

    void updateSnapshotMetrics() {
        for (UpdateHook h : snapshotUpdateHooks) {
            h.updateMetrics();
        }
    }

    synchronized void handleMetricsAltered() {
/*    if (consumerConfig.isEmpty()) {
        log.fine("Setting up consumers for the first time.");
    } else {
       log.info("Metrics registration changes detected. Handling changes.");
    }

        Map<String, ConsumerSpec> configMap = new HashMap<String, ConsumerSpec>();
        activeMetrics.getMetrics().clearRegistrationAltered();


    for (<config::MetricsmanagerConfig::Consumer>::const_iterator it
            = _config.consumer.begin(); it != _config.consumer.end(); ++it)
    {
        ConsumerMetricBuilder consumerMetricBuilder(*it);
        _activeMetrics.getMetrics().visit(consumerMetricBuilder);
        configMap[it->name] = ConsumerSpec::SP(
                new ConsumerSpec(consumerMetricBuilder._matchedMetrics));
    }
    _consumerConfig.swap(configMap);
    */
        log.fine("Recreating snapshots to include altered metrics");
        totalMetrics.recreateSnapshot(activeMetrics.getMetrics(), snapshotUnsetMetrics);

        for (MetricSnapshotSet set : snapshots) {
            set.recreateSnapshot(activeMetrics.getMetrics(), snapshotUnsetMetrics);
        }
    }

    abstract class UpdateHook {

        String name;
        int nextCall;
        int period;

        public UpdateHook(String name) {
            this.name = name;
            nextCall = 0;
            period = 0;
        }

        public abstract void updateMetrics();

        public String getName() {
            return name;
        }
    }
}
