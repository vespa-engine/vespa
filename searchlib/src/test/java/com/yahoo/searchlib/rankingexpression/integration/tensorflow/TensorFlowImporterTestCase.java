package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class TensorFlowImporterTestCase {
    
    @Test
    public void testModel1() {
        List<RankingExpression> expressions = 
                new TensorFlowImporter().importModel("src/test/files/integration/tensorflow/model1/");
        assertEquals(1, expressions.size());
        assertEquals("scores", expressions.get(0).getName());
        assertEquals("" +
                     "softmax(join(rename(matmul(x, rename(x, (d1, d2), (d2, d3)), d2), d3, d2), " +
                                  "rename(matmul(x, rename(x, (d1, d2), (d2, d3)), d2), d3, d2), " +
                                  "f(a,b)(a + b)), " +
                             "d1)",
                     toNonPrimitiveString(expressions.get(0)));
    }
    
    private String toNonPrimitiveString(RankingExpression expression) {
        // toString on the wrapping expression will map to primitives, which is harder to read
        return ((TensorFunctionNode)expression.getRoot()).function().toString();
    }
    
}
