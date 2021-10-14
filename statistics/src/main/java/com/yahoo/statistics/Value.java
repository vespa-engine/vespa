// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import com.yahoo.container.StatisticsConfig;
import com.yahoo.container.StatisticsConfig.Values.Operations;
import java.util.logging.Level;
import com.yahoo.log.event.Event;
import com.yahoo.statistics.SampleSet.Sampling;

/**
 * A statistical variable, typically representing a sampling of an
 * arbitrarily changing parameter.
 *
 * @author Steinar Knutsen
 */
public class Value extends Handle {

    // For accumulated values, SampleSet instances are mem barriers between {n
    // sampling threads} and {logging thread}.

    // lastValue is a memory barrier between {n sampling threads} and {n
    // sampling threads, logging thread}.

    // Therefore, the logging thread first locks SampleDirectory.directoryLock,
    // then locks each SampleSet, one by one. The sampling threads _either_ lock
    // a single SampleSet _or_ lock SampleDirectory.directoryLock.

    // It is necessary to create a memory relationship between the logging
    // threads to ensure the newest sample ends up in the log for logRaw = true.

    private final ThreadLocal<SampleSet> sample = new ThreadLocal<>();
    private final SampleDirectory directory = new SampleDirectory();

    // This must _only_ be touched if logRaw is true
    private volatile double lastValue = 0.0d;

    private final boolean logRaw;
    private final boolean logMean;
    private final boolean logSum;
    private final boolean logInsertions;
    private final boolean logMax;
    private final boolean logMin;
    private final boolean logHistogram;

    private final Limits histogram;
    private final boolean nameExtension;
    final HistogramType histogramId;
    private final char appendChar;

    private static final Logger log = Logger.getLogger(Value.class.getName());
    static final String HISTOGRAM_TYPE_WARNING = "Histogram types other than REGULAR currently not supported."
            +" Reverting to regular histogram for statistics event";

    /**
     * Parameters for building Value instances. All settings are classes instead
     * of primitive types to allow tri-state logic (true, false, unset).
     */
    public static class Parameters {
        /**
         * Log raw values. Raw values are basically the last value logged.
         */
        Boolean logRaw;
        /**
         * Log the sum of all data points for each interval.
         */
        Boolean logSum;
        /**
         * Log the mean value for each interval.
         */
        Boolean logMean;
        /**
         * Log the maximal value observed for each interval.
         */
        Boolean logMax;
        /**
         * Log the minimal value observed for each interval.
         */
        Boolean logMin;
        /**
         * Log the number of observations for each interval.
         */
        Boolean logInsertions;
        /**
         * Whether or not to add an identifying extension (like mean) to event
         * names when logging derived values.
         *
         * It is useful to disable extensions if a only a single dervied value,
         * e.g. the mean, is the only thing to be logged. The default is to use
         * extensions.
         *
         * If extensions are disabled, the ability to log more than one of raw
         * value, min, max, mean (i.e. the raw value and derived values of the
         * same type) is disabled to avoid confusion. Since histograms are not
         * Value events, these never have a name extension and are always
         * available.
         */
        Boolean nameExtension;
        /**
         * Log a data histogram.
         */
        Boolean logHistogram;
        /**
         * What kind of histogram to log.
         *
         * @see HistogramType
         */
        HistogramType histogramId;
        /**
         * The limits to use if logging as a histogram. The Limits instance must
         * be frozen before using the Parameters instance in a Value constructor
         * call.
         *
         * @see Limits
         */
        Limits limits;
        /**
         * Separator character to use between event name and type of
         * nameExtension is set to true.
         */
        Character appendChar;

        /**
         * This is invoked each time a value is dumped to the log.
         *
         * @see Handle#runCallback()
         */
        Callback callback;

        /**
         * Whether to register in the Statistics manager. This is not touched by
         * merge and also has no undefined state. In general, a Value should
         * always register and not doing so explicitly should not be part of the
         * public API.
         */
        private boolean register = true;

        /**
         * Get a fresh Parameters instance with all features turned/unset.
         * Parameters instances may be recycled for construction multiple Value
         * instances, but do note any Limits instance added must be frozen.
         */
        public Parameters() {
        }

        /**
         * (De-)Activate logging of raw values. Raw values are basically the
         * last value logged.
         *
         * @return "this" for call chaining
         */
        public Parameters setLogRaw(Boolean logRaw) {
            this.logRaw = logRaw;
            return this;
        }

