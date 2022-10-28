// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Rank extends QueryChain {

    private final List<QueryChain> queries = new ArrayList<>();

    Rank(Query query, QueryChain... ranks) {
        this.query = query;
        this.nonEmpty = query.nonEmpty();
        queries.add(query);
        queries.addAll(Stream.of(ranks).collect(Collectors.toList()));
    }

    @Override
    public Select getSelect() {
        return sources.select;
    }

    @Override
    public String toString() {
        return "rank(" + queries.stream().map(Objects::toString).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    boolean hasPositiveSearchField(String fieldName) {
        return queries.get(0).hasPositiveSearchField(fieldName);
    }

    @Override
    boolean hasPositiveSearchField(String fieldName, Object value) {
        return queries.get(0).hasPositiveSearchField(fieldName, value);
    }

    @Override
    boolean hasNegativeSearchField(String fieldName) {
        return queries.get(0).hasNegativeSearchField(fieldName);
    }

    @Override
    boolean hasNegativeSearchField(String fieldName, Object value) {
        return queries.get(0).hasNegativeSearchField(fieldName, value);
    }

}
