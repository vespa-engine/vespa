// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc.test;

import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.slobrok.server.Slobrok;

/**
 * @author jonmv
 */
public class TestUtils {

    private TestUtils() { }

    public static SlobroksConfig configFor(Slobrok slobrok) {
        return new SlobroksConfig.Builder().slobrok(new SlobroksConfig.Slobrok.Builder()
                                                            .connectionspec(new Spec("localhost", slobrok.port()).toString()))
                                           .build();
    }

}