        /**
         * (De-)Activate logging the sum of all data points for each interval.
         *
         * @return "this" for call chaining
         */
        public Parameters setLogSum(Boolean logSum) {
            this.logSum = logSum;
            return this;
        }

        /**
         * (De)-activate loging the mean value for each interval.
         *
         * @return "this" for call chaining
         */
        public Parameters setLogMean(Boolean logMean) {
            this.logMean = logMean;
            return this;
        }

        /**
         * (De-)Activate logging the maximal value observed for each interval.
         *
         * @return "this" for call chaining
         */
        public Parameters setLogMax(Boolean logMax) {
            this.logMax = logMax;
            return this;
        }

        /**
         * (De-)Activate logging the minimal value observed for each interval.
         *
         * @return "this" for call chaining
         */
        public Parameters setLogMin(Boolean logMin) {
            this.logMin = logMin;
            return this;
        }

        /**
         * (De-)Activate loging the number of observations for each interval.
         *
         * @return "this" for call chaining
         */
        public Parameters setLogInsertions(Boolean logInsertions) {
            this.logInsertions = logInsertions;
            return this;
        }

        /**
         * Whether or not to add an identifying extension (like mean) to event
         * names when logging derived values.
         *
         * It is useful to disable extensions if a only a single dervied value,
         * e.g. the mean, is the only thing to be logged. The default is to use
         * extensions.
         *
         * If extensions are disabled, the ability to log more than one of raw
         * value, min, max, mean (i.e. the raw value and derived values of the
         * same type) is disabled to avoid confusion. Since histograms are not
         * Value events, these never have a name extension and are always
         * available.
         *
         * @return "this" for call chaining
         */
        public Parameters setNameExtension(Boolean nameExtension) {
            this.nameExtension = nameExtension;
            return this;
        }

        /**
         * (De-)Activate logging a data histogram.
         *
         * @return "this" for call chaining
         */
        public Parameters setLogHistogram(Boolean logHistogram) {
            this.logHistogram = logHistogram;
            return this;
        }

        /**
         * What kind of histogram to log.
         *
         * @see HistogramType
         *
         * @return "this" for call chaining
         */
        public Parameters setHistogramId(HistogramType histogramId) {
            this.histogramId = histogramId;
            return this;
        }

        /**
         * The limits to use if logging as a histogram. The Limits instance must
         * be frozen before using the Parameters instance in a Value constructor
         * call.
         *
         * @return "this" for call chaining*
         * @see Limits
         */
        public Parameters setLimits(Limits limits) {
            this.limits = limits;
            return this;
        }

        /**
         * Separator character to use between event name and type of
         * nameExtension is set to true. The default is '.'.
         *
         * @return "this" for call chaining
         */
        public Parameters setAppendChar(Character appendChar) {
            this.appendChar = appendChar;
            return this;
        }

        /**
         * Set a callback to be invoked each time this Value is written to the
         * log.
         *
         * @param callback
         *            to be invoked each time the Value is written to the log
         * @return "this" for call chaining
         */
        public Parameters setCallback(Callback callback) {
            this.callback = callback;
            return this;
        }

        /**
         * Set whether to register in the statistics manager. Do note the
         * package private access for this method opposed to all the other
         * setters.
         *
         * @param register
         *            set to false to avoid registering
         * @return "this" for call chaining
         */
        private Parameters setRegister(boolean register) {
            this.register = register;
            return this;
        }

        /**
         * If a member is not set in this, add it from defaults. Do note, this
         * applies for both true and false settings, in other words, the default
         * may also be used to turn off something if it is undefined in this.
         */
        void merge(Parameters defaults) {
            if (defaults == null) {
                return;
            }
            this.logRaw = this.logRaw == null ? defaults.logRaw : this.logRaw;
            this.logSum = this.logSum == null ? defaults.logSum : this.logSum;
            this.logMean = this.logMean == null ? defaults.logMean
                    : this.logMean;
            this.logMax = this.logMax == null ? defaults.logMax : this.logMax;
            this.logMin = this.logMin == null ? defaults.logMin : this.logMin;
            this.logInsertions = this.logInsertions == null ? defaults.logInsertions
                    : this.logInsertions;
            this.nameExtension = this.nameExtension == null ? defaults.nameExtension
                    : this.nameExtension;
            this.logHistogram = this.logHistogram == null ? defaults.logHistogram
                    : this.logHistogram;
            this.histogramId = this.histogramId == null ? defaults.histogramId
                    : this.histogramId;
            this.limits = this.limits == null ? defaults.limits : this.limits;
            this.appendChar = this.appendChar == null ? defaults.appendChar
                    : this.appendChar;
            this.callback = this.callback == null ? defaults.callback
                    : this.callback;
        }

    }

