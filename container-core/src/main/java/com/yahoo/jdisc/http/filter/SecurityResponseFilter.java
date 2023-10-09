// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

public interface SecurityResponseFilter extends ResponseFilterBase {

    void filter(DiscFilterResponse response, RequestView request);

}
