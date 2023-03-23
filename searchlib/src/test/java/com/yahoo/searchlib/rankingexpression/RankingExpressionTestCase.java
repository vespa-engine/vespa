// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression;

import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.OperationNode;
import com.yahoo.searchlib.rankingexpression.rule.Operator;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.IfNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.FunctionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.SerializationContext;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.functions.Reduce;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class RankingExpressionTestCase {

    @Test
    public void testParamInFeature() throws ParseException {
        assertParse("if (1 > 2, dotProduct(allparentid,query(cate1_parentid)), 2)",
                    "if ( 1 > 2,\n" +
                    "dotProduct(allparentid, query(cate1_parentid)),\n" +
                    "2\n" +
                    ")");
    }

    @Test
    public void testDollarShorthand() throws ParseException {
        assertParse("query(var1)", " $var1");
        assertParse("query(var1)", " $var1 ");
        assertParse("query(var1) + query(var2)", " $var1 + $var2 ");
        assertParse("query(var1) + query(var2) - query(var3)", " $var1 + $var2 - $var3 ");
        assertParse("query(var1) + query(var2) - query(var3) * query(var4) / query(var5)", " $var1 + $var2 - $var3 * $var4 / $var5 ");
        assertParse("(query(var1) + query(var2)) - query(var3) * query(var4) / query(var5)", "($var1 + $var2)- $var3 * $var4 / $var5 ");
        assertParse("query(var1) + (query(var2) - query(var3)) * query(var4) / query(var5)", " $var1 +($var2 - $var3)* $var4 / $var5 ");
        assertParse("query(var1) + query(var2) - (query(var3) * query(var4)) / query(var5)", " $var1 + $var2 -($var3 * $var4)/ $var5 ");
        assertParse("query(var1) + query(var2) - query(var3) * (query(var4) / query(var5))", " $var1 + $var2 - $var3 *($var4 / $var5)");
        assertParse("if (if (f1.out < query(p1), 0, 1) < if (f2.out < query(p2), 0, 1), f3.out, query(p3))", "if(if(f1.out<$p1,0,1)<if(f2.out<$p2,0,1),f3.out,$p3)");
    }
    
    @Test
    public void testProgrammaticBuilding() throws ParseException {
        ReferenceNode input = new ReferenceNode("input");
        ReferenceNode constant = new ReferenceNode("constant");
        OperationNode product = new OperationNode(input, Operator.multiply, constant);
        Reduce<Reference> sum = new Reduce<>(new TensorFunctionNode.ExpressionTensorFunction(product), Reduce.Aggregator.sum);
        RankingExpression expression = new RankingExpression(new TensorFunctionNode(sum));

        RankingExpression expected = new RankingExpression("sum(input * constant)");
        assertEquals(expected.toString(), expression.toString());
    }

    @Test
    public void testLookaheadIndefinitely() throws Exception {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<Boolean> future = exec.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                try {
                    new RankingExpression("if (fieldMatch(title) < 0.316316, if (now < 1.218627E9, if (now < 1.217667E9, if (now < 1.217244E9, if (rankBoost < 100050.0, 0.1424368, if (match < 0.284921, if (now < 1.217238E9, 0.1528184, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, if (now < 1.217238E9, 0.1, 0.1493261))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))), 0.1646852)), 0.1850886), if (match < 0.308468, if (firstPhase < 5891.5, 0.08424015, 0.1167076), if (rankBoost < 120050.0, 0.111576, 0.1370456))), if (match < 0.31644, 0.1543837, 0.1727403)), if (now < 1.218088E9, if (now < 1.217244E9, if (fieldMatch(metakeywords).significance < 0.1425405, if (match.totalWeight < 450.0, 0.1712793, 0.1632426), 0.1774488), 0.1895567), if (now < 1.218361E9, if (fieldTermMatch(keywords_1).firstPosition < 1.5, 0.1530005, 0.1370894), 0.1790079)))");
                    return Boolean.TRUE;
                } catch (ParseException e) {
                    return Boolean.FALSE;
                }
            }
        });
        assertTrue(future.get(60, TimeUnit.SECONDS));
    }

    @Test
    public void testSelfRecursionSerialization() throws ParseException {
        List<ExpressionFunction> functions = new ArrayList<>();
        functions.add(new ExpressionFunction("foo", null, new RankingExpression("foo")));

        RankingExpression exp = new RankingExpression("foo");
        try {
            exp.getRankProperties(new SerializationContext(functions, Optional.empty()));
        } catch (RuntimeException e) {
            assertEquals("Cycle in ranking expression function 'foo' called from: [foo[]]", e.getMessage());
        }
    }

    @Test
    public void testFunctionCycleSerialization() throws ParseException {
        List<ExpressionFunction> functions = new ArrayList<>();
        functions.add(new ExpressionFunction("foo", null, new RankingExpression("bar")));
        functions.add(new ExpressionFunction("bar", null, new RankingExpression("foo")));

        RankingExpression exp = new RankingExpression("foo");
        try {
            exp.getRankProperties(new SerializationContext(functions, Optional.empty()));
        } catch (RuntimeException e) {
            assertEquals("Cycle in ranking expression function 'foo' called from: [foo[], bar[]]", e.getMessage());
        }
    }

    @Test
    public void testSerialization() throws ParseException {
        List<ExpressionFunction> functions = new ArrayList<>();
        functions.add(new ExpressionFunction("foo", Arrays.asList("arg1", "arg2"), new RankingExpression("min(arg1, pow(arg2, 2))")));
        functions.add(new ExpressionFunction("bar", Arrays.asList("arg1", "arg2"), new RankingExpression("arg1 * arg1 + 2 * arg1 * arg2 + arg2 * arg2")));
        functions.add(new ExpressionFunction("baz", Arrays.asList("arg1", "arg2"), new RankingExpression("foo(1, 2) / bar(arg1, arg2)")));
        functions.add(new ExpressionFunction("cox", null, new RankingExpression("10 + 08 * 1977")));

        assertSerialization(Arrays.asList(
         "rankingExpression(foo@e2dc17a89864aed0.12232eb692c6c502) + rankingExpression(foo@af74e3fd9070bd18.a368ed0a5ba3a5d0) * rankingExpression(foo@dbab346efdad5362.e5c39e42ebd91c30)",
         "min(5,pow(rankingExpression(foo@d1d1417259cdc651.573bbcd4be18f379),2))",
         "min(6,pow(7,2))",
         "min(1,pow(2,2))",
         "min(3,pow(4,2))",
         "min(rankingExpression(foo@84951be88255b0ec.d0303e061b36fab8),pow(8,2))"), "foo(1,2) + foo(3,4) * foo(5, foo(foo(6, 7), 8))", functions);
        assertSerialization(Arrays.asList(
         "rankingExpression(foo@e2dc17a89864aed0.12232eb692c6c502) + rankingExpression(bar@af74e3fd9070bd18.a368ed0a5ba3a5d0)",
         "min(1,pow(2,2))",
         "3 * 3 + 2 * 3 * 4 + 4 * 4"), "foo(1, 2) + bar(3, 4)", functions);
        assertSerialization(Arrays.asList(
         "rankingExpression(baz@e2dc17a89864aed0.12232eb692c6c502)",
         "min(1,pow(2,2))",
         "rankingExpression(foo@e2dc17a89864aed0.12232eb692c6c502) / rankingExpression(bar@e2dc17a89864aed0.12232eb692c6c502)",
         "1 * 1 + 2 * 1 * 2 + 2 * 2"), "baz(1, 2)", functions);
        assertSerialization(Arrays.asList(
         "rankingExpression(cox)",
         "10 + 8 * 1977"), "cox", functions
        );
    }
    
    @Test
    public void testTensorSerialization() {
        assertSerialization("map(constant(tensor0), f(a)(cos(a)))", 
                            "map(constant(tensor0), f(a)(cos(a)))");
        assertSerialization("map(constant(tensor0), f(a)(cos(a))) + join(attribute(tensor1), map(reduce(map(attribute(tensor1), f(a)(a * a)), sum, x), f(a)(sqrt(a))), f(a,b)(a / b))", 
                            "map(constant(tensor0), f(a)(cos(a))) + l2_normalize(attribute(tensor1), x)");
        assertSerialization("join(reduce(join(reduce(join(constant(tensor0), attribute(tensor1), f(a,b)(a * b)), sum, x), attribute(tensor1), f(a,b)(a * b)), sum, y), query(tensor2), f(a,b)(a + b))", 
                            "xw_plus_b(matmul(constant(tensor0), attribute(tensor1), x), attribute(tensor1), query(tensor2), y)");
        assertSerialization("tensor(x{}):{{x:a}:(1 + 2 + 3),{x:b}:(if (1 > 2, 3, 4)),{x:c}:(reduce(tensor0 * tensor1, sum))}",
                            "tensor(x{}):{ {x:a}:1+2+3, {x:b}:if(1>2,3,4), {x:c}:sum(tensor0*tensor1) }");
        assertSerialization("tensor(x[3]):{{x:0}:1.0,{x:1}:2.0,{x:2}:3}",
                            "tensor(x[3]):[1.0, 2.0, 3]");
        assertSerialization("tensor(x[3]):{{x:0}:1.0,{x:1}:(reduce(tensor0 * tensor1, sum)),{x:2}:3}",
                            "tensor(x[3]):[1.0, sum(tensor0*tensor1), 3]");
    }

    @Test
    public void testFunctionInTensorSerialization() throws ParseException {
        List<ExpressionFunction> functions = new ArrayList<>();
        functions.add(new ExpressionFunction("scalarFunction", List.of(), new RankingExpression("5")));
        functions.add(new ExpressionFunction("tensorFunction", List.of(), new RankingExpression("tensor(x[3]):[1, 2, 3]")));

        // Getting a value from a tensor supplied by a function, inside a tensor generate function
        assertSerialization(List.of("tensor(x[3])((rankingExpression(tensorFunction)[x]))"),
                            "tensor(x[3])(tensorFunction[x])",
                            functions, false);

        // Getting a value from a tensor supplied by a function, where the value index is supplied by a function, inside a tensor generate function, short form
        assertSerialization(List.of("tensor(x[3])((rankingExpression(tensorFunction)[(rankingExpression(scalarFunction))]))"),
                            "tensor(x[3])(tensorFunction[scalarFunction()])",
                            functions, false);

        // 'scalarFunction'  is interpreted as a label here since it is the short form of a mapped dimension
        assertSerialization(List.of("tensor(x[3])((rankingExpression(tensorFunction){scalarFunction}))"),
                            "tensor(x[3])(tensorFunction{scalarFunction})",
                            functions, false);

        // Getting a value from a tensor supplied by a function, where the value index is supplied by a function, inside a tensor generate function, long form
        assertSerialization(List.of("tensor(x[3])((rankingExpression(tensorFunction){x:(rankingExpression(scalarFunction))}))"),
                            "tensor(x[3])(tensorFunction{x:scalarFunction()})",
                            functions, false);

        // 'scalarFunction'  without parentheses is interpreted as a label instead of a reference to the function
        assertSerialization(List.of("tensor(x[3])((rankingExpression(tensorFunction){x:scalarFunction}))"),
                            "tensor(x[3])(tensorFunction{x:scalarFunction})",
                            functions, false);

        // Accessing a function in a dynamic tensor, short form
        assertSerialization(List.of("tensor(x[2]):{{x:0}:(rankingExpression(scalarFunction)),{x:1}:(rankingExpression(scalarFunction))}"),
                            "tensor(x[2]):[scalarFunction(), scalarFunction()]",
                            functions, false);

        // Accessing a function in a dynamic tensor, long form
        assertSerialization(List.of("tensor(x{}):{{x:foo}:(rankingExpression(scalarFunction)),{x:bar}:(rankingExpression(scalarFunction))}"),
                            "tensor(x{}):{{x:foo}:scalarFunction(), {x:bar}:scalarFunction()}",
                            functions, false);

        // Shadowing
        assertSerialization(List.of("tensor(scalarFunction[1])((rankingExpression(tensorFunction){x:(scalarFunction + rankingExpression(scalarFunction))}))"),
                            "tensor(scalarFunction[1])(tensorFunction{x: scalarFunction + scalarFunction()})",
                            functions, true);

    }

    @Test
    public void testPropertyName() {
        assertEquals("rankingExpression(m4).rankingScript", RankingExpression.propertyName("m4"));
        assertEquals("m4", RankingExpression.extractScriptName("rankingExpression(m4).rankingScript"));
        assertNull(RankingExpression.extractScriptName("rankingexpression(m4).rankingScript"));
        assertNull(RankingExpression.extractScriptName("rankingExpression(m4).rankingscript"));

        assertEquals("rankingExpression(m4).expressionName", RankingExpression.propertyExpressionName("m4"));
    }

    @Test
    public void testBug3464208() throws ParseException {
        List<ExpressionFunction> functions = new ArrayList<>();
        functions.add(new ExpressionFunction("log10tweetage", null, new RankingExpression("69")));

        String lhs = "log10(0.01+attribute(user_followers_count)) * log10(socialratio) * " +
                     "log10(userage/(0.01+attribute(user_statuses_count)))";
        String rhs = "(log10tweetage * log10tweetage * log10tweetage) + 5.0 * " +
                     "attribute(ythl)";

        String expLhs = "log10(0.01 + attribute(user_followers_count)) * log10(socialratio) * " +
                        "log10(userage / (0.01 + attribute(user_statuses_count)))";
        String expRhs = "(rankingExpression(log10tweetage) * rankingExpression(log10tweetage) * " +
                        "rankingExpression(log10tweetage)) + 5.0 * attribute(ythl)";

        assertSerialization(Arrays.asList(expLhs + " + " + expRhs, "69"), lhs + " + " + rhs, functions);
        assertSerialization(Arrays.asList(expLhs + " - " + expRhs, "69"), lhs + " - " + rhs, functions);
    }

    @Test
    public void testParse() throws ParseException, IOException {
        BufferedReader reader = new BufferedReader(new FileReader("src/tests/rankingexpression/rankingexpressionlist"));
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.length() == 0 || line.charAt(0) == '#') {
                continue;
            }
            String[] parts = line.split(";");
            // System.out.println("Parsing '" + parts[0].trim() + "'..");
            RankingExpression expression = new RankingExpression(parts[0].trim());

            String out = expression.toString();
            if (parts.length == 1) {
                assertEquals(parts[0].trim(), out);
            } else {
                boolean ok = false;
                String err = "Expression '" + out + "' not present in { ";
                for (int i = 1; i < parts.length && !ok; ++i) {
                    err += "'" + parts[i].trim() + "'";
                    if (parts[i].trim().equals(out)) {
                        ok = true;
                    }
                    if (i < parts.length - 1) {
                        err += ", ";
                    }
                }
                err += " }.";
                assertTrue("At line " + lineNumber + ": " + err, ok);
            }
        }
    }

    @Test
    public void testIssue() throws ParseException {
        assertEquals("feature.0", new RankingExpression("feature.0").toString());
        assertEquals("if (1 > 2, 3, 4) + feature(arg1).out.out",
                     new RankingExpression("if ( 1 > 2 , 3 , 4 ) + feature ( arg1 ) . out.out").toString());
    }

    @Test
    public void testNegativeConstantArgument() throws ParseException {
        assertEquals("foo(-1.2)", new RankingExpression("foo(-1.2)").toString());
    }

    @Test
    public void testNaming() throws ParseException {
        RankingExpression test = new RankingExpression("a+b");
        test.setName("test");
        assertEquals("test: a + b", test.toString());
    }

    @Test
    public void testCondition() throws ParseException {
        RankingExpression expression = new RankingExpression("if(1<2,3,4)");
        assertTrue(expression.getRoot() instanceof IfNode);
    }

    @Test
    public void testFileImporting() throws ParseException {
        RankingExpression expression = new RankingExpression(new File("src/test/files/simple.expression"));
        assertEquals("simple: a + b", expression.toString());
    }

    @Test
    public void testNonCanonicalLegalStrings() throws ParseException {
        assertParse("a * (b) + c * d", "a* (b) + \nc*d");
    }

    @Test
    public void testEquality() throws ParseException {
        assertEquals(new RankingExpression("if ( attribute(foo)==\"BAR\",log(attribute(popularity)+5),log(fieldMatch(title).proximity)*fieldMatch(title).completeness)"),
                     new RankingExpression("if(attribute(foo)==\"BAR\",  log(attribute(popularity)+5),log(fieldMatch(title).proximity) * fieldMatch(title).completeness)"));

        assertFalse(new RankingExpression("if ( attribute(foo)==\"BAR\",log(attribute(popularity)+5),log(fieldMatch(title).proximity)*fieldMatch(title).completeness)").equals(
                    new RankingExpression("if(attribute(foo)==\"BAR\",  log(attribute(popularity)+5),log(fieldMatch(title).earliness) * fieldMatch(title).completeness)")));
    }

    @Test
    public void testSetMembershipConditions() throws ParseException {
        assertEquals(new RankingExpression("if ( attribute(foo) in [\"FOO\",  \"BAR\"],log(attribute(popularity)+5),log(fieldMatch(title).proximity)*fieldMatch(title).completeness)"),
                     new RankingExpression("if(attribute(foo) in [\"FOO\",\"BAR\"],  log(attribute(popularity)+5),log(fieldMatch(title).proximity) * fieldMatch(title).completeness)"));

        assertFalse(new RankingExpression("if ( attribute(foo) in [\"FOO\",  \"BAR\"],log(attribute(popularity)+5),log(fieldMatch(title).proximity)*fieldMatch(title).completeness)").equals(
                    new RankingExpression("if(attribute(foo) in [\"FOO\",\"BAR\"],  log(attribute(popularity)+5),log(fieldMatch(title).earliness) * fieldMatch(title).completeness)")));

        assertEquals(new RankingExpression("if ( attribute(foo) in [attribute(category),  \"BAR\"],log(attribute(popularity)+5),log(fieldMatch(title).proximity)*fieldMatch(title).completeness)"),
                     new RankingExpression("if(attribute(foo) in [attribute(category),\"BAR\"],  log(attribute(popularity)+5),log(fieldMatch(title).proximity) * fieldMatch(title).completeness)"));
        assertEquals(new RankingExpression("if (GENDER$ in [-1.0, 1.0], 1, 0)"), new RankingExpression("if (GENDER$ in [-1.0, 1.0], 1, 0)"));
    }

    @Test
    public void testComments() throws ParseException {
        assertEquals(new RankingExpression("if ( attribute(foo) in [\"FOO\",  \"BAR\"],\n" +
        		"# a comment\n" +
        		"log(attribute(popularity)+5),log(fieldMatch(title).proximity)*" +
        		"# a multiline \n" +
        		" # comment\n" +
        		"fieldMatch(title).completeness)"),
                new RankingExpression("if(attribute(foo) in [\"FOO\",\"BAR\"],  log(attribute(popularity)+5),log(fieldMatch(title).proximity) * fieldMatch(title).completeness)"));
    }

    @Test
    public void testIsNan() throws ParseException {
        String strExpr = "if (isNan(attribute(foo)) == 1.0, 1.0, attribute(foo))";
        RankingExpression expr = new RankingExpression(strExpr);
        CompositeNode root = (CompositeNode)expr.getRoot();
        CompositeNode comparison = (CompositeNode)root.children().get(0);
        ExpressionNode isNan = comparison.children().get(0);
        assertTrue(isNan instanceof FunctionNode);
        assertEquals("isNan(attribute(foo))", isNan.toString());
    }

    protected static void assertParse(String expected, String expression) throws ParseException {
        assertEquals(expected, new RankingExpression(expression).toString());
    }

    /** Test serialization with no functions */
    private void assertSerialization(String expectedSerialization, String expressionString) {
        String serializedExpression;
        try {
            RankingExpression expression = new RankingExpression(expressionString);
            // No functions -> expect one rank property
            serializedExpression = expression.getRankProperties(new SerializationContext()).values().iterator().next();
            assertEquals(expectedSerialization, serializedExpression);
        }
        catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }

        try {
            // No functions -> output should be parseable to a ranking expression
            // (but not the same one due to primitivization)
            RankingExpression reparsedExpression = new RankingExpression(serializedExpression);
            // Serializing the primitivized expression should yield the same expression again
            String reserializedExpression = 
                    reparsedExpression.getRankProperties(new SerializationContext()).values().iterator().next();
            assertEquals(expectedSerialization, reserializedExpression);
        }
        catch (ParseException e) {
            throw new IllegalArgumentException("Could not parse the serialized expression", e);
        }
    }

    private void assertSerialization(List<String> expectedSerialization,
                                     String expressionString,
                                     List<ExpressionFunction> functions) {
        assertSerialization(expectedSerialization, expressionString, functions, false);
    }

    private void assertSerialization(List<String> expectedSerialization, String expressionString,
                                     List<ExpressionFunction> functions, boolean print) {
        try {
            if (print)
                System.out.println("Parsing expression '" + expressionString + "':");

            RankingExpression expression = new RankingExpression(expressionString);
            Map<String, String> rankProperties = expression.getRankProperties(new SerializationContext(functions,
                                                                                                       Optional.empty()));
            if (print) {
                for (String key : rankProperties.keySet())
                    System.out.println(key + ": " + rankProperties.get(key));
            }
            for (int i = 0; i < expectedSerialization.size();) {
                String val = expectedSerialization.get(i++);
                assertTrue("Properties contains " + val, rankProperties.containsValue(val));
            }
            if (print)
                System.out.println("");
        }
        catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
