// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.client;

/**
 * @author bjorncs
 */
public class FeedClientBuilder {

    public static FeedClientBuilder create() { return new FeedClientBuilder(); }

    private FeedClientBuilder() {}

    public FeedClient build() { return new FeedClientImpl(this); }
}
