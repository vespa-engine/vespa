// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import org.apache.hc.core5.reactor.ssl.TlsDetails;

import javax.net.ssl.SSLEngine;

/**
 * @author bjorncs
 */
public class TlsDetailsFactory {
    private TlsDetailsFactory() {}

    public static TlsDetails create(SSLEngine e) { return new TlsDetails(e.getSession(), "h2"); /*h2 == HTTP2*/ }
}