    /**
     * Configure a Value instance fully, no raw config access.
     *
     * @param name
     *            tag for logging
     * @param manager
     *            the statistics manager acquired by injection
     * @param parameters
     *            all the parameters necessary for initializing a Value
     * @throws IllegalStateException
     *             if Parameters.limits exists and is not frozen
     */
    public Value(String name, Statistics manager, Parameters parameters) {
        super(name, manager, parameters.callback);
        this.logHistogram = isTrue(parameters.logHistogram);
        this.logMax = isTrue(parameters.logMax);
        this.logMean = isTrue(parameters.logMean);
        this.logMin = isTrue(parameters.logMin);
        this.logRaw = isTrue(parameters.logRaw);
        this.logSum = isTrue(parameters.logSum);
        this.logInsertions = isTrue(parameters.logInsertions);
        this.nameExtension = isTrue(parameters.nameExtension);
        if (logHistogram) {
            if (!parameters.limits.isFrozen()) {
                throw new IllegalStateException("The Limits instance must be frozen.");
            }
            if (parameters.histogramId != HistogramType.REGULAR) {
                log.log(Level.WARNING, HISTOGRAM_TYPE_WARNING + " '" + name + "'");
            }
            this.histogramId = HistogramType.REGULAR;
            this.histogram = parameters.limits;
        } else {
            this.histogram = null;
            this.histogramId = HistogramType.REGULAR;
        }
        Character appendChar = parameters.appendChar;
        if (appendChar == null) {
            this.appendChar = '.';
        } else {
            this.appendChar = appendChar.charValue();
        }
        if (parameters.register) {
            manager.register(this);
        }
    }

    private static boolean isTrue(Boolean b) {
        return b != null && b.booleanValue();
    }

    /**
     * Build a Value which should be initialized from config.
     *
     * @param name
     *            the name of the event in the log
     * @param manager
     *            the current Statistics manager, acquired by injection
     * @param defaults
     *            defaults for values not defined by config, this may be null
     */
    public static Value buildValue(String name, Statistics manager, Parameters defaults) {
        return new Value(name, manager, buildParameters(name, manager, defaults));
    }

    /**
     * Get a Value instance not registered in the statistics manager. This is
     * used by ValueGroup and should not be made public.
     *
     * @param name
     *            The name of this counter, for use in logging.
     * @param parameters
     *            setting for the new Value
     */
    static Value initializeUnregisteredValue(String name, Parameters parameters) {
        return new Value(name, null, parameters.setRegister(false));
    }

    private static Parameters buildParameters(String name, Statistics manager, Parameters defaults) {
        Parameters p = null;
        StatisticsConfig config = manager.getConfig();
        if (config != null) {
            for (int i = 0; i < config.values().size(); i++) {
                String configName = config.values(i).name();
                if (configName.equals(name)) {
                    p = parametersFromConfig(config.values(i).operations());
                    break;
                }
            }
        }
        if (p == null) {
            if (defaults == null) {
                p = defaultParameters();
            } else {
                p = defaults;
            }
        } else {
            p.merge(defaults);
        }
        return p;
    }

    static Parameters defaultParameters() {
        return new Parameters().setLogRaw(true).setNameExtension(true);
    }

    private static Parameters parametersFromConfig(List<Operations> o) {
        Parameters p = new Parameters().setNameExtension(true);

        for (Operations operation : o) {
            Operations.Name.Enum opName = operation.name();

            HashMap<String, String> args = new HashMap<>();
            for (int j = 0; j < operation.arguments().size(); j++) {
                args.put(operation.arguments(j).key(), operation.arguments(j).value());
            }

            if (opName == Operations.Name.MEAN) {
                p.setLogMean(true);
            } else if (opName == Operations.Name.MAX) {
                p.setLogMax(true);
            } else if (opName == Operations.Name.MIN) {
                p.setLogMin(true);
            } else if (opName == Operations.Name.RAW) {
                p.setLogRaw(true);
            } else if (opName == Operations.Name.SUM) {
                p.setLogSum(true);
            } else if (opName == Operations.Name.INSERTIONS) {
                p.setLogInsertions(true);
            } else if (opName == Operations.Name.REGULAR) {
                p.setLogHistogram(true);
                p.setHistogramId(HistogramType.REGULAR);
                p.setLimits(initHistogram(args.get("axes"), args.get("limits")));
            } else if (opName == Operations.Name.CUMULATIVE) {
                p.setLogHistogram(true);
                p.setHistogramId(HistogramType.CUMULATIVE);
                p.setLimits(initHistogram(args.get("axes"), args.get("limits")));
            } else if (opName == Operations.Name.REVERSE_CUMULATIVE) {
                p.setLogHistogram(true);
                p.setHistogramId(HistogramType.REVERSE_CUMULATIVE);
                p.setLimits(initHistogram(args.get("axes"),args.get("limits")));
            }
        }
        return p;
    }

