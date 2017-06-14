// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import java.util.Collections;

import com.yahoo.component.ComponentId;
import com.yahoo.search.federation.ProviderConfig;
import com.yahoo.search.Result;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.statistics.Statistics;


/**
 * Superclass for http client searchers which depends on config. All this is doing is translating
 * the provider and cache configurations to parameters which are passed upwards.
 *
 * @author bratseth
 */
public abstract class ConfiguredHTTPClientSearcher extends HTTPClientSearcher {

    /** Create this from a configuraton */
    public ConfiguredHTTPClientSearcher(final ComponentId id, final ProviderConfig providerConfig, Statistics manager) {
        super(id, ConfiguredSearcherHelper.toConnectionList(providerConfig), new HTTPParameters(providerConfig), manager);
    }

    /** Create an instance from direct parameters having a single connection. Useful for testing */
    public ConfiguredHTTPClientSearcher(String idString,String host,int port,String path, Statistics manager) {
        super(new ComponentId(idString), Collections.singletonList(new Connection(host,port)),path, manager);
    }

    /** Forwards to the next in chain fill(result,summaryName) */
    @Override
    public void fill(Result result,String summaryName, Execution execution,Connection connection) {
        execution.fill(result,summaryName);
    }

}
