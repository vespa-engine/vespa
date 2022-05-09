// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.impl;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * @author Ulf Lilleengen
 * @author Bjorn Borud
 * @author arnej27959
 * @author bjorncs
 */
public class LogUtils {
    public static boolean empty(String s) {
        return (s == null || s.equals(""));
    }
    public static String getHostName () {
        return getDefaults().vespaHostname();
    }
    public static String getPID() {
        return Long.toString(ProcessHandle.current().pid());
    }
}
