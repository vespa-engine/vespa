// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Group implements IGroup, IGroupOperation {

    private final String type;
    private final IGroupOperation[] operations;

    Group(String type, IGroupOperation[] operations) {
        this.type = type;
        this.operations = operations;
    }

    @Override
    public String toString() {
        return Text.format("%s(%s)",
                             type,
                             Stream.of(operations).map(Objects::toString).collect(Collectors.joining(" ")));
    }
}
