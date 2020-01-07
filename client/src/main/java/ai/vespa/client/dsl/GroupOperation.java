// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GroupOperation implements IGroupOperation {

    String type;
    Object value;
    Aggregator[] aggregators;

    public GroupOperation(String type, Object value) {
        this.type = type;
        this.value = value;
    }

    public GroupOperation(String type, Aggregator[] aggregators) {
        this.type = type;
        this.aggregators = aggregators;
    }

    @Override
    public String toString() {
        if (value != null) {
            return String.format("%s(%s)", type, value);
        }

        return String.format("%s(%s)",
                             type,
                             Stream.of(aggregators).map(Objects::toString).collect(Collectors.joining(" ")));
    }
}
