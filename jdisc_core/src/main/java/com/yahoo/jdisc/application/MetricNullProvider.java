// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.Provider;

/**
 * @author Simon Thoresen Hult
 */
class MetricNullProvider implements Provider<MetricConsumer> {

    @Override
    public MetricConsumer get() {
        return null;
    }
}