    private static Limits initHistogram(String axes, String limits) {
        String[] borders;
        double[] vanillaLimits;
        Limits l = new Limits();
        int i = 0;

        if (axes != null) {
            throw new RuntimeException("Config of multidimensional histograms not yet implemented.");
        }
        if (limits == null) {
            throw new RuntimeException("Config of histograms needs a list of limits.");
        }
        borders = limits.split(",");
        vanillaLimits = new double[borders.length];
        while (i < vanillaLimits.length) {
            vanillaLimits[i] = Double.parseDouble(borders[i].trim());
            ++i;
        }
        l.addAxis(null, vanillaLimits);
        l.freeze();
        return l;
    }

    private SampleSet getSample() {
        SampleSet s = sample.get();
        if (s == null) {
            s = new SampleSet(histogram);
            sample.set(s);
        }
        return s;
    }

    private void putComposite(double x) {
        SampleSet s = getSample();
        boolean isInDir = s.put(x);
        if (!isInDir) {
            directory.put(s);
        }
    }

    /**
     * Insert x, do all pertinent operations. (Update histogram, update
     * insertion count for calculating mean, etc.)
     */
    public void put(double x) {
        if (logComposite()) {
            putComposite(x);
        }
        if (logRaw) {
            lastValue = x;
        }
    }

    private boolean logComposite() {
        return logMean || logMin || logMax || logSum || logInsertions || logHistogram;
    }

    /**
     * Get mean value since last reset.
     */
    public double getMean() {
        Sampling[] values = directory.viewValues();
        long insertions = 0L;
        double sum = 0.0d;
        for (Sampling x : values) {
            insertions += x.insertions;
            sum += x.sum;
        }
        if (insertions == 0) {
            return 0.0d;
        }
        return sum/insertions;
    }

    /**
     * Get minimal value logged since last reset.
     */
    public double getMin() {
        Sampling[] values = directory.viewValues();
        long insertions = 0L;
        double min = 0.0d;
        for (Sampling x : values) {
            if (x.insertions == 0) {
                continue;
            }
            if (insertions == 0) {
                min = x.min;
            } else {
                min = Math.min(x.min, min);
            }
            insertions += x.insertions;
        }
        return min;
    }

    /**
     * Get maximum value logged since last reset.
     */
    public double getMax() {
        Sampling[] values = directory.viewValues();
        long insertions = 0L;
        double max = 0.0d;
        for (Sampling x : values) {
            if (x.insertions == 0) {
                continue;
            }
            if (insertions == 0) {
                max = x.max;
            } else {
                max = Math.max(x.max, max);
            }
            insertions += x.insertions;
        }
        return max;
    }

    private Histogram getHistogram() {
        if (histogram == null) {
            return null;
        } else {
            Sampling[] values = directory.viewValues();
            Histogram merged = new Histogram(histogram);
            for (Sampling s : values) {
                merged.merge(s.histogram);
            }
            return merged;
        }
    }

    /**
     * Get last value logged, 0 if nothing logged since reset.
     */
    public double get() {
        return lastValue;
    }

    /**
     * Set last value logged container to 0, reset histogram and set all
     * counters and derived statistics to 0.
     */
    public void reset() {
        if (logComposite()) {
            directory.fetchValues();
        }
        if (logRaw) {
            lastValue = 0.0d;
        }
    }

