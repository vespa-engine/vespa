package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.vespa.model.AbstractService;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LargeRankExpressions {
    private final Map<String, RankExpressionBody> expressions = new ConcurrentHashMap<>();
    private final FileRegistry fileRegistry;

    public LargeRankExpressions(FileRegistry fileRegistry) {
        this.fileRegistry = fileRegistry;
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

    /** Returns a read-only map of the ranking constants in this indexed by name */
    public Map<String, RankExpressionBody> asMap() {
        return Collections.unmodifiableMap(expressions);
    }

    /** Initiate sending of these constants to some services over file distribution */
    public void sendTo(Collection<? extends AbstractService> services) {
        expressions.values().forEach(constant -> constant.sendTo(services));
    }
}
