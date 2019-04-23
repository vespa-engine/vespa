// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler.v3;

import com.yahoo.jdisc.Metric;
import java.util.Map;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.20
 */
public final class NullFeedMetric implements Metric {

    public NullFeedMetric(boolean flag) {
        if (!flag) {
            throw new IllegalArgumentException("must set flag allowing to throw away metrics");
        }
    }

    @Override
    public void set(String key, Number val, Context ctx) {
    }

    @Override
    public void add(String key, Number val, Context ctx) {
    }

    @Override
    public Context createContext(Map<String, ?> properties) {
        return NullFeedContext.INSTANCE;
    }

    private static class NullFeedContext implements Context {
        private static final NullFeedContext INSTANCE = new NullFeedContext();
    }
}
