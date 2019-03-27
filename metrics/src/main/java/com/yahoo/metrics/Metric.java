// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

import com.yahoo.text.XMLWriter;
import com.yahoo.text.Utf8String;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public abstract class Metric {

    private String name;
    private String tags;
    private String description;

    public String getXMLTag() {
        return getName();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    MetricSet owner;

    public Metric(String name, String tags, String description) {
        this(name, tags, description, null);
    }

    public Metric(String name, String tags, String description, MetricSet owner) {
        this.name = name;
        this.tags = tags;
        this.description = description;

        if (owner != null) {
            owner.registerMetric(this);
        }
    }

    public Metric(Metric other, MetricSet owner) {
        this(other.name, other.tags, other.description, owner);
    }

    public String getName() { return name; }

    public String getPath() {
        if (owner == null || owner.owner == null) {
            return getName();
        }

        return owner.getPath() + "." + getName();
    }

    public List<String> getPathVector() {
        List<String> result = new ArrayList<>();
        result.add(getName());
        MetricSet owner = this.owner;
        while (owner != null) {
            result.add(0, owner.getName());
            owner = owner.owner;
        }
        return result;
    }

    public String getDescription() { return description; }

    public String[] getTagVector()  {
        return getTags().split("[ \r\t\f]");
    }

    /**
     * Returns true if the given tag exists in this metric's tag list.
     *
     * @return true if tag exists in tag list
     */
    public boolean hasTag(String tag) {
        for (String s : getTagVector()) {
            if (s.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    public enum CopyType { CLONE, INACTIVE }

    /**
     * The clone function will clone metrics to an identical subtree of
     * metrics. Clone is primarily used for load metrics that wants to clone
     * a template metric for each loadtype. But it should work generically.
     *
     * @param type If set to inactive, sum metrics will evaluate to primitives
     *             and metrics can save memory by knowing no updates are coming.
     * @param includeUnused When creating snapshots we do not want to include
     *             unused metrics, but while generating sum metric sum in active
     *             metrics we want to. This has no affect if type is CLONE.
     */
    public abstract Metric clone(CopyType type, MetricSet owner, boolean includeUnused);

    /**
     * Utility function for assigning values from one metric of identical type
     * to this metric. For simplicity sake it does a const cast and calls
     * addToSnapshot, which should not alter source if reset is false. This can
     * not be used to copy between active metrics and inactive copies.
     *
     * @return Returns itself.
     */
    public Metric assignValues(Metric m) {
        m.addToSnapshot(this);
            // As this should only be called among active metrics, all metrics
            // should exist and owner list should thus always end up empty.
        return this;
    }

    /** Reset all metric values. */
    public abstract void reset();

    public boolean logFromTotalMetrics() { return false; }

    /** Implement to make metric able to log event.
     *
     * @param logger An event logger to use for logging.
     * @param fullName The name to use for the event.
     */
    public abstract void logEvent(EventLogger logger, String fullName);

    public static final Utf8String TAG_NAME = new Utf8String("name");
    public static final Utf8String TAG_TAGS = new Utf8String("tags");
    public static final Utf8String TAG_DESC = new Utf8String("description");

    void openXMLTag(XMLWriter writer, int verbosity) {
        String[] tags = getTagVector();

        writer.openTag(getXMLTag());

        if ( ! getXMLTag().equals(getName())) {
            writer.attribute(TAG_NAME, getName());
        }

        if (verbosity >= 3 && tags.length > 0) {
            String tagStr = "";
            for (String tag : tags) {
                if (!tagStr.isEmpty()) {
                    tagStr = ",";
                }
                tagStr += tag;
            }

            writer.attribute(TAG_TAGS, tagStr);
        }

        if (verbosity >= 1 && !getDescription().isEmpty()) {
            writer.attribute(TAG_DESC, getDescription());
        }
    }

    /**
     * The verbosity says how much to print.
     * At verbosity level 0, only the most critical parts are printed.
     * At verbosity level 1, descriptions are added.
     * At verbosity level 2, metrics without data is added.
     * At verbosity level 3, tags are included too.
     */
    public abstract void printXml(XMLWriter writer,
                                  int secondsPassed,
                                  int verbosity);

    public String toXml(int secondsPassed, int verbosity) {
        StringWriter writer = new StringWriter();
        printXml(new XMLWriter(writer), secondsPassed, verbosity);
        return writer.toString();
    }

    /**
     * Most metrics report numbers of some kind. To be able to report numbers
     * without having code to handle each possible metric type, these functions
     * exist to extract raw data to present easily.
     * @param id The part of the metric to extract. For instance, an average
     *           metric have average,
     */
    public abstract long getLongValue(String id);
    public abstract double getDoubleValue(String id);

    /**
     * When snapshotting we need to be able to join data from one set of metrics
     * to another set of metrics taken at another time. MetricSet doesn't know
     * the type of the metrics it contains, so we need a generic function for
     * doing this. This function assumes metric given as input is of the exact
     * same type as the one it is called on for simplicity. This is true when
     * adding to snapshots as they have been created with clone and is thus
     * always exactly equal.
     *
     * @param m Metric of exact same type as this one. (Will core if wrong)
     */
    abstract void addToSnapshot(Metric m);

    /**
     * For sum metrics to work with metric sets, metric sets need operator+=.
     * To implement this, we need a function to join any metric type together.
     * This is different from adding to snapshot. When adding to snapshots we
     * join different time periods to the same metric, but when adding parts
     * together we join different metrics for the same time. For instance, an
     * average metric of queuesize, should just join new values to create new
     * average when adding to snapshot, but when adding parts, the averages
     * themselves should be added together.
     *
     * @param m Metric of exact same type as this one. (Will core if wrong)
     */
    abstract void addToPart(Metric m);

    public boolean visit(MetricVisitor visitor, boolean tagAsAutoGenerated) {
        return visitor.visitPrimitiveMetric(this, tagAsAutoGenerated);
    }

    /** Set whether metrics have ever been set. */
    public abstract boolean used();

    /** Returns true if this metric is registered in a metric set. */
    public boolean isRegistered() { return (owner != null); }

    /**
     * If this metric is registered with an owner, remove itself from that owner.
     */
    public void unregister() {
        if (isRegistered()) {
            getOwner().unregisterMetric(this);
        }
    }

    public MetricSet getOwner() { return owner; }

    public MetricSet getRoot() {
        if (owner == null) {
            if (this instanceof MetricSet) {
                return (MetricSet)this;
            } else {
                return null;
            }
        } else {
            return owner.getRoot();
        }
    }
}
