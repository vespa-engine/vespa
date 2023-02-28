// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.ranking;

import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;

/**
 * factory for model-evaluation proxies
 * @author arnej
 */
@Beta
public class RankProfilesEvaluatorFactory {

    private final ComponentRegistry<RankProfilesEvaluator> registry;

    @Inject
    public RankProfilesEvaluatorFactory(ComponentRegistry<RankProfilesEvaluator> registry) {
        this.registry = registry;
    }

    public RankProfilesEvaluator proxyForSchema(String schemaName) {
        var component = registry.getComponent("ranking-expression-evaluator." + schemaName);
        if (component == null) {
            throw new IllegalArgumentException("ranking expression evaluator for schema '" + schemaName + "' not found");
        }
        return component;
    }

    public String toString() {
        var buf = new StringBuilder();
        buf.append(this.getClass().getName()).append(" containing: [");
        for (var id : registry.allComponentsById().keySet()) {
            buf.append(" ").append(id.toString());
        }
        buf.append(" ]");
        return buf.toString();
    }
}
