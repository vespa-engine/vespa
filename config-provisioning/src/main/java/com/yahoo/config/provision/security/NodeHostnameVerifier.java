// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.security;

import javax.net.ssl.SSLSession;

/**
 * @author bjorncs
 */
public interface NodeHostnameVerifier {
    boolean verify(String hostname, SSLSession session);
}
