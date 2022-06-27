// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.csp;

import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.http.filter.DiscFilterResponse;
import com.yahoo.jdisc.http.filter.RequestView;
import com.yahoo.jdisc.http.filter.SecurityResponseFilter;
import com.yahoo.yolean.chain.Provides;

/**
 * The HTTP Content-Security-Policy (CSP) sandbox directive enables a sandbox for the requested resource similar to
 * the &lt;iframe&gt; sandbox attribute. It applies restrictions to a page's actions including preventing popups,
 * preventing the execution of plugins and scripts, and enforcing a same-origin policy.
 *
 * @author freva
 */
@Provides("CspResponseFilter")
public class CspResponseFilter extends AbstractResource implements SecurityResponseFilter {

    @Inject
    public CspResponseFilter() { }

    @Override
    public void filter(DiscFilterResponse response, RequestView request) {
        response.setHeader("Content-Security-Policy", "sandbox");
    }

}
