// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.client;

import java.util.Optional;

/**
 * @author bjorncs
 */
public class Result {

    public enum Type {

    }

    public Type type() { return null; }
    public String documentId() { return null; }
    public String resultMessage() { return null; }
    public Optional<String> traceMessage() { return Optional.empty(); }
}
