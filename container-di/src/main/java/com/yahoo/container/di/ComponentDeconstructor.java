// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 */
public interface ComponentDeconstructor {
    void deconstruct(Object component);
}
