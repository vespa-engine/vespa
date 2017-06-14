// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import java.util.ArrayList;
import java.util.List;

import com.yahoo.search.federation.ProviderConfig;

/**
 * Some static helper classes for configured*Searcher classes
 *
 * @author bratseth
 */
class ConfiguredSearcherHelper {

    /** No instantiation */
    private ConfiguredSearcherHelper() { }

    public static List<Connection> toConnectionList(ProviderConfig providerConfig) {
        List<Connection> connections=new ArrayList<>();
        for(ProviderConfig.Node node : providerConfig.node()) {
            connections.add(new Connection(node.host(), node.port()));
        }
        return connections;
    }

}
