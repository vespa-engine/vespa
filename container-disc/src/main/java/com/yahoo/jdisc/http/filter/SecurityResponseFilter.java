// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.component.chain.model.Chainable;

public interface SecurityResponseFilter extends ResponseFilterBase, Chainable {

    void filter(DiscFilterResponse response, RequestView request);

}
