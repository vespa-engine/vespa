// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.google.inject.Provider;

import javax.net.ssl.SSLContext;

/**
 * Provides a {@link SSLContext} for use in controller clients communicating with Athenz TLS secured services.
 * It is configured with a keystore containing the Athenz service certificate and a trust store with the Athenz CA certificates.
 *
 * @author bjorncs
 */
public interface AthenzSslContextProvider extends Provider<SSLContext> {}
