// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.javacc.UnicodeUtilities;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.ArithmeticNode;
import com.yahoo.searchlib.rankingexpression.rule.ArithmeticOperator;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.IfNode;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests expression evaluation
 *
 * @author bratseth
 */
public class EvaluationTestCase {

    private double tolerance = 0.000001;

    @Test
    public void testEvaluation() {
        EvaluationTester tester = new EvaluationTester();
        tester.assertEvaluates(0.5, "0.5");
        tester.assertEvaluates(-0.5, "-0.5");
        tester.assertEvaluates(0.5, "one_half");
        tester.assertEvaluates(-0.5, "-one_half");
        tester.assertEvaluates(0, "nonexisting");
        tester.assertEvaluates(0.75, "0.5 + 0.25");
        tester.assertEvaluates(0.75, "one_half + a_quarter");
        tester.assertEvaluates(1.25, "0.5 - 0.25 + one");
        tester.assertEvaluates(9.0, "3 ^ 2");

        // String
        tester.assertEvaluates(1, "if(\"a\"==\"a\",1,0)");

        // Precedence
        tester.assertEvaluates(26, "2*3+4*5");
        tester.assertEvaluates(1, "2/6+4/6");
        tester.assertEvaluates(2 * 3 * 4 + 3 * 4 * 5 - 4 * 200 / 10, "2*3*4+3*4*5-4*200/10");
        tester.assertEvaluates(3, "1 + 10 % 6 / 2");
        tester.assertEvaluates(10.0, "3 ^ 2 + 1");
        tester.assertEvaluates(18.0, "2 * 3 ^ 2");

        // Conditionals
        tester.assertEvaluates(2 * (3 * 4 + 3) * (4 * 5 - 4 * 200) / 10, "2*(3*4+3)*(4*5-4*200)/10");
        tester.assertEvaluates(0.5, "if( 2<3, one_half, one_quarter)");
        tester.assertEvaluates(0.25,"if( 2>3, one_half, a_quarter)");
        tester.assertEvaluates(0.5, "if( 1==1, one_half, a_quarter)");
        tester.assertEvaluates(0.5, "if( 1<=1, one_half, a_quarter)");
        tester.assertEvaluates(0.5, "if( 1<=1.1, one_half, a_quarter)");
        tester.assertEvaluates(0.25,"if( 1>=1.1, one_half, a_quarter)");
        tester.assertEvaluates(0.5, "if( 0.33333333333333333333~=1/3, one_half, a_quarter)");
        tester.assertEvaluates(0.25,"if( 0.33333333333333333333~=1/35, one_half, a_quarter)");
        tester.assertEvaluates(5.5, "if(one_half in [one_quarter,one_half],  one_half+5,log(one_quarter) * one_quarter)");
        tester.assertEvaluates(0.5, "if( 1 in [1,2 , 3], one_half, a_quarter)");
        tester.assertEvaluates(0.25,"if( 1 in [  2,3,4], one_half, a_quarter)");
        tester.assertEvaluates(0.5, "if( \"foo\" in [\"foo\",\"bar\"], one_half, a_quarter)");
        tester.assertEvaluates(0.5, "if( foo in [\"foo\",\"bar\"], one_half, a_quarter)");
        tester.assertEvaluates(0.5, "if( \"foo\" in [foo,\"bar\"], one_half, a_quarter)");
        tester.assertEvaluates(0.5, "if( foo in [foo,\"bar\"], one_half, a_quarter)");
        tester.assertEvaluates(0.25,"if( \"foo\" in [\"baz\",\"boz\"], one_half, a_quarter)");
        tester.assertEvaluates(0.5, "if( one in [0, 1, 2], one_half, a_quarter)");
        tester.assertEvaluates(0.25,"if( one in [2], one_half, a_quarter)");
        tester.assertEvaluates(2.5, "if(1.0, 2.5, 3.5)");
        tester.assertEvaluates(3.5, "if(0.0, 2.5, 3.5)");
        tester.assertEvaluates(2.5, "if(1.0-1.1, 2.5, 3.5)");
        tester.assertEvaluates(3.5, "if(1.0-1.0, 2.5, 3.5)");

        // Conditionals with branch probabilities
        RankingExpression e = tester.assertEvaluates(3.5, "if(1.0-1.0, 2.5, 3.5, 0.3)");
        assertEquals(0.3d, (double)((IfNode) e.getRoot()).getTrueProbability(), tolerance);

        // Conditionals as expressions
        tester.assertEvaluates(new BooleanValue(true), "2<3");
        tester.assertEvaluates(new BooleanValue(false), "2>3");
        tester.assertEvaluates(new BooleanValue(false), "if (3>2, 2>3, 5.0)");
        tester.assertEvaluates(new BooleanValue(true), "2>3<1"); // The result of 2>3 is converted to 0, which is <1
        tester.assertEvaluates(2.5, "if(2>3<1, 2.5, 3.5)");
        tester.assertEvaluates(2.5, "if(1+1>3<1+0, 2.5, 3.5)");

        // Functions
        tester.assertEvaluates(0, "sin(0)");
        tester.assertEvaluates(1, "cos(0)");
        tester.assertEvaluates(8, "pow(4/2,min(cos(0)*3,5))");

        // Random feature (which is also a tensor function) (We expect to be able to parse it and look up a zero)
        tester.assertEvaluates(0, "random(1)");
        tester.assertEvaluates(0, "random(foo)");

        // Combined
        tester.assertEvaluates(1.25, "5*if(1>=1.1, one_half, if(min(1,2)<max(1,2),if (\"foo\" in [\"foo\",\"bar\"],a_quarter,3000), 0.57345347))");
    }

