// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;


import com.yahoo.log.event.Event;
import com.yahoo.statistics.Value.Parameters;

import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

/**
 * A set of related values which should be logged together.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ValueGroup extends Group {
    // A map for names of subevents and Value instances
    private Map<String, Value> subEvents = new HashMap<>();

    /**
     * Create a ValueGroup.
     *
     * @param name
     *            The symbolic name of this group of values
     * @param manager
     *            the statistics manager acquired by injection
     */
    public ValueGroup(String name, Statistics manager) {
        this(name, manager, null);
    }

    /**
     * Create a ValueGroup.
     *
     * @param name
     *            The symbolic name of this group of values
     * @param manager
     *            the statistics manager acquired by injection
     * @param callback
     *            will be invoked each time data is written to the log
     */
    public ValueGroup(String name, Statistics manager, Callback callback) {
        super(name, manager, callback);
        manager.register(this);
    }

    /**
     * Put a value into the named value in the group.
     */
    public void put(String name, double x) {
        Value v = getValue(name);
        v.put(x);
    }

    /**
     * Get a value with a given name, creates a new value if no
     * value with the name given exists.
     *
     */
    synchronized Value getValue(String name) {
        Value v = subEvents.get(name);
        if (v == null) {
            v = getNewValue(name);
        }
        return v;
    }

    private Value getNewValue(String subName) {
        Value v = Value.initializeUnregisteredValue(subName, new Parameters().setLogRaw(true));
        subEvents.put(subName, v);
        return v;
    }

    /**
     * Dump state to log and reset.
     */
    @Override
    public void runHandle() {
        StringBuilder multi = new StringBuilder();
        ValueProxy[] proxies;
        int i = 0;

        synchronized (this) {
            proxies = new ValueProxy[subEvents.size()];
            i = 0;
            for (Iterator<Value> j = subEvents.values().iterator(); j.hasNext();) {
                Value v = j.next();
                proxies[i] = v.getProxyAndReset();
                i++;
            }
        }

        while (i > 0) {
            i--;
            if (multi.length() > 0) {
                multi.append(", ");
            }
            multi.append(proxies[i].getName());
            multi.append("=");
            multi.append(proxies[i].getRaw());
        }

        Event.valueGroup(getName(), multi.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (o.getClass() != this.getClass()) {
            return false;
        }
        ValueGroup other = (ValueGroup) o;
        return getName().equals(other.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode() + 31 * "ValueGroup".hashCode();
    }
}
