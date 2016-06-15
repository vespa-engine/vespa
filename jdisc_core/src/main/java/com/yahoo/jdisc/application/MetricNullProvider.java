// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.Provider;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
class MetricNullProvider implements Provider<MetricConsumer> {

    @Override
    public MetricConsumer get() {
        return null;
    }
}
