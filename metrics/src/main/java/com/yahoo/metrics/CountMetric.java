// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

import com.yahoo.metrics.util.MetricValueSet;
import com.yahoo.metrics.util.ValueType;
import com.yahoo.metrics.util.HasCopy;
import com.yahoo.text.Utf8String;
import com.yahoo.text.XMLWriter;

import java.util.Collection;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * A metric that counts something. The value should always be positive.
 */
@SuppressWarnings("unchecked")
public class CountMetric extends Metric {

    public static final int LOG_IF_UNSET = 2;

    private static final Utf8String AVERAGE_CHANGE_PER_SECOND = new Utf8String("average_change_per_second");
    private static final Utf8String COUNT = new Utf8String("count");
    private static final Logger log = Logger.getLogger(CountMetric.class.getName());
    private final MetricValueSet<CountValue> values;
    private int flags;

    public CountMetric(String name, String tags, String description, MetricSet owner) {
        super(name, tags, description, owner);
        values = new MetricValueSet<CountValue>();
        flags = LOG_IF_UNSET;
    }

    public CountMetric(CountMetric other, CopyType copyType, MetricSet owner) {
        super(other, owner);
        values = new MetricValueSet<CountValue>(other.values, copyType == CopyType.CLONE ? other.values.size() : 1);
        flags = other.flags;
    }

    private CountValue getValues() {
        return values.getValue();
    }

    public long getValue() {
        CountValue val = getValues();
        return (val == null ? 0 : val.value);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void logOnlyIfSet() {
        flags &= LOG_IF_UNSET;
    }

    public void set(long value) {
        while (!values.setValue(new CountValue(value))) {
            // try again
        }
    }

    public void inc() {
        inc(1);
    }

    public void dec() {
        dec(1);
    }

    public void inc(long i) {
        boolean overflow;
        CountValue val;
        do {
            val = getValues();
            if (val == null) {
                val = new CountValue(0);
            }
            overflow = (val.value + i < val.value);
            val.value += i;
        } while (!values.setValue(val));

        if (overflow) {
            reset();
            log.fine("Overflow in metric " + getName() + ". Resetting it.");
        }
    }

    public void dec(long i) {
        boolean underflow;
        CountValue val;
        do {
            val = getValues();
            if (val == null) {
                val = new CountValue(0);
            }
            underflow = (val.value - i > val.value);
            val.value -= i;
        } while (!values.setValue(val));

        if (underflow) {
            reset();
            log.fine("Underflow in metric " + getName() + ". Resetting it.");
        }
    }

    @Override
    public void reset() {
        values.reset();
    }

    @Override
    public boolean logFromTotalMetrics() {
        return true;
    }

    @Override
    public void logEvent(EventLogger logger, String fullName) {
        CountValue val = getValues();

        if ((flags & LOG_IF_UNSET) != 0 || val != null) {
            logger.count(fullName, val == null ? 0 : val.value);
        }
    }

    @Override
    public void printXml(XMLWriter writer,
                         int secondsPassed,
                         int verbosity)
    {
        CountValue valRef = getValues();
        if (valRef == null && verbosity < 2) {
            return;
        }
        long val = valRef != null ? valRef.value : 0;
        openXMLTag(writer, verbosity);
        writer.attribute(COUNT, String.valueOf(val));

        if (secondsPassed > 0) {
            writer.attribute(AVERAGE_CHANGE_PER_SECOND,
                             String.format(Locale.US, "%.2f", (double)val / secondsPassed));
        }

        writer.closeTag();
    }

    // Only one metric in valuemetric, so return it on any id.
    @Override
    public long getLongValue(String id) {
        CountValue val = getValues();
        return (val == null ? 0 : val.value);
    }

    @Override
    public double getDoubleValue(String id) {
        CountValue val = getValues();
        return (val == null ? 0 : val.value);
    }

    @Override
    public boolean used() {
        return getValues() != null;
    }

    @Override
    public void addToSnapshot(Metric m) {
        CountValue val = getValues();
        if (val != null) {
            ((CountMetric)m).inc(val.value);
        }
    }

    @Override
    public void addToPart(Metric m) {
        CountValue val = getValues();
        if (val != null) {
            ((CountMetric)m).inc(val.value);
        }
    }

    @Override
    public Metric clone(CopyType type, MetricSet owner, boolean includeUnused) {
        return new CountMetric(this, type, owner);
    }

    private static class CountValue implements ValueType, HasCopy<CountValue> {

        long value;

        private CountValue(long value) {
            this.value = value;
        }

        public CountValue clone() {
            try {
                return (CountValue)super.clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }

        public void add(ValueType other) {
            value += ((CountValue)other).value;
        }

        public ValueType join(Collection<ValueType> sources, JoinBehavior joinBehavior) {
            CountValue result = new CountValue(0);
            for (ValueType t : sources) {
                result.add(t);
            }
            if (joinBehavior == JoinBehavior.AVERAGE_ON_JOIN) {
                result.value /= sources.size();
            }
            return result;
        }

        public String toString() {
            return Long.toString(value);
        }

        public CountValue copyObject() {
            return new CountValue(value);
        }
    }
}
