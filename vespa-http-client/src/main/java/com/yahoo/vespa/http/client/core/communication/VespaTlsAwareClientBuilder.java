// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import org.apache.http.impl.client.HttpClientBuilder;

/**
 * A static factory for VespaHttpClientBuilder.
 * The main purpose of this class is to avoid references to classes not compiled with JDK8.
 *
 * @author bjorncs
 */
// TODO Remove use of reflection once vespa-http-client only targets JDK11
// The VespaTlsAwareClientBuilder class refers to classes in security-utils / http-utils that targets JDK11+.
class VespaTlsAwareClientBuilder {

    private VespaTlsAwareClientBuilder() {}

    static HttpClientBuilder createHttpClientBuilder() {
        try {
            Class<?> builderClass = Class.forName("ai.vespa.util.http.VespaHttpClientBuilder");
            return (HttpClientBuilder) builderClass.getMethod("create").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
