// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import java.util.Map;

/**
 * A set of metric dimensions, which are key-value string pairs.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public interface MetricDimensions extends Iterable<Map.Entry<String, String>> {

}
