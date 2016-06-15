// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.ProxyServer;

import java.net.URI;
import java.util.Locale;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 * @since 2.0
 */
final class ProxyServerFactory {

    private ProxyServerFactory() {
        // hide
    }

    public static ProxyServer newInstance(URI uri) {
        if (uri == null) {
            return null;
        }
        String userInfo = uri.getUserInfo();
        String username = null, password = null;
        if (userInfo != null) {
            String[] arr = userInfo.split(":", 2);
            username = arr[0];
            password = arr.length > 1 ? arr[1] : null;
        }
        return new ProxyServer(ProxyServer.Protocol.valueOf(uri.getScheme().toUpperCase(Locale.US)),
                               uri.getHost(), uri.getPort(), username, password);
    }
}
