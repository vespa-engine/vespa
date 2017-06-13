// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi.test.counterservice;

/**
 * Interface to the test bundle service
 *
 * @author bratseth
 */
public interface CounterService {

    public int getCounter();

    public void incrementCounter();

}
