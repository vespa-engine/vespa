// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

public class NonEmpty extends QueryChain {

    private final Query query;

    NonEmpty(Query query) {
        this.query = query;
        this.nonEmpty = true;
    }

    @Override
    public Select getSelect() {
        return sources.select;
    }

    @Override
    public String toString() {
        return Text.format("nonEmpty(%s)", query);
    }

    @Override
    boolean hasPositiveSearchField(String fieldName) {
        // TODO: implementation
        throw new UnsupportedOperationException("method not implemented");
    }

    @Override
    boolean hasPositiveSearchField(String fieldName, Object value) {
        // TODO: implementation
        throw new UnsupportedOperationException("method not implemented");
    }

    @Override
    boolean hasNegativeSearchField(String fieldName) {
        // TODO: implementation
        throw new UnsupportedOperationException("method not implemented");
    }

    @Override
    boolean hasNegativeSearchField(String fieldName, Object value) {
        // TODO: implementation
        throw new UnsupportedOperationException("method not implemented");
    }

}