    /**
     * Dump state to log and reset.
     */
    @Override
    public void runHandle() {
        String rawImage = null;
        String meanImage = null;
        String minImage = null;
        String maxImage = null;
        String sumImage = null;
        String insertionsImage = null;
        String histImage = null;
        String lastHist = null;
        String histType = null;
        Snapshot now = getCurrentState();

        if (nameExtension) {
            if (logRaw) {
                rawImage = getName();
            }
            if (logMean) {
                meanImage = getName() + appendChar + "mean";
            }
            if (logMin) {
                minImage = getName() + appendChar + "min";
            }
            if (logMax) {
                maxImage = getName() + appendChar + "max";
            }
            if (logSum) {
                sumImage = getName() + appendChar + "sum";
            }
            if (logInsertions) {
                insertionsImage = getName() + appendChar + "insertions";
            }
        } else {
            if (logRaw) {
                rawImage = getName();
            } else if (logMean) {
                meanImage = getName();
            } else if (logMin) {
                minImage = getName();
            } else if (logMax) {
                maxImage = getName();
            } else if (logSum) {
                sumImage = getName();
            } else if (logInsertions) {
                insertionsImage = getName();
            }
        }
        if (logHistogram) {
            histImage = getName();
            lastHist = now.histogram.toString();
            histType = histogramId.toString();
        }
        if (rawImage != null) {
            Event.value(rawImage, now.raw);
        }
        if (meanImage != null) {
            Event.value(meanImage, now.mean);
        }
        if (minImage != null) {
            Event.value(minImage, now.min);
        }
        if (maxImage != null) {
            Event.value(maxImage, now.max);
        }
        if (histImage != null) {
            Event.histogram(histImage, lastHist, histType);
        }
        if (sumImage != null) {
            Event.value(sumImage, now.sum);
        }
        if (insertionsImage != null) {
            Event.value(insertionsImage, now.insertions);
        }
    }

    public String toString() {
        if (histogram == null) {
            return super.toString() + " " + getName();
        } else {
            return super.toString() + " " + getName() + " " + getHistogram().toString();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o.getClass() != this.getClass()) {
            return false;
        }
        Value other = (Value) o;
        return getName().equals(other.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode() + 31 * "Value".hashCode();
    }

    static class Snapshot {
        double insertions;
        double max;
        double min;
        double mean;
        double sum;
        double raw;
        Histogram histogram = null;

        Snapshot insertions(double lastInsertions) {
            this.insertions = lastInsertions;
            return this;
        }

        Snapshot max(double lastMax) {
            this.max = lastMax;
            return this;
        }

        Snapshot min(double lastMin) {
            this.min = lastMin;
            return this;
        }

        Snapshot mean(double lastMean) {
            this.mean = lastMean;
            return this;
        }

        Snapshot sum(double lastSum) {
            this.sum = lastSum;
            return this;
        }

        Snapshot raw(double lastRaw) {
            this.raw = lastRaw;
            return this;
        }

        Snapshot histogram(Histogram mergedHistogram) {
            this.histogram = mergedHistogram;
            return this;
        }
    }

    private Snapshot getCurrentState() {
        double lastInsertions = 0L;
        double lastMax = 0.0d;
        double lastMin = 0.0d;
        double lastMean = 0.0d;
        double lastSum = 0.0d;
        double lastRaw = 0.0d;
        Histogram mergedHistogram = null;

        if (logRaw) {
            lastRaw = lastValue;
        }
        if (logComposite()) {
            Sampling[] lastInterval = directory.fetchValues();
            if (histogram != null) {
                mergedHistogram = new Histogram(histogram);
            }
            for (Sampling threadData : lastInterval) {
                if (threadData.insertions == 0) {
                    continue;
                }
                if (lastInsertions == 0L) {
                    lastMax = threadData.max;
                    lastMin = threadData.min;
                } else {
                    lastMax = Math.max(threadData.max, lastMax);
                    lastMin = Math.min(threadData.min, lastMin);
                }
                lastSum += threadData.sum;
                if (mergedHistogram != null) {
                    mergedHistogram.merge(threadData.histogram);
                }
                lastInsertions += threadData.insertions;
            }
            if (lastInsertions == 0L) {
                lastMean = 0.0d;
            } else {
                lastMean = lastSum / lastInsertions;
            }
        }
        return new Snapshot().insertions(lastInsertions)
                .max(lastMax).mean(lastMean).min(lastMin)
                .raw(lastRaw).sum(lastSum)
                .histogram(mergedHistogram);
    }

    ValueProxy getProxyAndReset() {
        ValueProxy p = new ValueProxy(getName());
        Snapshot now = getCurrentState();

        if (logRaw) {
            p.setRaw(now.raw);
        }
        if (logMean) {
            p.setMean(now.mean);
        }
        if (logMin) {
            p.setMin(now.min);
        }
        if (logMax) {
            p.setMax(now.max);
        }
        if (logHistogram) {
            p.setHistogram(now.histogram);
        }

        return p;
    }
}

