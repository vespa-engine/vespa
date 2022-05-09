// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.FileRegistry;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LargeRankExpressions {
    private final Map<String, RankExpressionBody> expressions = new ConcurrentHashMap<>();
    private final FileRegistry fileRegistry;
    private final int limit;

    public LargeRankExpressions(FileRegistry fileRegistry) {
        this(fileRegistry, 8192);
    }
    public LargeRankExpressions(FileRegistry fileRegistry, int limit) {
        this.fileRegistry = fileRegistry;
        this.limit = limit;
    }

    public void add(RankExpressionBody expression) {
        String name = expression.getName();
        RankExpressionBody prev = expressions.putIfAbsent(name, expression);
        if (prev == null) {
            expression.validate();
            expression.register(fileRegistry);
        } else {
            if ( ! prev.getBlob().equals(expression.getBlob())) {
                throw new IllegalArgumentException("Rank expression '" + name +
                        "' defined twice. Previous blob with " + prev.getBlob().remaining() +
                        " bytes, while current has " + expression.getBlob().remaining() + " bytes");
            }
        }
    }
    public int limit() { return limit; }

    /** Returns a read-only map of the ranking constants in this indexed by name */
    public Map<String, RankExpressionBody> asMap() {
        return Collections.unmodifiableMap(expressions);
    }

}
