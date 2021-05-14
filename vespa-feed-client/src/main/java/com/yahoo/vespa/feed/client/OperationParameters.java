// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.client;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * @author bjorncs
 * @author jonmv
 */
public class OperationParameters {

    public static Builder builder() { return new Builder(); }

    private OperationParameters(Builder builder) {

    }

    public boolean createIfNonExistent() { return false; }
    public Optional<String> testAndSetCondition() { return Optional.empty(); }
    public Optional<Duration> timeout() { return Optional.empty(); }
    public Optional<String> route() { return Optional.empty(); }
    public OptionalInt tracelevel() { return OptionalInt.empty(); }

    public static class Builder {

        private Builder() {}

        public OperationParameters build() { return new OperationParameters(this); }

    }
}
