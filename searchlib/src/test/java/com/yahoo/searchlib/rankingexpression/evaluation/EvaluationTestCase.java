// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.javacc.UnicodeUtilities;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.tensor.MapTensor;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.*;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tests expression evaluation
 *
 * @author bratseth
 */
public class EvaluationTestCase extends junit.framework.TestCase {

    private MapContext defaultContext;

    @Override
    protected void setUp() {
        Map<String, Value> bindings = new HashMap<String, Value>();
        bindings.put("zero", DoubleValue.frozen(0d));
        bindings.put("one", DoubleValue.frozen(1d));
        bindings.put("one_half", DoubleValue.frozen(0.5d));
        bindings.put("a_quarter", DoubleValue.frozen(0.25d));
        bindings.put("foo", StringValue.frozen("foo"));
        defaultContext = new MapContext(bindings);
    }

    public void testEvaluation() {
        assertEvaluates(0.5, "0.5");
        assertEvaluates(-0.5, "-0.5");
        assertEvaluates(0.5, "one_half");
        assertEvaluates(-0.5, "-one_half");
        assertEvaluates(0, "nonexisting");
        assertEvaluates(0.75, "0.5 + 0.25");
        assertEvaluates(0.75, "one_half + a_quarter");
        assertEvaluates(1.25, "0.5 - 0.25 + one");

        // String
        assertEvaluates(1, "if(\"a\"==\"a\",1,0)");

        // Precedence
        assertEvaluates(26, "2*3+4*5");
        assertEvaluates(1, "2/6+4/6");
        assertEvaluates(2 * 3 * 4 + 3 * 4 * 5 - 4 * 200 / 10, "2*3*4+3*4*5-4*200/10");

        // Conditionals
        assertEvaluates(2 * (3 * 4 + 3) * (4 * 5 - 4 * 200) / 10, "2*(3*4+3)*(4*5-4*200)/10");
        assertEvaluates(0.5, "if( 2<3, one_half, one_quarter)");
        assertEvaluates(0.25,"if( 2>3, one_half, a_quarter)");
        assertEvaluates(0.5, "if( 1==1, one_half, a_quarter)");
        assertEvaluates(0.5, "if( 1<=1, one_half, a_quarter)");
        assertEvaluates(0.5, "if( 1<=1.1, one_half, a_quarter)");
        assertEvaluates(0.25,"if( 1>=1.1, one_half, a_quarter)");
        assertEvaluates(0.5, "if( 0.33333333333333333333~=1/3, one_half, a_quarter)");
        assertEvaluates(0.25,"if( 0.33333333333333333333~=1/35, one_half, a_quarter)");
        assertEvaluates(5.5, "if(one_half in [one_quarter,one_half],  one_half+5,log(one_quarter) * one_quarter)");
        assertEvaluates(0.5, "if( 1 in [1,2 , 3], one_half, a_quarter)");
        assertEvaluates(0.25,"if( 1 in [  2,3,4], one_half, a_quarter)");
        assertEvaluates(0.5, "if( \"foo\" in [\"foo\",\"bar\"], one_half, a_quarter)");
        assertEvaluates(0.5, "if( foo in [\"foo\",\"bar\"], one_half, a_quarter)");
        assertEvaluates(0.5, "if( \"foo\" in [foo,\"bar\"], one_half, a_quarter)");
        assertEvaluates(0.5, "if( foo in [foo,\"bar\"], one_half, a_quarter)");
        assertEvaluates(0.25,"if( \"foo\" in [\"baz\",\"boz\"], one_half, a_quarter)");
        assertEvaluates(0.5, "if( one in [0, 1, 2], one_half, a_quarter)");
        assertEvaluates(0.25,"if( one in [2], one_half, a_quarter)");
        assertEvaluates(2.5, "if(1.0, 2.5, 3.5)");
        assertEvaluates(3.5, "if(0.0, 2.5, 3.5)");
        assertEvaluates(2.5, "if(1.0-1.1, 2.5, 3.5)");
        assertEvaluates(3.5, "if(1.0-1.0, 2.5, 3.5)");

        // Conditionals with branch probabilities
        RankingExpression e = assertEvaluates(3.5, "if(1.0-1.0, 2.5, 3.5, 0.3)");
        assertEquals(0.3, ((IfNode) e.getRoot()).getTrueProbability());

        // Conditionals as expressions
        assertEvaluates(new BooleanValue(true), "2<3");
        assertEvaluates(new BooleanValue(false), "2>3");
        assertEvaluates(new BooleanValue(false), "if (3>2, 2>3, 5.0)");
        assertEvaluates(new BooleanValue(true), "2>3<1"); // The result of 2>3 is converted to 0, which is <1
        assertEvaluates(2.5, "if(2>3<1, 2.5, 3.5)");
        assertEvaluates(2.5, "if(1+1>3<1+0, 2.5, 3.5)");

        // Functions
        assertEvaluates(0, "sin(0)");
        assertEvaluates(1, "cos(0)");
        assertEvaluates(8, "pow(4/2,min(cos(0)*3,5))");

        // Combined
        assertEvaluates(1.25, "5*if(1>=1.1, one_half, if(min(1,2)<max(1,2),if (\"foo\" in [\"foo\",\"bar\"],a_quarter,3000), 0.57345347))");

    }

