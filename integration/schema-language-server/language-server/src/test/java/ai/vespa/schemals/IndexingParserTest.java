package ai.vespa.schemals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.yahoo.vespa.indexinglanguage.ExpressionVisitor;
import com.yahoo.vespa.indexinglanguage.expressions.ArithmeticExpression;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ChunkExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ConstantExpression;
import com.yahoo.vespa.indexinglanguage.expressions.EmbedExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.ForEachExpression;
import com.yahoo.vespa.indexinglanguage.expressions.IndexExpression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.LowerCaseExpression;
import com.yahoo.vespa.indexinglanguage.expressions.NormalizeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.PackBitsExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SummaryExpression;

import ai.vespa.schemals.parser.indexinglanguage.IndexingParser;

public class IndexingParserTest {
    @Test
    void checkIndexingExpressionConstruction() {
        // Check that expression construction is successful in 
        // our limited parsing environment.

        assertEqualsParsedFlattened(new Class<?>[] {
            StatementExpression.class,
            InputExpression.class,
            EmbedExpression.class,
            AttributeExpression.class}, "input foo | embed embedder | attribute");

        assertEqualsParsedFlattened(new Class<?>[] {
            StatementExpression.class,
            InputExpression.class,
            ForEachExpression.class,
            StatementExpression.class,
            LowerCaseExpression.class,
            ForEachExpression.class,
            StatementExpression.class,
            NormalizeExpression.class,
            IndexExpression.class,
            SummaryExpression.class}, "input text_array | for_each { lowercase } | for_each { normalize } | index | summary");

        assertEqualsParsedFlattened(new Class<?>[] {
            StatementExpression.class,
            ArithmeticExpression.class,
            ArithmeticExpression.class,
            ArithmeticExpression.class,
            InputExpression.class,
            ConstantExpression.class,
            InputExpression.class,
            InputExpression.class,
            SummaryExpression.class}, "input weight_src * 67 + input w1_src + input w2_src | summary");

        assertEqualsParsedFlattened(new Class<?>[] {
            StatementExpression.class,
            InputExpression.class,
            ChunkExpression.class,
            EmbedExpression.class,
            PackBitsExpression.class,
            AttributeExpression.class,
            IndexExpression.class}, "input text | chunk fixed-length 1024 | embed | pack_bits | attribute | index");
    }

    private static void assertEqualsParsedFlattened(Class<?>[] expectedFlattened, String input) {
        IndexingParser parser = new IndexingParser(input);
        Expression exp = parser.root();
        ClassFlattenVisitor visitor = new ClassFlattenVisitor();
        visitor.visit(exp);
        var result = visitor.getResult();

        assertEquals(expectedFlattened.length, result.size(), "On input: " + input);

        for (int i = 0; i < expectedFlattened.length; ++i) {
            assertEquals(expectedFlattened[i], result.get(i), "On input: " + input);
        }
    }

    private static class ClassFlattenVisitor extends ExpressionVisitor {
        List<Class<?>> result;

        public ClassFlattenVisitor() {
            result = new ArrayList<>();
        }

        @Override
        protected void doVisit(Expression exp) {
            result.add(exp.getClass());
        }

        List<Class<?>> getResult() {
            return result;
        }
    }
}
