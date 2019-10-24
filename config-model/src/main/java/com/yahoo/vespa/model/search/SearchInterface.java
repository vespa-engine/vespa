// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

/**
 * This represents an interface for searching.
 * It can be both a backend search node or a dispatcher.
 *
 * @author geirst
 */
public interface SearchInterface {

    NodeSpec getNodeSpec();
    String getHostName();

}
