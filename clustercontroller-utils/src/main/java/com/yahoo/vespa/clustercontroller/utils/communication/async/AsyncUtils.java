// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.async;

public class AsyncUtils {

    public static void waitFor(AsyncOperation<?> op) {
        while (!op.isDone()) {
            try{ Thread.sleep(1); } catch (InterruptedException e) {}
        }
    }

}
