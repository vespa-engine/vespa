// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.indexinglanguage.ExpressionOptimizer;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Run ExpressionOptimizer on all scripts, to get rid of expressions that have no effect.
 */
public class OptimizeIlscript extends Processor {

    public OptimizeIlscript(Search search,
                            DeployLogger deployLogger,
                            RankProfileRegistry rankProfileRegistry,
                            QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        for (SDField field : search.allConcreteFields()) {
            ScriptExpression script = field.getIndexingScript();
            if (script == null) continue;

            field.setIndexingScript((ScriptExpression)new ExpressionOptimizer().convert(script));
            if ( ! field.getIndexingScript().toString().equals(script.toString())) {
                warn(search, field, "Rewrote ilscript from:\n" + script.toString() +
                                    "\nto\n" + field.getIndexingScript().toString());
            }
        }
    }

}