    @Test
    public void testBooleanEvaluation() {
        EvaluationTester tester = new EvaluationTester();

        // and
        tester.assertEvaluates(false, "0 && 0");
        tester.assertEvaluates(false, "0 && 1");
        tester.assertEvaluates(false, "1 && 0");
        tester.assertEvaluates(true, "1 && 1");
        tester.assertEvaluates(true, "1 && 2");
        tester.assertEvaluates(true, "1 && 0.1");

        // or
        tester.assertEvaluates(false, "0 || 0");
        tester.assertEvaluates(true, "0 || 0.1");
        tester.assertEvaluates(true, "0 || 1");
        tester.assertEvaluates(true, "1 || 0");
        tester.assertEvaluates(true, "1 || 1");

        // not
        tester.assertEvaluates(true, "!0");
        tester.assertEvaluates(false, "!1");
        tester.assertEvaluates(false, "!2");
        tester.assertEvaluates(true, "!0 && 1");

        // precedence
        tester.assertEvaluates(0, "2 * (0 && 1)");
        tester.assertEvaluates(2, "2 * (1 && 1)");
        tester.assertEvaluates(true, "2 + 0 && 1");
        tester.assertEvaluates(true, "1 && 0 + 2");
    }

    @Test
    public void testTensorEvaluation() {
        EvaluationTester tester = new EvaluationTester();
        tester.assertEvaluates("{}", "tensor0", "{}");

        // tensor map
        tester.assertEvaluates("{ {d1:0}:1, {d1:1}:2, {d1:2 }:3 }",
                              "map(tensor0, f(x) (log10(x)))", "{ {d1:0}:10, {d1:1}:100, {d1:2}:1000 }");
        tester.assertEvaluates("{ {d1:0}:4, {d1:1}:9, {d1:2 }:16 }",
                              "map(tensor0, f(x) (x * x))", "{ {d1:0}:2, {d1:1}:3, {d1:2}:4 }");
        // -- tensor map composites
        tester.assertEvaluates("{ {d1:0}:1, {d1:1}:2, {d1:2 }:3 }",
                               "log10(tensor0)", "{ {d1:0}:10, {d1:1}:100, {d1:2}:1000 }");
        tester.assertEvaluates("{ {d1:0}:-10, {d1:1}:-100, {d1:2 }:-1000 }",
                               "- tensor0", "{ {d1:0}:10, {d1:1}:100, {d1:2}:1000 }");
        tester.assertEvaluates("{ {d1:0}:-10, {d1:1}:0, {d1:2 }:0 }",
                               "min(tensor0, 0)", "{ {d1:0}:-10, {d1:1}:0, {d1:2}:10 }");
        tester.assertEvaluates("{ {d1:0}:0, {d1:1}:0, {d1:2 }:10 }",
                               "max(tensor0, 0)", "{ {d1:0}:-10, {d1:1}:0, {d1:2}:10 }");
        // operators
        tester.assertEvaluates("{ {d1:0}:1, {d1:1}:1, {d1:2 }:1 }",
                               "tensor0 % 2 == map(tensor0, f(x) (x % 2))", "{ {d1:0}:2, {d1:1}:3, {d1:2}:4 }");
        tester.assertEvaluates("{ {d1:0}:1, {d1:1}:1, {d1:2 }:1 }",
                               "tensor0 || 1 == map(tensor0, f(x) (x || 1))", "{ {d1:0}:2, {d1:1}:3, {d1:2}:4 }");
        tester.assertEvaluates("{ {d1:0}:1, {d1:1}:1, {d1:2 }:1 }",
                               "tensor0 && 1 == map(tensor0, f(x) (x && 1))", "{ {d1:0}:2, {d1:1}:3, {d1:2}:4 }");
        tester.assertEvaluates("{ {d1:0}:1, {d1:1}:1, {d1:2 }:1 }",
                               "!tensor0 == map(tensor0, f(x) (!x))", "{ {d1:0}:0, {d1:1}:1, {d1:2}:0 }");

        // -- explicitly implemented functions (not foolproof tests as we don't bother testing float value equivalence)
        tester.assertEvaluates("{ {x:0}:1, {x:1}:2 }",     "abs(tensor0)",    "{ {x:0}:1, {x:1}:-2 }");
        tester.assertEvaluates("{ {x:0}:0, {x:1}:0 }",     "acos(tensor0)",   "{ {x:0}:1, {x:1}:1 }");
        tester.assertEvaluates("{ {x:0}:0, {x:1}:0 }",     "asin(tensor0)",   "{ {x:0}:0, {x:1}:0 }");
        tester.assertEvaluates("{ {x:0}:0, {x:1}:0 }",     "atan(tensor0)",   "{ {x:0}:0, {x:1}:0 }");
        tester.assertEvaluates("{ {x:0}:1, {x:1}:2 }",     "ceil(tensor0)",   "{ {x:0}:1, {x:1}:2 }");
        tester.assertEvaluates("{ {x:0}:1, {x:1}:1 }",     "cos(tensor0)",    "{ {x:0}:0, {x:1}:0 }");
        tester.assertEvaluates("{ {x:0}:1, {x:1}:1 }",     "cosh(tensor0)",   "{ {x:0}:0, {x:1}:0 }");
        tester.assertEvaluates("{ {x:0}:1, {x:1}:2 }",     "elu(tensor0)",    "{ {x:0}:1, {x:1}:2 }");
        tester.assertEvaluates("{ {x:0}:1, {x:1}:1 }",     "exp(tensor0)",    "{ {x:0}:0, {x:1}:0 }");
        tester.assertEvaluates("{ {x:0}:1, {x:1}:2 }",     "fabs(tensor0)",   "{ {x:0}:1, {x:1}:2 }");
        tester.assertEvaluates("{ {x:0}:1, {x:1}:2 }",     "floor(tensor0)",  "{ {x:0}:1, {x:1}:2 }");
        tester.assertEvaluates("{ {x:0}:0, {x:1}:0 }",     "isNan(tensor0)",  "{ {x:0}:1, {x:1}:2 }");
        tester.assertEvaluates("{ {x:0}:0, {x:1}:0 }",     "log(tensor0)",    "{ {x:0}:1, {x:1}:1 }");
        tester.assertEvaluates("{ {x:0}:0, {x:1}:1 }",     "log10(tensor0)",  "{ {x:0}:1, {x:1}:10 }");
        tester.assertEvaluates("{ {x:0}:0, {x:1}:2 }",     "fmod(tensor0, 3)","{ {x:0}:3, {x:1}:8 }");
        tester.assertEvaluates("{ {x:0}:1, {x:1}:8 }",     "pow(tensor0, 3)", "{ {x:0}:1, {x:1}:2 }");
        tester.assertEvaluates("{ {x:0}:8, {x:1}:16 }",    "ldexp(tensor0,3.1)","{ {x:0}:1, {x:1}:2 }");
        tester.assertEvaluates("{ {x:0}:1, {x:1}:2 }",     "relu(tensor0)",   "{ {x:0}:1, {x:1}:2 }");
        tester.assertEvaluates("{ {x:0}:1, {x:1}:2 }",     "round(tensor0)",  "{ {x:0}:1, {x:1}:1.8 }");
        tester.assertEvaluates("{ {x:0}:0.5, {x:1}:0.5 }", "sigmoid(tensor0)","{ {x:0}:0, {x:1}:0 }");
        tester.assertEvaluates("{ {x:0}:1, {x:1}:-1 }",    "sign(tensor0)",   "{ {x:0}:3, {x:1}:-5 }");
        tester.assertEvaluates("{ {x:0}:0, {x:1}:0 }",     "sin(tensor0)",    "{ {x:0}:0, {x:1}:0 }");
        tester.assertEvaluates("{ {x:0}:0, {x:1}:0 }",     "sinh(tensor0)",   "{ {x:0}:0, {x:1}:0 }");
        tester.assertEvaluates("{ {x:0}:1, {x:1}:4 }",     "square(tensor0)", "{ {x:0}:1, {x:1}:2 }");
        tester.assertEvaluates("{ {x:0}:1, {x:1}:3 }",     "sqrt(tensor0)",   "{ {x:0}:1, {x:1}:9 }");
        tester.assertEvaluates("{ {x:0}:0, {x:1}:0 }",     "tan(tensor0)",    "{ {x:0}:0, {x:1}:0 }");
        tester.assertEvaluates("{ {x:0}:0, {x:1}:0 }",     "tanh(tensor0)",   "{ {x:0}:0, {x:1}:0 }");

        // tensor reduce
        // -- reduce 2 dimensions
        tester.assertEvaluates("{ {}:4 }",
                               "reduce(tensor0, avg, x, y)",   "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");
        tester.assertEvaluates("{ {}:4 }",
                               "reduce(tensor0, count, x, y)", "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");
        tester.assertEvaluates("{ {}:105 }",
                               "reduce(tensor0, prod, x, y)",  "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");
        tester.assertEvaluates("{ {}:16 }",
                               "reduce(tensor0, sum, x, y)",   "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");
        tester.assertEvaluates("{ {}:7 }",
                               "reduce(tensor0, max, x, y)",   "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");
        tester.assertEvaluates("{ {}:1 }",
                               "reduce(tensor0, min, x, y)",   "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");
        // -- reduce 2 by specifying no arguments
        tester.assertEvaluates("{ {}:4 }",
                               "reduce(tensor0, avg)",   "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");
        // -- reduce 1 dimension
        tester.assertEvaluates("{ {y:0}:2, {y:1}:6 }",
                               "reduce(tensor0, avg, x)",   "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");
        tester.assertEvaluates("{ {y:0}:2, {y:1}:2 }",
                               "reduce(tensor0, count, x)", "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");
        tester.assertEvaluates("{ {y:0}:3, {y:1}:35 }",
                               "reduce(tensor0, prod, x)",  "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");
        tester.assertEvaluates("{ {y:0}:4, {y:1}:12 }",
                               "reduce(tensor0, sum, x)",   "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");
        tester.assertEvaluates("{ {y:0}:3, {y:1}:7 }",
                               "reduce(tensor0, max, x)",   "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");
        tester.assertEvaluates("{ {y:0}:1, {y:1}:5 }",
                               "reduce(tensor0, min, x)",   "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");
        // -- reduce composites
        tester.assertEvaluates("{ {}: 5   }", "sum(tensor0)", "5.0");
        tester.assertEvaluates("{ {}:-5   }", "sum(tensor0)", "-5.0");
        tester.assertEvaluates("{ {}:12.5 }", "sum(tensor0)", "{ {d1:0}:5.5, {d1:1}:7.0 }");
        tester.assertEvaluates("{ {}: 0   }", "sum(tensor0)", "{ {d1:0}:5.0, {d1:1}:7.0, {d1:2}:-12.0}");
        tester.assertEvaluates("{ {y:0}:4, {y:1}:12.0 }",
                               "sum(tensor0, x)", "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");
        tester.assertEvaluates("{ {x:0}:6, {x:1}:10.0 }",
                               "sum(tensor0, y)", "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");
        tester.assertEvaluates("{ {}:16 }",
                               "sum(tensor0, x, y)", "{ {x:0,y:0}:1.0, {x:1,y:0}:3.0, {x:0,y:1}:5.0, {x:1,y:1}:7.0 }");

        // tensor join
        tester.assertEvaluates("{ {x:0,y:0}:15, {x:1,y:0}:35 }", "join(tensor0, tensor1, f(x,y) (x*y))", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:5 }");
        // -- join composites
        tester.assertEvaluates("{ }", "tensor0 * tensor0", "{}");
        tester.assertEvaluates("{{x:0,y:0,z:0}:0.0}", "( tensor0 * tensor1 ) * ( tensor2 * tensor1 )",
                               "{{x:0}:1}", "{}", "{{y:0,z:0}:1}");
        tester.assertEvaluates("tensor(x{}):{}",
                               "tensor0 * tensor1", "{ {x:0}:3 }", "tensor(x{}):{ {x:1}:5 }");
        tester.assertEvaluates("{ {x:0}:15 }",
                               "tensor0 * tensor1", "{ {x:0}:3 }", "{ {x:0}:5 }");
        tester.assertEvaluates("{ {x:0,y:0}:15 }",
                               "tensor0 * tensor1", "{ {x:0}:3 }", "{ {y:0}:5 }");
        tester.assertEvaluates("{ {x:0,y:0}:15, {x:1,y:0}:35 }",
                               "tensor0 * tensor1", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:5 }");
        tester.assertEvaluates("{ {x:0,y:0}:8, {x:1,y:0}:12 }",
                               "tensor0 + tensor1", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:5 }");
        tester.assertEvaluates("{ {x:0,y:0}:-2, {x:1,y:0}:2 }",
                               "tensor0 - tensor1", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:5 }");
        tester.assertEvaluates("{ {x:0,y:0}:5, {x:1,y:0}:4 }",
                               "tensor0 / tensor1", "{ {x:0}:15, {x:1}:12 }", "{ {y:0}:3 }");
        tester.assertEvaluates("{ {x:0,y:0}:5, {x:1,y:0}:7 }",
                               "max(tensor0, tensor1)", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:5 }");
        tester.assertEvaluates("{ {x:0,y:0}:3, {x:1,y:0}:5 }",
                               "min(tensor0, tensor1)", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:5 }");
        tester.assertEvaluates("{ {x:0,y:0}:243, {x:1,y:0}:16807 }",
                               "pow(tensor0, tensor1)", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:5 }");
        tester.assertEvaluates("{ {x:0,y:0}:243, {x:1,y:0}:16807 }",
                               "tensor0 ^ tensor1", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:5 }");
        tester.assertEvaluates("{ {x:0,y:0}:3, {x:1,y:0}:2 }",
                               "fmod(tensor0, tensor1)", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:5 }");
        tester.assertEvaluates("{ {x:0,y:0}:3, {x:1,y:0}:2 }",
                               "tensor0 % tensor1", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:5 }");
        tester.assertEvaluates("{ {x:0,y:0}:96, {x:1,y:0}:224 }",
                               "ldexp(tensor0, tensor1)", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:5.1 }");
        tester.assertEvaluates("{ {x:0,y:0,z:0}:7, {x:0,y:0,z:1}:13, {x:1,y:0,z:0}:21, {x:1,y:0,z:1}:39, {x:0,y:1,z:0}:55, {x:0,y:1,z:1}:0, {x:1,y:1,z:0}:0, {x:1,y:1,z:1}:0 }",
                               "tensor0 * tensor1", "{ {x:0,y:0}:1, {x:1,y:0}:3, {x:0,y:1}:5, {x:1,y:1}:0 }", "{ {y:0,z:0}:7, {y:1,z:0}:11, {y:0,z:1}:13, {y:1,z:1}:0 }");
        tester.assertEvaluates("{ {x:0,y:1,z:0}:35, {x:0,y:1,z:1}:65 }",
                               "tensor0 * tensor1", "tensor(x{},y{}):{ {x:0,y:0}:1, {x:1,y:0}:3, {x:0,y:1}:5 }", "tensor(y{},z{}):{ {y:1,z:0}:7, {y:2,z:0}:11, {y:1,z:1}:13 })");
        tester.assertEvaluates("{{x:0,y:0}:0.0}","tensor1 * tensor2 * tensor3", "{ {x:0}:1 }", "{ {x:1,y:0}:1, {x:0,y:0}:1 }", "{ {x:0,y:0}:1 }");
        tester.assertEvaluates("{ {d1:0}:50, {d1:1}:500, {d1:2}:5000 }",
                               "5 * tensor0", "{ {d1:0}:10, {d1:1}:100, {d1:2}:1000 }");
        tester.assertEvaluates("{ {d1:0}:13, {d1:1}:103, {d1:2}:1003 }",
                               "tensor0 + 3","{ {d1:0}:10, {d1:1}:100, {d1:2}:1000 }");
        tester.assertEvaluates("{ {d1:0}:1, {d1:1}:10, {d1:2 }:100 }",
                               "tensor0 / 10", "{ {d1:0}:10, {d1:1}:100, {d1:2}:1000 }");
        tester.assertEvaluates("{ {h:0}:1.5, {h:1}:1.5 }", "0.5 + tensor0", "{ {h:0}:1.0,{h:1}:1.0 }");
        tester.assertEvaluates("{ {x:0,y:0}:0, {x:1,y:0}:0 }",
                               "atan2(tensor0, tensor1)", "{ {x:0}:0, {x:1}:0 }", "{ {y:0}:1 }");
        tester.assertEvaluates("{ {x:0,y:0}:0, {x:1,y:0}:1 }",
                               "tensor0 > tensor1", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:5 }");
        tester.assertEvaluates("{ {x:0,y:0}:1, {x:1,y:0}:0 }",
                               "tensor0 < tensor1", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:5 }");
        tester.assertEvaluates("{ {x:0,y:0}:0, {x:1,y:0}:1 }",
                               "tensor0 >= tensor1", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:5 }");
        tester.assertEvaluates("{ {x:0,y:0}:1, {x:1,y:0}:0 }",
                               "tensor0 <= tensor1", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:5 }");
        tester.assertEvaluates("{ {x:0,y:0}:0, {x:1,y:0}:1 }",
                               "tensor0 == tensor1", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:7 }");
        tester.assertEvaluates("{ {x:0,y:0}:0, {x:1,y:0}:1 }",
                               "tensor0 ~= tensor1", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:7 }");
        tester.assertEvaluates("{ {x:0,y:0}:1, {x:1,y:0}:0 }",
                               "tensor0 != tensor1", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:7 }");
        tester.assertEvaluates("{ {x:0}:1, {x:1}:0 }",
                               "tensor0 in [1,2,3]", "{ {x:0}:3, {x:1}:7 }");
        tester.assertEvaluates("{ {x:0}:0.1 }", "join(tensor0, 0.1, f(x,y) (x*y))", "{ {x:0}:1 }");

        // TODO
        // argmax
        // argmin
        tester.assertEvaluates("{ {x:0,y:0}:1, {x:1,y:0}:0 }",
                               "tensor0 != tensor1", "{ {x:0}:3, {x:1}:7 }", "{ {y:0}:7 }");

        // tensor rename
        tester.assertEvaluates("{ {newX:0,y:0}:3 }", "rename(tensor0, x, newX)", "{ {x:0,y:0}:3.0 }");
        tester.assertEvaluates("{  {x:0,y:0}:3, {x:1,y:0}:5 }", "rename(tensor0, (x, y), (y, x))", "{ {x:0,y:0}:3.0, {x:0,y:1}:5.0 }");

        // tensor generate
        tester.assertEvaluates("{ {x:0,y:0}:0, {x:1,y:0}:0, {x:0,y:1}:1, {x:1,y:1}:0, {x:0,y:2}:0, {x:1,y:2}:1 }", "tensor(x[2],y[3])(x+1==y)");
        tester.assertEvaluates("{ {y:0,x:0}:0, {y:1,x:0}:0, {y:0,x:1}:1, {y:1,x:1}:0, {y:0,x:2}:0, {y:1,x:2}:1 }", "tensor(y[2],x[3])(y+1==x)");
        tester.assertEvaluates("{ {x:0,y:0,z:0}:1 }", "tensor(x[1],y[1],z[1])((x==y)*(y==z))");
        // - generate composites
        tester.assertEvaluates("{ {x:0}:0, {x:1}:1, {x:2}:2 }", "range(x[3])");
        tester.assertEvaluates("{ {x:0,y:0,z:0}:1, {x:0,y:0,z:1}:0, {x:0,y:1,z:0}:0, {x:0,y:1,z:1}:0, {x:1,y:0,z:0}:0, {x:1,y:0,z:1}:0, {x:1,y:1,z:0}:0, {x:1,y:1,z:1}:1, }", "diag(x[2],y[2],z[2])");
        tester.assertEvaluates("6", "reduce(random(x[2],y[3]), count)");

        // composite functions
        tester.assertEvaluates("{ {x:0}:0.25, {x:1}:0.75 }", "l1_normalize(tensor0, x)", "{ {x:0}:1, {x:1}:3 }");
        tester.assertEvaluates("{ {x:0}:0.31622776601683794, {x:1}:0.9486832980505138 }", "l2_normalize(tensor0, x)", "{ {x:0}:1, {x:1}:3 }");
        tester.assertEvaluates("{ {y:0}:81.0 }", "matmul(tensor0, tensor1, x)", "{ {x:0}:15, {x:1}:12 }", "{ {y:0}:3 }");
        tester.assertEvaluates("{ {x:0}:0.5, {x:1}:0.5 }", "softmax(tensor0, x)", "{ {x:0}:1, {x:1}:1 }", "{ {y:0}:1 }");
        tester.assertEvaluates("{ {x:0,y:0}:81.0, {x:1,y:0}:88.0 }", "xw_plus_b(tensor0, tensor1, tensor2, x)", "{ {x:0}:15, {x:1}:12 }", "{ {y:0}:3 }", "{ {x:0}:0, {x:1}:7 }");
        tester.assertEvaluates("{ {x:0}:1, {x:1}:0, {x:2}:0, {x:3}:1 }", "argmax(tensor0, x)", "{ {x:0}:15, {x:1}:12, {x:2}:7, {x:3}:15 }");
        tester.assertEvaluates("{ {x:0}:0, {x:1}:0, {x:2}:1, {x:3}:0 }", "argmin(tensor0, x)", "{ {x:0}:15, {x:1}:12, {x:2}:7, {x:3}:15 }");

        // expressions combining functions
        tester.assertEvaluates("tensor(y{}):{{y:6}:0}}", "matmul(tensor0, diag(x[5],y[7]), x)", "tensor(x{},y{}):{{x:4,y:6}:1})");
        tester.assertEvaluates("tensor(y{}):{{y:6}:10}}", "matmul(tensor0, range(x[5],y[7]), x)", "tensor(x{},y{}):{{x:4,y:6}:1})");
        tester.assertEvaluates(String.valueOf(7.5 + 45 + 1.7),
                               "sum( " +               // model computation:
                               "      tensor0 * tensor1 * tensor2 " + // - feature combinations
                               "      * tensor3" +                    // - model weights application
                               ") + 1.7",
                               "{ {x:0}:1, {x:1}:2 }", "{ {y:0}:3, {y:1}:4 }", "{ {z:0}:5 }",
                               "{ {x:0,y:0,z:0}:0.5, {x:1,y:0,z:0}:1.5, {x:0,y:0,z:1}:4.5, {x:0,y:1,z:0}:0, {x:1,y:0,z:1}:0, {x:0,y:1,z:1}:0, {x:1,y:1,z:0}:0, {x:1,y:1,z:1}:0 }");
        tester.assertEvaluates("1.0", "sum(tensor0 * tensor1 + 0.5)", "{ {x:0}:0, {x:1}:0 }", "{ {x:0}:1, {x:1}:1 }");
        tester.assertEvaluates("1.0", "sum(tensor0 * tensor1 + 0.5)", "{}",                  "{ {x:0}:1, {x:1}:1 }");
        tester.assertEvaluates("0.0", "sum(tensor0 * tensor1 + 0.5)", "tensor(x{}):{}",                  "{ {x:0}:1, {x:1}:1 }");

        tester.assertEvaluates("1",
                               "reduce(join(tensor0, tensor1, f(x,y) (if(x > y, 1.0, 0.0))), sum, tag) == reduce(tensor0, count, tag)",
                               "tensor(tag{}):{{tag:tag1}:10, {tag:tag2}:20}", "{5}");
        tester.assertEvaluates("0",
                               "reduce(join(tensor0, tensor1, f(x,y) (if(x > y, 1.0, 0.0))), sum, tag) == reduce(tensor0, count, tag)",
                               "tensor(tag{}):{{tag:tag1}:10, {tag:tag2}:20}", "{15}");
        tester.assertEvaluates("0",
                               "reduce(join(tensor0, tensor1, f(x,y) (if(x > y, 1.0, 0.0))), sum, tag) == reduce(tensor0, count, tag)",
                               "tensor(tag{}):{{tag:tag1}:10, {tag:tag2}:20}", "{25}");

        // tensor result dimensions are given from argument dimensions, not the resulting values
        tester.assertEvaluates("tensor(x{}):{}", "tensor0 * tensor1", "{ {x:0}:1 }", "tensor(x{}):{ {x:1}:1 }");
        tester.assertEvaluates("tensor(x{},y{}):{}", "tensor0 * tensor1", "{ {x:0}:1 }", "tensor(x{},y{}):{ {x:1,y:0}:1, {x:2,y:1}:1 }");
    }

