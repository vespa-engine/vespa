// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.response;

import com.yahoo.component.provider.ListenableFreezableClass;
import com.yahoo.processing.Request;

/**
 * Convenience superclass for implementations of data. This contains no payload.
 *
 * @author bratseth
 */
public abstract class AbstractData extends ListenableFreezableClass implements Data {

    private Request request;

    /**
     * Creates some data marked with the request that created it
     */
    public AbstractData(Request request) {
        this.request = request;
    }

    /**
     * Returns the request that created this data
     */
    public Request request() {
        return request;
    }

}
