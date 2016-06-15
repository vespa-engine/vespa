// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.jdisc.application.MetricConsumer;

/**
 * <p>This is the interface to implement if one wishes to configure a non-default <tt>MetricConsumer</tt>. Simply
 * add the implementing class as a component in your services.xml file.</p>
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public interface MetricConsumerFactory {

    public MetricConsumer newInstance();
}