    @Test
    public void testTensorEvaluation() {
        assertEvaluates("{}", "tensor0", "{}"); // empty

        // sum(tensor)
        assertEvaluates("{ {}:5.0 }",  "sum(tensor0)", "5.0");
        assertEvaluates("{ {}:-5.0 }", "sum(tensor0)", "-5.0");
        assertEvaluates("{ {}:12.5 }", "sum(tensor0)", "{ {d1:l1}:5.5, {d2:l2}:7.0 }");
        assertEvaluates("{ {}:0.0 }",  "sum(tensor0)", "{ {d1:l1}:5.0, {d2:l2}:7.0, {}:-12.0}");

        // scalar functions on tensors
        assertEvaluates("{ {}:1, {d1:l1}:2, {d1:l1,d2:l1 }:3 }", 
                        "log10(tensor0)", "{ {}:10, {d1:l1}:100, {d1:l1,d2:l1}:1000 }");
        assertEvaluates("{ {}:50, {d1:l1}:500, {d1:l1,d2:l1}:5000 }",
                        "5 * tensor0", "{ {}:10, {d1:l1}:100, {d1:l1,d2:l1}:1000 }");
        assertEvaluates("{ {}:13, {d1:l1}:103, {d1:l1,d2:l1}:1003 }",
                        "tensor0 + 3","{ {}:10, {d1:l1}:100, {d1:l1,d2:l1}:1000 }");
        assertEvaluates("{ {}:1, {d1:l1}:10, {d1:l1,d2:l1 }:100 }",
                        "tensor0 / 10", "{ {}:10, {d1:l1}:100, {d1:l1,d2:l1}:1000 }");
        assertEvaluates("{ {}:-10, {d1:l1}:-100, {d1:l1,d2:l1 }:-1000 }",
                        "- tensor0", "{ {}:10, {d1:l1}:100, {d1:l1,d2:l1}:1000 }");
        assertEvaluates("{ {}:-10, {d1:l1}:0, {d1:l1,d2:l1 }:0 }",
                        "min(tensor0, 0)", "{ {}:-10, {d1:l1}:0, {d1:l1,d2:l1}:10 }");
        assertEvaluates("{ {}:0, {d1:l1}:0, {d1:l1,d2:l1 }:10 }",
                        "max(tensor0, 0)", "{ {}:-10, {d1:l1}:0, {d1:l1,d2:l1}:10 }");
        assertEvaluates("{ {h:1}:1.5, {h:2}:1.5 }", "0.5 + tensor0", "{ {h:1}:1.0,{h:2}:1.0 }");

        // sum(tensor, dimension)
        assertEvaluates("{ {y:1}:4.0, {y:2}:12.0 }",
                        "sum(tensor0, x)", "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        assertEvaluates("{ {x:1}:6.0, {x:2}:10.0 }",
                        "sum(tensor0, y)", "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");

        // tensor join
        assertEvaluates("{ }", "tensor0 * tensor0", "{}");
        assertEvaluates("tensor(x{},y{},z{}):{}", "( tensor0 * tensor1 ) * ( tensor2 * tensor1 )", 
                        "{{x:-}:1}", "{}", "{{y:-,z:-}:1}"); // empty dimensions are preserved
        assertEvaluates("tensor(x{}):{}",
                        "tensor0 * tensor1", "{ {x:1}:3 }", "{ {x:2}:5 }");
        assertEvaluates("{ {x:1}:15 }",
                        "tensor0 * tensor1", "{ {x:1}:3 }", "{ {x:1}:5 }");
        assertEvaluates("{ {x:1,y:1}:15 }",
                        "tensor0 * tensor1", "{ {x:1}:3 }", "{ {y:1}:5 }");
        assertEvaluates("{ {x:1,y:1}:15, {x:2,y:1}:35 }",
                        "tensor0 * tensor1", "{ {x:1}:3, {x:2}:7 }", "{ {y:1}:5 }");
        assertEvaluates("{ {x:1,y:1}:8, {x:2,y:1}:12 }",
                        "tensor0 + tensor1", "{ {x:1}:3, {x:2}:7 }", "{ {y:1}:5 }");
        assertEvaluates("{ {x:1,y:1}:-2, {x:2,y:1}:2 }",
                        "tensor0 - tensor1", "{ {x:1}:3, {x:2}:7 }", "{ {y:1}:5 }");
        assertEvaluates("{ {x:1,y:1}:5, {x:2,y:1}:4 }",
                        "tensor0 / tensor1", "{ {x:1}:15, {x:2}:12 }", "{ {y:1}:3 }");
        assertEvaluates("{ {x:1,y:1,z:1}:7, {x:1,y:1,z:2}:13, {x:2,y:1,z:1}:21, {x:2,y:1,z:2}:39, {x:1,y:2,z:1}:55 }",
                        "tensor0 * tensor1", "{ {x:1,y:1}:1, {x:2,y:1}:3, {x:1,y:2}:5 }", "{ {y:1,z:1}:7, {y:2,z:1}:11, {y:1,z:2}:13 }");
        assertEvaluates("{{x:1,y:1}:0.0}","tensor1 * tensor2 * tensor3", "{ {x:1}:1 }", "{ {x:2,y:1}:1, {x:1,y:1}:1 }", "{ {x:1,y:1}:1 }");

        // min
        assertEvaluates("{ {x:1}:3, {x:2}:5 }",
                        "min(tensor0, tensor1)", "{ {x:1}:3 }", "{ {x:2}:5 }");
        assertEvaluates("{ {x:1}:3 }",
                        "min(tensor0, tensor1)", "{ {x:1}:3 }", "{ {x:1}:5 }");
        assertEvaluates("{ {x:1}:3, {y:1}:5 }",
                        "min(tensor0, tensor1)", "{ {x:1}:3 }", "{ {y:1}:5 }");
        assertEvaluates("{ {x:1}:3, {x:2}:7, {y:1}:5 }",
                        "min(tensor0, tensor1)", "{ {x:1}:3, {x:2}:7 }", "{ {y:1}:5 }");
        assertEvaluates("{ {x:1,y:1}:1, {x:2,y:1}:3, {x:1,y:2}:5, {y:1,z:1}:7, {y:2,z:1}:11, {y:1,z:2}:13 }",
                        "min(tensor0, tensor1)", "{ {x:1,y:1}:1, {x:2,y:1}:3, {x:1,y:2}:5 }", "{ {y:1,z:1}:7, {y:2,z:1}:11, {y:1,z:2}:13 }");
        assertEvaluates("{ {x:1}:5, {x:1,y:1}:1, {y:1,z:1}:7 }",
                        "min(tensor0, tensor1)", "{ {x:1}:5, {x:1,y:1}:1 }", "{ {y:1,z:1}:7 }");
        assertEvaluates("{ {x:1}:5, {x:1,y:1}:1, {z:1}:11, {y:1,z:1}:7 }",
                        "min(tensor0, tensor1)", "{ {x:1}:5, {x:1,y:1}:1 }", "{ {z:1}:11, {y:1,z:1}:7 }");
        assertEvaluates("{ {}:5, {x:1,y:1}:1, {y:1,z:1}:7 }",
                        "min(tensor0, tensor1)", "{ {}:5, {x:1,y:1}:1 }", "{ {y:1,z:1}:7 }");
        assertEvaluates("{ {}:5, {x:1,y:1}:1,  {y:1,z:1}:7 }",
                        "min(tensor0, tensor1)", "{ {}:5, {x:1,y:1}:1 }", "{ {}:11, {y:1,z:1}:7 }");
        assertEvaluates("{ {}:5, {x:1}:3, {x:2}:4, {x:1,y:1}:1, {x:1,y:2}:6, {z:1,y:1,x:1}:10 }",
                        "min(tensor0, tensor1)", "{ {}:5, {x:1}:3, {x:2}:4, {x:1,y:1}:1, {x:1,y:2}:6 }", "{ {x:1}:5, {y:1,x:1}:7, {z:1,y:1,x:1}:10 }");

        // max
        assertEvaluates("{ {x:1}:3, {x:2}:5 }",
                        "max(tensor0, tensor1)", "{ {x:1}:3 }", "{ {x:2}:5 }");
        assertEvaluates("{ {x:1}:5 }",
                        "max(tensor0, tensor1)", "{ {x:1}:3 }", "{ {x:1}:5 }");
        assertEvaluates("{ {x:1}:3, {y:1}:5 }",
                        "max(tensor0, tensor1)", "{ {x:1}:3 }", "{ {y:1}:5 }");
        assertEvaluates("{ {x:1}:3, {x:2}:7, {y:1}:5 }",
                        "max(tensor0, tensor1)", "{ {x:1}:3, {x:2}:7 }", "{ {y:1}:5 }");
        assertEvaluates("{ {x:1,y:1}:1, {x:2,y:1}:3, {x:1,y:2}:5, {y:1,z:1}:7, {y:2,z:1}:11, {y:1,z:2}:13 }",
                        "max(tensor0, tensor1)", "{ {x:1,y:1}:1, {x:2,y:1}:3, {x:1,y:2}:5 }", "{ {y:1,z:1}:7, {y:2,z:1}:11, {y:1,z:2}:13 }");
        assertEvaluates("{ {x:1}:5, {x:1,y:1}:1, {y:1,z:1}:7 }",
                        "max(tensor0, tensor1)", "{ {x:1}:5, {x:1,y:1}:1 }", "{ {y:1,z:1}:7 }");
        assertEvaluates("{ {x:1}:5, {x:1,y:1}:1, {z:1}:11, {y:1,z:1}:7 }",
                        "max(tensor0, tensor1)", "{ {x:1}:5, {x:1,y:1}:1 }", "{ {z:1}:11, {y:1,z:1}:7 }");
        assertEvaluates("{ {}:5, {x:1,y:1}:1, {y:1,z:1}:7 }",
                        "max(tensor0, tensor1)", "{ {}:5, {x:1,y:1}:1 }", "{ {y:1,z:1}:7 }");
        assertEvaluates("{ {}:11, {x:1,y:1}:1,  {y:1,z:1}:7 }",
                        "max(tensor0, tensor1)", "{ {}:5, {x:1,y:1}:1 }", "{ {}:11, {y:1,z:1}:7 }");
        assertEvaluates("{ {}:5, {x:1}:5, {x:2}:4, {x:1,y:1}:7, {x:1,y:2}:6, {z:1,y:1,x:1}:10 }",
                        "max(tensor0, tensor1)", "{ {}:5, {x:1}:3, {x:2}:4, {x:1,y:1}:1, {x:1,y:2}:6 }", "{ {x:1}:5, {y:1,x:1}:7, {z:1,y:1,x:1}:10 }");

        // Combined
        assertEvaluates(String.valueOf(7.5 + 45 + 1.7),
                        "sum( " +                              // model computation:
                        "      tensor0 * tensor1 * tensor2 " + // - feature combinations
                        "      * tensor3" +                    // - model weights application
                        ") + 1.7",
                        "{ {x:1}:1, {x:2}:2 }", "{ {y:1}:3, {y:2}:4 }", "{ {z:1}:5 }",
                        "{ {x:1,y:1,z:1}:0.5, {x:2,y:1,z:1}:1.5, {x:1,y:1,z:2}:4.5 }");

        // undefined is not the same as 0
        assertEvaluates("1.0", "sum(tensor0 * tensor1 + 0.5)", "{ {x:1}:0, {x:2}:0 }", "{ {x:1}:1, {x:2}:1 }");
        assertEvaluates("0.0", "sum(tensor0 * tensor1 + 0.5)", "{}",                   "{ {x:1}:1, {x:2}:1 }");

        // tensor result dimensions are given from argument dimensions, not the resulting values
        assertEvaluates("tensor(x{}):{}", "tensor0 * tensor1", "{ {x:1}:1 }", "{ {x:2}:1 }");
        assertEvaluates("tensor(x{},y{}):{{x:1}:1.0}", "tensor0 * tensor1", "{ {x:1}:1 }", "{ {x:2,y:1}:1, {x:1}:1 }");
    }

