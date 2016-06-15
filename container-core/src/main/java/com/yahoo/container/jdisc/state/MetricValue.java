// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

/**
 * A metric value
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public abstract class MetricValue {

    abstract void add(Number val);

    abstract void add(MetricValue val);

}
