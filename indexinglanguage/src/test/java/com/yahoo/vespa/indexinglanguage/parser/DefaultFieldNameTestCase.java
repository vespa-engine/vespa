// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.parser;

import com.yahoo.language.process.Chunker;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.FieldGenerator;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.ScriptParserContext;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class DefaultFieldNameTestCase {

    @Test
    public void requireThatDefaultFieldNameIsAppliedWhenArgumentIsMissing() throws com.yahoo.vespa.indexinglanguage.parser.ParseException {
        IndexingInput input = new IndexingInput("input");
        var context = new ScriptParserContext(new SimpleLinguistics(),
                                              Chunker.throwsOnUse.asMap(),
                                              Embedder.throwsOnUse.asMap(),
                                              FieldGenerator.throwsOnUse.asMap());
        context.setInputStream(input);
        context.setDefaultFieldName("foo");
        InputExpression expression = (InputExpression)Expression.newInstance(context);
        assertEquals("foo", expression.getFieldName());
    }

}
