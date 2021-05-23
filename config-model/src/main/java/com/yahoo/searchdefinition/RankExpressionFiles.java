package com.yahoo.searchdefinition;

import com.yahoo.vespa.model.AbstractService;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RankExpressionFiles {
    private final Map<String, RankExpressionFile> expressions = new HashMap<>();

    public void add(RankExpressionFile expression) {
        expression.validate();
        String name = expression.getName();
        if (expressions.containsKey(name))
            throw new IllegalArgumentException("Rank expression file '" + name + "' defined twice");
        expressions.put(name, expression);
    }

    /** Returns the ranking constant with the given name, or null if not present */
    public RankExpressionFile get(String name) {
        return expressions.get(name);
    }

    /** Returns a read-only map of the ranking constants in this indexed by name */
    public Map<String, RankExpressionFile> asMap() {
        return Collections.unmodifiableMap(expressions);
    }

    /** Initiate sending of these constants to some services over file distribution */
    public void sendTo(Collection<? extends AbstractService> services) {
        expressions.values().forEach(constant -> constant.sendTo(services));
    }
}
