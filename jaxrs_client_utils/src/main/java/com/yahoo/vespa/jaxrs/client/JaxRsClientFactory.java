// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import com.yahoo.vespa.applicationmodel.HostName;

/**
 * Interface for creating a JAX-RS client API instance for a single server endpoint.
 *
 * @author bakksjo
 */
public interface JaxRsClientFactory {
    <T> T createClient(Class<T> apiClass, HostName hostName, int port, String pathPrefix, String scheme);
}