    public void testProgrammaticBuildingAndPrecedence() {
        RankingExpression standardPrecedence = new RankingExpression(new ArithmeticNode(constant(2), ArithmeticOperator.PLUS, new ArithmeticNode(constant(3), ArithmeticOperator.MULTIPLY, constant(4))));
        RankingExpression oppositePrecedence = new RankingExpression(new ArithmeticNode(new ArithmeticNode(constant(2), ArithmeticOperator.PLUS, constant(3)), ArithmeticOperator.MULTIPLY, constant(4)));
        assertEquals(14.0, standardPrecedence.evaluate(null).asDouble());
        assertEquals(20.0, oppositePrecedence.evaluate(null).asDouble());
        assertEquals("2.0 + 3.0 * 4.0", standardPrecedence.toString());
        assertEquals("(2.0 + 3.0) * 4.0", oppositePrecedence.toString());
    }

    private ConstantNode constant(double value) {
        return new ConstantNode(new DoubleValue(value));
    }

    public void testStructuredVariableEvaluation() {
        Context context = new StructuredTestContext();
        //assertEvaluates(77,"average(6,8)+average(6,8).timesten",context);
        assertEvaluates(77, "average(\"2*3\",\"pow(2,3)\")+average(\"2*3\",\"pow(2,3)\").timesten", context);
    }

