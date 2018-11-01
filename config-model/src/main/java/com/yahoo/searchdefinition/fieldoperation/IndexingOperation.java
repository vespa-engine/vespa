// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.searchdefinition.parser.SimpleCharStream;
import com.yahoo.vespa.indexinglanguage.ScriptParserContext;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.linguistics.AnnotatorConfig;

/**
 * @author Einar M R Rosenvinge
 */
public class IndexingOperation implements FieldOperation {

    private final ScriptExpression script;

    public IndexingOperation(ScriptExpression script) {
        this.script = script;
    }

    public void apply(SDField field) {
        field.setIndexingScript(script);
    }

    /** Creates an indexing operation which will use the simple linguistics implementation suitable for testing */
    @SuppressWarnings("deprecation")
    public static IndexingOperation fromStream(SimpleCharStream input, boolean multiLine) throws ParseException {
        return fromStream(input, multiLine, new SimpleLinguistics());
    }

    public static IndexingOperation fromStream(SimpleCharStream input, boolean multiLine, Linguistics linguistics)
            throws ParseException {
        ScriptParserContext config = new ScriptParserContext(linguistics);
        config.setAnnotatorConfig(new AnnotatorConfig());
        config.setInputStream(input);
        ScriptExpression exp;
        try {
            if (multiLine) {
                exp = ScriptExpression.newInstance(config);
            } else {
                exp = new ScriptExpression(StatementExpression.newInstance(config));
            }
        } catch (com.yahoo.vespa.indexinglanguage.parser.ParseException e) {
            ParseException t = new ParseException("Error reported by IL parser: " + e.getMessage());
            t.initCause(e);
            throw t;
        }
        return new IndexingOperation(exp);
    }

}
