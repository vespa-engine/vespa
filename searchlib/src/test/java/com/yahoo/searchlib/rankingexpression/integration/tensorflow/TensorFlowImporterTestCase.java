package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class TensorFlowImporterTestCase {

    @Test
    public void testModel1() {
        TestLogger logger = new TestLogger();
        List<RankingExpression> expressions =
                new TensorFlowImporter().importModel("src/test/files/integration/tensorflow/model1/", logger);

        // Check logged messages
        assertEquals(2, logger.messages.size());
        assertEquals("Skipping output 'TopKV2:0' of signature 'tensorflow/serving/classify': Conversion of TensorFlow operation 'TopKV2' is not supported",
                     logger.messages.get(0));
        assertEquals("Skipping output 'index_to_string_Lookup:0' of signature 'tensorflow/serving/classify': Conversion of TensorFlow operation 'LookupTableFindV2' is not supported",
                     logger.messages.get(1));

        // Check resulting Vespa expression
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

    private class TestLogger implements TensorFlowImporter.MessageLogger {

        List<String> messages = new ArrayList<>();

        @Override
        public void log(Level level, String message) {
            messages.add(message);
        }

    }

}