    private RankingExpression assertEvaluates(String expectedTensor, String expressionString, String ... tensorArguments) {
        MapContext context = defaultContext.thawedCopy();
        int argumentIndex = 0;
        for (String tensorArgument : tensorArguments)
            context.put("tensor" + (argumentIndex++), new TensorValue(MapTensor.from(tensorArgument)));
        return assertEvaluates(new TensorValue(MapTensor.from(expectedTensor)), expressionString, context);
    }

    /** Validate also that the dimension of the resulting tensors are as expected */
    private RankingExpression assertEvaluates_old(String tensorDimensions, String resultTensor, String expressionString) {
        RankingExpression expression = assertEvaluates(new TensorValue(MapTensor.from(resultTensor)), expressionString, defaultContext);
        TensorValue value = (TensorValue)expression.evaluate(defaultContext);
        assertEquals(toSet(tensorDimensions), value.asTensor().dimensions());
        assertEquals("String values are equals", resultTensor, expression.evaluate(defaultContext).toString());
        return expression;
    }

    private RankingExpression assertEvaluates(Value value, String expressionString) {
        return assertEvaluates(value, expressionString, defaultContext);
    }

    private RankingExpression assertEvaluates(double value, String expressionString) {
        return assertEvaluates(value, expressionString, defaultContext);
    }

