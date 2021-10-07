// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;


/**
 * The base class for groups of counters and values.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
abstract class Group extends Handle {
    Group(String name, Statistics manager, Callback parametrizedCallback) {
        super(name, manager, parametrizedCallback);
    }
}
