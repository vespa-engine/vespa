// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.vespa.model.container.component.chain.Chain;

/**
 * @author bjorncs
 */
public class HttpFilterChain extends Chain<Filter> {

    public enum Type { USER, SYSTEM }

    private final Type type;

    public HttpFilterChain(ChainSpecification inner, Type type) {
        super(inner);
        this.type = type;
    }

    public HttpFilterChain(ComponentId id, Type type) { this(FilterChains.emptyChainSpec(id), type); }
    public HttpFilterChain(String id, Type type) { this(ComponentId.fromString(id), type); }

    public Type type() { return type; }
    public String id() { return getComponentId().stringValue(); }
}
