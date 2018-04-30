// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;


/**
 * Helper class for carrying around cache-related
 * config parameters to the FastSearcher class.
 *
 * @author arnej27959
 */
public class CacheParams {

    public int cacheMegaBytes = 0;
    public double cacheTimeOutSeconds = 0;
    public CacheControl cacheControl = null;

    public CacheParams(int megabytes, double timeoutseconds) {
        this.cacheMegaBytes = megabytes;
        this.cacheTimeOutSeconds = timeoutseconds;
    }

    public CacheParams(CacheControl cacheControl) {
        this.cacheControl = cacheControl;
    }

}
