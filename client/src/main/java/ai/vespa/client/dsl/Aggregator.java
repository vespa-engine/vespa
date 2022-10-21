// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

public class Aggregator {

    private final String type;
    private final Object value;

    Aggregator(String type) {
        this(type, "");
    }

    Aggregator(String type, Object value) {
        this.type = type;
        this.value = value;
    }

    @Override
    public String toString() {
        return Text.format("%s(%s)", type, value);
    }

}