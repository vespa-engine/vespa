// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.parser;

import com.yahoo.language.process.Embedder;
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
    public void requireThatDefaultFieldNameIsAppliedWhenArgumentIsMissing() throws ParseException {
        IndexingInput input = new IndexingInput("input");
        InputExpression exp = (InputExpression)Expression.newInstance(new ScriptParserContext(new SimpleLinguistics(),
                                                                                              Embedder.throwsOnUse.asMap())
                                                                              .setInputStream(input)
                                                                              .setDefaultFieldName("foo"));
        assertEquals("foo", exp.getFieldName());
    }

}
