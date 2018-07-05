// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

/**
 * A metric value
 *
 * @author Simon Thoresen Hult
 */
public abstract class MetricValue {

    abstract void add(Number val);

    abstract void add(MetricValue val);

}
