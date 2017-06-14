// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.provider;

/**
 * A freezable which supports listening
 *
 * @author bratseth
 * @since 5.1.13
 */
public interface ListenableFreezable extends Freezable {

    /** Adds a listener which will be called when this is frozen */
    public void addFreezeListener(java.lang.Runnable runnable, java.util.concurrent.Executor executor);

}
