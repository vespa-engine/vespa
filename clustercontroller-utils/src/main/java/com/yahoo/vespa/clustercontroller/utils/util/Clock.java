// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.util;

/**
 * Wrap access to clock so that we can override it in unit tests
 */
public class Clock {

    public long getTimeInMillis() { return System.currentTimeMillis(); }

    public int getTimeInSecs() { return (int)(getTimeInMillis() / 1000); }

}
