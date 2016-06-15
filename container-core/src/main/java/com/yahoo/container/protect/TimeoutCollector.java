// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.protect;

import com.yahoo.concurrent.ThreadLocalDirectory.Updater;

/**
 * Allocator and glue for sampling timeouts in SearchHandler.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 * @deprecated this is not in use and will be removed in the next major release
 */
@Deprecated
public final class TimeoutCollector implements Updater<TimeoutRate, Boolean> {

    @Override
    public TimeoutRate createGenerationInstance(TimeoutRate previous) {
        return new TimeoutRate();
    }

    @Override
    public TimeoutRate update(TimeoutRate current, Boolean x) {
        current.addQuery(x);
        return current;
    }

}
