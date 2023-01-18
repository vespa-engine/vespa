// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.application.api.FileRegistry;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class LargeRankingExpressions {

    private final Map<String, RankingExpressionBody> expressions = new ConcurrentHashMap<>();
    private final FileRegistry fileRegistry;
    private final int limit;

    public LargeRankingExpressions(FileRegistry fileRegistry) {
        this(fileRegistry, 8192);
    }
    public LargeRankingExpressions(FileRegistry fileRegistry, int limit) {
        this.fileRegistry = fileRegistry;
        this.limit = limit;
    }

    public void add(RankingExpressionBody expression) {
        String name = expression.getName();
        RankingExpressionBody prev = expressions.putIfAbsent(name, expression);
        if (prev == null) {
            expression.validate();
            expression.register(fileRegistry);
        } else {
            if ( ! prev.getBlob().equals(expression.getBlob())) {
                throw new IllegalArgumentException("Ranking expression '" + name +
                                                   "' defined twice. Previous blob with " + prev.getBlob().remaining() +
                                                   " bytes, while current has " + expression.getBlob().remaining() + " bytes");
            }
        }
    }
    public int limit() { return limit; }

    /** Returns a read-only list of ranking constants ordered by name */
    public Collection<RankingExpressionBody> expressions() {
        return expressions.values().stream().sorted().toList();
    }

    // Note: Use by integration tests in internal repo
    /** Returns a read-only map of the ranking constants in this indexed by name */
    public Map<String, RankingExpressionBody> asMap() {
        return Collections.unmodifiableMap(expressions);
    }

}
