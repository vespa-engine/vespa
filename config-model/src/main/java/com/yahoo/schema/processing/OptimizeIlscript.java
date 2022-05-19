// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.indexinglanguage.ExpressionOptimizer;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Run ExpressionOptimizer on all scripts, to get rid of expressions that have no effect.
 */
public class OptimizeIlscript extends Processor {

    public OptimizeIlscript(Schema schema,
                            DeployLogger deployLogger,
                            RankProfileRegistry rankProfileRegistry,
                            QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : schema.allConcreteFields()) {
            ScriptExpression script = field.getIndexingScript();
            if (script == null) continue;

            field.setIndexingScript((ScriptExpression)new ExpressionOptimizer().convert(script));
            if ( ! field.getIndexingScript().toString().equals(script.toString())) {
                info(schema, field, "Rewrote ilscript from:\n" + script.toString() +
                                    "\nto\n" + field.getIndexingScript().toString());
            }
        }
    }

}
