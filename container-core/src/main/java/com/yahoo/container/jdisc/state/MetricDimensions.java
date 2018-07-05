// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import java.util.Map;

/**
 * A set of metric dimensions, which are key-value string pairs.
 *
 * @author Simon Thoresen Hult
 */
public interface MetricDimensions extends Iterable<Map.Entry<String, String>> {

}
