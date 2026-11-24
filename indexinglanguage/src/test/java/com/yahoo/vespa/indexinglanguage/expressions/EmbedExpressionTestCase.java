// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * @author glebashnik
 */
public class EmbedExpressionTestCase {

    @Test
    public void testArrayOfAnyIsOnlyAcceptedWhenItIsMultipart() {
        var context = new TypeContext(new SimpleTestAdapter());
        embedExpression().setInputType(new MultipartDataType(List.of(DataType.STRING, DataType.RAW)), context);

        var e = assertThrows(VerificationException.class,
                             () -> embedExpression().setInputType(new ArrayDataType(AnyDataType.instance), context));
        assertTrue(e.getMessage(),
                   e.getMessage().contains("This requires either a string, raw or array<string> input type, " +
                                           "or a string and a raw separated by a comma, but got Array<any>"));
    }

    private static EmbedExpression embedExpression() {
        var embedders = new Components.Map<Embedder>(Map.of("emb1", new Embedder.FailingEmbedder()), Embedder.FailingEmbedder::new);
        return new EmbedExpression(new SimpleLinguistics(), embedders, "emb1", List.of());
    }

}
