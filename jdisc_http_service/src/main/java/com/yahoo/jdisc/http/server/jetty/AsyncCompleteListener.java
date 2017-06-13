// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import java.io.IOException;

/**
 * Interface for async listeners only interested in onComplete.
 * @author tonytv
 */
@FunctionalInterface
interface AsyncCompleteListener extends AsyncListener {
    @Override
    default void onTimeout(AsyncEvent event) throws IOException {}

    @Override
    default void onError(AsyncEvent event) throws IOException {}

    @Override
    default void onStartAsync(AsyncEvent event) throws IOException {}
}
