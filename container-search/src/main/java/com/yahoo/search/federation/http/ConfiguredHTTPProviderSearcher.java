// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import com.yahoo.component.ComponentId;
import com.yahoo.search.federation.ProviderConfig;
import com.yahoo.search.cache.QrBinaryCacheConfig;
import com.yahoo.search.cache.QrBinaryCacheRegionConfig;
import com.yahoo.search.Result;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.statistics.Statistics;

import java.util.Collections;


/**
 * Superclass for http provider searchers which depends on config. All this is doing is translating
 * the provider and cache configurations to parameters which are passed upwards.
 *
 * @author  Arne Bergene Fossaa
 * @author  bratseth
 */
public abstract class ConfiguredHTTPProviderSearcher extends HTTPProviderSearcher {

    /** Create this from a configuraton */
    public ConfiguredHTTPProviderSearcher(ComponentId id, ProviderConfig providerConfig, Statistics manager) {
        super(id,ConfiguredSearcherHelper.toConnectionList(providerConfig), new HTTPParameters(providerConfig), manager);
    }

    /** Create this from a configuraton */
    public ConfiguredHTTPProviderSearcher(ComponentId id, ProviderConfig providerConfig,
                                          HTTPParameters parameters, Statistics manager) {
        super(id,ConfiguredSearcherHelper.toConnectionList(providerConfig), parameters, manager);
    }

    /** Create this from a configuraton with a configured cache */
    public ConfiguredHTTPProviderSearcher(ComponentId id, ProviderConfig providerConfig,
                                          QrBinaryCacheConfig cacheConfig,
    	                                  QrBinaryCacheRegionConfig regionConfig, Statistics manager) {
        super(id,ConfiguredSearcherHelper.toConnectionList(providerConfig), new HTTPParameters(providerConfig), manager);
        configureCache(cacheConfig, regionConfig);
    }

    /** Create this from a configuraton with a configured cache */
    public ConfiguredHTTPProviderSearcher(ComponentId id, ProviderConfig providerConfig,
                                          QrBinaryCacheConfig cacheConfig,
    	                                  QrBinaryCacheRegionConfig regionConfig, HTTPParameters parameters, Statistics manager) {
        super(id,ConfiguredSearcherHelper.toConnectionList(providerConfig), parameters, manager);
        configureCache(cacheConfig, regionConfig);
    }

    /** Create an instance from direct parameters having a single connection. Useful for testing */
    public ConfiguredHTTPProviderSearcher(String idString, String host, int port, String path, Statistics manager) {
        super(new ComponentId(idString), Collections.singletonList(new Connection(host, port)), path, manager);
    }

    /** Create an instance from direct parameters having a single connection. Useful for testing */
    public ConfiguredHTTPProviderSearcher(String idString, String host,int port, HTTPParameters parameters, Statistics manager) {
        super(new ComponentId(idString), Collections.singletonList(new Connection(host, port)), parameters, manager);
    }

    /**
     * Override this to provider multi-phase result filling towards a backend.
     * This default implementation does nothing.
     */
    @Override
    public void fill(Result result, String summaryName, Execution execution, Connection connection) {
    }

}
