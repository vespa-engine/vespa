// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * Interface used to get time. This is separated into its own class, such that unit tests can fake timing to do timing related
 * tests without relying on the speed of the unit test processing.
 */
public interface Timer {

    long getCurrentTimeInMillis();

}
