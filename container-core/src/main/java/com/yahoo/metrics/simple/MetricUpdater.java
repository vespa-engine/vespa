// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import com.yahoo.concurrent.ThreadLocalDirectory.Updater;

/**
 * The link between each single thread and the central data store.
 *
 * @author Steinar Knutsen
 */
class MetricUpdater implements Updater<Bucket, Sample> {

    @Override
    public Bucket createGenerationInstance(Bucket previous) {
        return new Bucket();
    }

    @Override
    public Bucket update(Bucket current, Sample x) {
        current.put(x);
        return current;
    }

}
