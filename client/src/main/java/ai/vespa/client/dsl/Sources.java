// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Sources {

    final Select select;
    private final List<String> targetDocTypes;

    Sources(Select select, List<String> searchDefinitions) {
        this.select = select;
        this.targetDocTypes = new ArrayList<>(searchDefinitions);
    }

    Sources(Select select, String searchDefinition) {
        this(select, Collections.singletonList(searchDefinition));
    }

    Sources(Select select, String searchDefinition, String... others) {
        this(select, Stream.concat(Stream.of(searchDefinition), Stream.of(others)).collect(Collectors.toList()));
    }

    @Override
    public String toString() {
        if (targetDocTypes.isEmpty() || targetDocTypes.size() == 1 && "*".equals(targetDocTypes.get(0))) {
            return "sources *";
        }

        if (targetDocTypes.size() == 1) {
            return targetDocTypes.get(0);
        }

        return "sources " + String.join(", ", targetDocTypes);
    }

    public Field where(String fieldName) {
        Field f = new Field(this, fieldName);
        f.setOp("and");
        return f;
    }

    public Query where(QueryChain userinput) {
        return whereReturnQuery(userinput);
    }

    public EndQuery where(Rank rank) {
        return whereReturnEndQuery(rank);
    }

    private Query whereReturnQuery(QueryChain qc) {
        return new Query(this, qc);
    }

    private EndQuery whereReturnEndQuery(Rank rank) {
        rank.setSources(this);
        return new EndQuery(rank);
    }

}
