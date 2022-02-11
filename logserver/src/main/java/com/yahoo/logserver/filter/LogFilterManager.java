// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.filter;

import com.yahoo.log.LogLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * The LogFilterManager keeps track of associations between
 * LogFilter names and instances, so that access to filters
 * is truly global.  It also manages the LogFilter namespace
 * to ensure that system-defined filters are not tampered with.
 *
 * @author Bjorn Borud
 */
@SuppressWarnings("deprecation")
public class LogFilterManager {
    private static final LogFilterManager instance;

    static {
        instance = new LogFilterManager();

        LevelFilter allEvents = new LevelFilter();
        allEvents.addLevel(LogLevel.EVENT);
        instance.addLogFilterInternal("system.allevents", allEvents);
        instance.addLogFilterInternal("system.all", new NullFilter());
        instance.addLogFilterInternal("system.mute", MuteFilter.getInstance());
    }

    private final Map<String, LogFilter> filters = new HashMap<String, LogFilter>();

    private LogFilterManager() {
    }

    /**
     * Public interface for adding a name-logfilter mapping. If
     * there exists a mapping already the old mapping is replaced
     * with the new mapping.
     * <p>
     * If the name is within the namespace reserved for internal
     * built-in filters it will throw an exception
     */
    public static void addLogFilter(String name, LogFilter filter) {
        if (filter == null) {
            throw new NullPointerException("filter cannot be null");
        }

        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }

        String n = name.toLowerCase();

        if (n.startsWith("system.")) {
            throw new IllegalArgumentException("'system' namespace is reserved");
        }

        instance.addLogFilterInternal(n, filter);
    }

    /**
     * LogFilter lookup function
     *
     * @param name The name of the LogFilter to be looked up.
     * @return Returns the LogFilter associated with this name or
     * <code>null</code> if not found.
     */
    public static LogFilter getLogFilter(String name) {
        return instance.filters.get(name);
    }


    /**
     * Get the names of the defined filters.
     *
     * @return Returns an array containing the names of filters that
     * have been registered.
     */
    public static String[] getFilterNames() {
        synchronized (instance.filters) {
            String[] filterNames = new String[instance.filters.keySet().size()];
            instance.filters.keySet().toArray(filterNames);
            return filterNames;
        }
    }

    /**
     * Internal method which takes care of the job of adding
     * LogFilter mappings but doesn't perform any of the checks
     * performed by the public method for adding mappings.
     */
    private void addLogFilterInternal(String name, LogFilter filter) {
        filters.put(name, filter);
    }
}