    @Test
    public void testProgrammaticBuildingAndPrecedence() {
        RankingExpression standardPrecedence = new RankingExpression(new ArithmeticNode(constant(2), ArithmeticOperator.PLUS, new ArithmeticNode(constant(3), ArithmeticOperator.MULTIPLY, constant(4))));
        RankingExpression oppositePrecedence = new RankingExpression(new ArithmeticNode(new ArithmeticNode(constant(2), ArithmeticOperator.PLUS, constant(3)), ArithmeticOperator.MULTIPLY, constant(4)));
        assertEquals(14.0, standardPrecedence.evaluate(null).asDouble(), tolerance);
        assertEquals(20.0, oppositePrecedence.evaluate(null).asDouble(), tolerance);
        assertEquals("2.0 + 3.0 * 4.0", standardPrecedence.toString());
        assertEquals("(2.0 + 3.0) * 4.0", oppositePrecedence.toString());
    }

    @Test
    public void testStructuredVariableEvaluation() {
        EvaluationTester tester = new EvaluationTester();
        Context context = new StructuredTestContext();
        //assertEvaluates(77,"average(6,8)+average(6,8).timesten",context);
        tester.assertEvaluates(77, "average(\"2*3\",\"pow(2,3)\")+average(\"2*3\",\"pow(2,3)\").timesten", context);
    }

    private ConstantNode constant(double value) {
        return new ConstantNode(new DoubleValue(value));
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
