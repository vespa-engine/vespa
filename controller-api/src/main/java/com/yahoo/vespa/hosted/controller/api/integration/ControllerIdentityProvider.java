// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;

import javax.net.ssl.SSLSocketFactory;

/**
 * @author freva
 */
public interface ControllerIdentityProvider extends ServiceIdentityProvider {

    /** Returns SSLSocketFactory that creates appropriate sockets to talk to the different config servers */
    SSLSocketFactory getConfigServerSslSocketFactory();

}
