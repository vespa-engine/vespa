// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.config;


import com.yahoo.container.jdisc.HttpRequest;

/**
 * An insulating layer between HTTP and whatever kind of HTTP statistics
 * interface is made available. This is an intermediary step towards a
 * generalized network layer.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class StatisticsEmitter {

    public StringBuilder respond(HttpRequest request) {
        return new StringBuilder("No statistics available yet.");
    }

}
