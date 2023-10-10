// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

/**
 * @author bjorncs
 */
public class AcceptAllHostnamesVerifier implements HostnameVerifier {

    private static final AcceptAllHostnamesVerifier INSTANCE = new AcceptAllHostnamesVerifier();

    public static AcceptAllHostnamesVerifier instance() { return INSTANCE; }

    private AcceptAllHostnamesVerifier() {}

    @Override public boolean verify(String hostname, SSLSession session) { return true; }

}

