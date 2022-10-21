// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Select {

    private final List<String> selectedFields = new ArrayList<>();

    Select(String fieldName) {
        selectedFields.add(fieldName);
    }

    public Select(String fieldName, String... others) {
        selectedFields.add(fieldName);
        selectedFields.addAll(Stream.of(others).collect(Collectors.toList()));
    }

    Select(List<String> fieldNames) {
        selectedFields.addAll(fieldNames);
    }

    public Sources from(String sd) {
        return new Sources(this, sd);
    }

    public Sources from(String sd, String... sds) {
        return new Sources(this, sd, sds);
    }

    @Override
    public String toString() {
        return selectedFields.isEmpty() ? "*" : String.join(", ", selectedFields);
    }

}
