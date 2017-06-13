// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * $Id$
 *
 */

package com.yahoo.logserver.formatter;

import java.util.HashMap;
import java.util.Map;

/**
 * This singleton class implements a central registry of LogFormatter
 * instances.
 *
 * @author Bjorn Borud
 */
public class LogFormatterManager {
    private static final LogFormatterManager instance;

    static {
        instance = new LogFormatterManager();
        instance.addLogFormatterInternal("system.textformatter", new TextFormatter());
        instance.addLogFormatterInternal("system.nullformatter", new NullFormatter());
    }

    private final Map<String, LogFormatter> logFormatters = new HashMap<String, LogFormatter>();

    private LogFormatterManager() {}

    /**
     * LogFormatter lookup function
     *
     * @param name The name of the LogFormatter to be looked up.
     * @return Returns the LogFormatter associated with this name or
     * <code>null</code> if not found.
     */
    public static LogFormatter getLogFormatter(String name) {
        synchronized (instance.logFormatters) {
            return instance.logFormatters.get(name);
        }
    }

    /**
     * Get the names of the defined formatters.
     *
     * @return Returns an array containing the names of formatters that
     * have been registered.
     */
    public static String[] getFormatterNames() {
        synchronized (instance.logFormatters) {
            String[] formatterNames = new String[instance.logFormatters.keySet().size()];
            instance.logFormatters.keySet().toArray(formatterNames);
            return formatterNames;
        }
    }

    /**
     * Internal method which takes care of the job of adding
     * LogFormatter mappings but doesn't perform any of the checks
     * performed by the public method for adding mappings.
     */
    private void addLogFormatterInternal(String name, LogFormatter logFormatter) {
        synchronized (logFormatters) {
            logFormatters.put(name, logFormatter);
        }
    }

}
