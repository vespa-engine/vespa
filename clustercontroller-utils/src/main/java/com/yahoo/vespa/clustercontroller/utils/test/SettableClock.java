// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.test;

import com.yahoo.vespa.clustercontroller.utils.util.Clock;

public abstract class SettableClock extends Clock {
    public abstract void set(long newTime);
    public abstract void adjust(long adjustment);
}
