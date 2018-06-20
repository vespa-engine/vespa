// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

/**
 * Interface for subscribing to config from upstream config sources.
 *
 * @author hmusum
 */
public interface Subscriber extends Runnable {

    void cancel();
}
