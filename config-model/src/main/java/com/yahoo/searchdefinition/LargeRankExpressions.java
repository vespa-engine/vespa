package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.vespa.model.AbstractService;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LargeRankExpressions {
    private final Map<String, RankExpressionBody> expressions = new HashMap<>();
    private final FileRegistry fileRegistry;

    public LargeRankExpressions(FileRegistry fileRegistry) {
        this.fileRegistry = fileRegistry;
    }

    public void add(RankExpressionBody expression) {
        expression.validate();
        expression.register(fileRegistry);
        String name = expression.getName();
        if (expressions.containsKey(name)) {
            if ( ! expressions.get(name).getBlob().equals(expression.getBlob())) {
                throw new IllegalArgumentException("Rank expression '" + name +
                        "' defined twice. Previous blob with " + expressions.get(name).getBlob().remaining() +
                        " bytes, while current has " + expression.getBlob().remaining() + " bytes");
            }
        } else {
            expressions.put(name, expression);
        }
    }

    /** Returns the ranking constant with the given name, or null if not present */
    public RankExpressionBody get(String name) {
        return expressions.get(name);
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
