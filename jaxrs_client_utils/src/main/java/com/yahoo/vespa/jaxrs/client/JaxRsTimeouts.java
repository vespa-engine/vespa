// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import java.time.Duration;

/**
 * @author hakonhall
 */
public interface JaxRsTimeouts {
    /**
     * The connect timeout, which must be at least 1ms. Called once per real REST call.
     *
     * Throws com.google.common.util.concurrent.UncheckedTimeoutException on timeout.
     */
    Duration getConnectTimeoutOrThrow();

    /**
     * The read timeout, which must be at least 1ms. Called once per real REST call.
     *
     * Throws com.google.common.util.concurrent.UncheckedTimeoutException on timeout.
     */
    Duration getReadTimeoutOrThrow();
}