    private RankingExpression assertEvaluates(double value, String expressionString, Context context) {
        return assertEvaluates(new DoubleValue(value), expressionString, context);
    }

    private RankingExpression assertEvaluates(Value value, String expressionString, Context context) {
        try {
            RankingExpression expression = new RankingExpression(expressionString);
            assertEquals(expression.toString(), value, expression.evaluate(context));
            return expression;
        }
        catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /** Turns a comma-separated string into a set of string values */
    private Set<String> toSet(String values) {
        Set<String> set = new HashSet<>();
        for (String value : values.split(","))
            set.add(value.trim());
        return set;
    }

    private static class StructuredTestContext extends MapContext {

        @Override
        public Value get(String name, Arguments arguments, String output) {
            if (!name.equals("average")) {
                throw new IllegalArgumentException("Unknown operation '" + name + "'");
            }
            if (arguments.expressions().size() != 2) {
                throw new IllegalArgumentException("'average' takes 2 arguments");
            }
            if (output != null && !output.equals("timesten")) {
                throw new IllegalArgumentException("Unknown 'average' output '" + output + "'");
            }

            Value result = evaluateStringAsExpression(0, arguments).add(evaluateStringAsExpression(1, arguments)).divide(new DoubleValue(2));
            if ("timesten".equals(output)) {
                result = result.multiply(new DoubleValue(10));
            }
            return result;
        }

        private Value evaluateStringAsExpression(int index, Arguments arguments) {
            try {
                ExpressionNode e = arguments.expressions().get(index);
                if (e instanceof ConstantNode) {
                    return new DoubleValue(new RankingExpression(UnicodeUtilities.unquote(((ConstantNode)e).sourceString())).evaluate(this).asDouble());
                }
                return e.evaluate(this);
            }
            catch (ParseException e) {
                throw new RuntimeException("Could not evaluate argument '" + index + "'", e);
            }
        }

    }

}
