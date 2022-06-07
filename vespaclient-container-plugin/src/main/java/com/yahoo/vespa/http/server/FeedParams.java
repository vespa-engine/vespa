// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import java.util.concurrent.TimeUnit;

/**
 * Feed level parameters.
 *
 * @author Einar M R Rosenvinge
 */
public final class FeedParams {

    /**
     * Enumeration of data formats that are acceptable by the
     * FeedClient methods.
     */
    public enum DataFormat {
        /** UTF-8-encoded XML. Preamble is not necessary. */
        XML_UTF8,
        JSON_UTF8
    }

}
