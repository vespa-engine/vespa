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
        assertEvaluates("{}", "tensor0", "{}");

        // tensor map
        assertEvaluates("{ {d1:l1}:1, {d1:l2}:2, {d1:l3 }:3 }",
                        "map(tensor0, f(x) (log10(x)))", "{ {d1:l1}:10, {d1:l2}:100, {d1:l3}:1000 }");
        assertEvaluates("{ {d1:l1}:4, {d1:l2}:9, {d1:l3 }:16 }",
                        "map(tensor0, f(x) (x * x))", "{ {d1:l1}:2, {d1:l2}:3, {d1:l3}:4 }");
        // -- tensor map composites
        assertEvaluates("{ {d1:l1}:1, {d1:l2}:2, {d1:l3 }:3 }", 
                        "log10(tensor0)", "{ {d1:l1}:10, {d1:l2}:100, {d1:l3}:1000 }");
        assertEvaluates("{ {d1:l1}:-10, {d1:l2}:-100, {d1:l3 }:-1000 }",
                        "- tensor0", "{ {d1:l1}:10, {d1:l2}:100, {d1:l3}:1000 }");
        assertEvaluates("{ {d1:l1}:-10, {d1:l2}:0, {d1:l3 }:0 }",
                        "min(tensor0, 0)", "{ {d1:l1}:-10, {d1:l2}:0, {d1:l3}:10 }");
        assertEvaluates("{ {d1:l1}:0, {d1:l2}:0, {d1:l3 }:10 }",
                        "max(tensor0, 0)", "{ {d1:l1}:-10, {d1:l2}:0, {d1:l3}:10 }");
        // -- explicitly implemented functions (not foolproof tests as we don't bother testing float value equivalence)
        assertEvaluates("{ {x:1}:1, {x:2}:2 }",     "abs(tensor0)",    "{ {x:1}:1, {x:2}:-2 }");
        assertEvaluates("{ {x:1}:0, {x:2}:0 }",     "acos(tensor0)",   "{ {x:1}:1, {x:2}:1 }");
        assertEvaluates("{ {x:1}:0, {x:2}:0 }",     "asin(tensor0)",   "{ {x:1}:0, {x:2}:0 }");
        assertEvaluates("{ {x:1}:0, {x:2}:0 }",     "atan(tensor0)",   "{ {x:1}:0, {x:2}:0 }");
        assertEvaluates("{ {x:1}:1, {x:2}:2 }",     "ceil(tensor0)",   "{ {x:1}:1, {x:2}:2 }");
        assertEvaluates("{ {x:1}:1, {x:2}:1 }",     "cos(tensor0)",    "{ {x:1}:0, {x:2}:0 }");
        assertEvaluates("{ {x:1}:1, {x:2}:1 }",     "cosh(tensor0)",   "{ {x:1}:0, {x:2}:0 }");
        assertEvaluates("{ {x:1}:1, {x:2}:2 }",     "elu(tensor0)",    "{ {x:1}:1, {x:2}:2 }");
        assertEvaluates("{ {x:1}:1, {x:2}:1 }",     "exp(tensor0)",    "{ {x:1}:0, {x:2}:0 }");
        assertEvaluates("{ {x:1}:1, {x:2}:2 }",     "fabs(tensor0)",   "{ {x:1}:1, {x:2}:2 }");
        assertEvaluates("{ {x:1}:1, {x:2}:2 }",     "floor(tensor0)",  "{ {x:1}:1, {x:2}:2 }");
        assertEvaluates("{ {x:1}:0, {x:2}:0 }",     "isNan(tensor0)",  "{ {x:1}:1, {x:2}:2 }");
        assertEvaluates("{ {x:1}:0, {x:2}:0 }",     "log(tensor0)",    "{ {x:1}:1, {x:2}:1 }");
        assertEvaluates("{ {x:1}:0, {x:2}:1 }",     "log10(tensor0)",  "{ {x:1}:1, {x:2}:10 }");
        assertEvaluates("{ {x:1}:0, {x:2}:2 }",     "mod(tensor0, 3)", "{ {x:1}:3, {x:2}:8 }");
        assertEvaluates("{ {x:1}:1, {x:2}:8 }",     "pow(tensor0, 3)", "{ {x:1}:1, {x:2}:2 }");
        assertEvaluates("{ {x:1}:1, {x:2}:2 }",     "relu(tensor0)",   "{ {x:1}:1, {x:2}:2 }");
        assertEvaluates("{ {x:1}:1, {x:2}:2 }",     "round(tensor0)",  "{ {x:1}:1, {x:2}:1.8 }");
        assertEvaluates("{ {x:1}:0.5, {x:2}:0.5 }", "sigmoid(tensor0)","{ {x:1}:0, {x:2}:0 }");
        assertEvaluates("{ {x:1}:1, {x:2}:-1 }",    "sign(tensor0)",   "{ {x:1}:3, {x:2}:-5 }");
        assertEvaluates("{ {x:1}:0, {x:2}:0 }",     "sin(tensor0)",    "{ {x:1}:0, {x:2}:0 }");
        assertEvaluates("{ {x:1}:0, {x:2}:0 }",     "sinh(tensor0)",   "{ {x:1}:0, {x:2}:0 }");
        assertEvaluates("{ {x:1}:1, {x:2}:4 }",     "square(tensor0)", "{ {x:1}:1, {x:2}:2 }");
        assertEvaluates("{ {x:1}:1, {x:2}:3 }",     "sqrt(tensor0)",   "{ {x:1}:1, {x:2}:9 }");
        assertEvaluates("{ {x:1}:0, {x:2}:0 }",     "tan(tensor0)",    "{ {x:1}:0, {x:2}:0 }");
        assertEvaluates("{ {x:1}:0, {x:2}:0 }",     "tanh(tensor0)",   "{ {x:1}:0, {x:2}:0 }");

        // tensor reduce
        // -- reduce 2 dimensions
        assertEvaluates("{ {}:4 }",
                        "reduce(tensor0, avg, x, y)",   "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        assertEvaluates("{ {}:4 }",
                        "reduce(tensor0, count, x, y)", "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        assertEvaluates("{ {}:105 }",
                        "reduce(tensor0, prod, x, y)",  "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        assertEvaluates("{ {}:16 }",
                        "reduce(tensor0, sum, x, y)",   "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        assertEvaluates("{ {}:7 }",
                        "reduce(tensor0, max, x, y)",   "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        assertEvaluates("{ {}:1 }",
                        "reduce(tensor0, min, x, y)",   "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        // -- reduce 2 by specifying no arguments
        assertEvaluates("{ {}:4 }",
                        "reduce(tensor0, avg)",   "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        // -- reduce 1 dimension
        assertEvaluates("{ {y:1}:2, {y:2}:6 }",
                        "reduce(tensor0, avg, x)",   "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        assertEvaluates("{ {y:1}:2, {y:2}:2 }",
                        "reduce(tensor0, count, x)", "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        assertEvaluates("{ {y:1}:3, {y:2}:35 }",
                        "reduce(tensor0, prod, x)",  "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        assertEvaluates("{ {y:1}:4, {y:2}:12 }",
                        "reduce(tensor0, sum, x)",   "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        assertEvaluates("{ {y:1}:3, {y:2}:7 }",
                        "reduce(tensor0, max, x)",   "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        assertEvaluates("{ {y:1}:1, {y:2}:5 }",
                        "reduce(tensor0, min, x)",   "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        // -- reduce composites
        assertEvaluates("{ {}: 5   }", "sum(tensor0)", "5.0");
        assertEvaluates("{ {}:-5   }", "sum(tensor0)", "-5.0");
        assertEvaluates("{ {}:12.5 }", "sum(tensor0)", "{ {d1:l1}:5.5, {d1:l2}:7.0 }");
        assertEvaluates("{ {}: 0   }", "sum(tensor0)", "{ {d1:l1}:5.0, {d1:l2}:7.0, {d1:l3}:-12.0}");
        assertEvaluates("{ {y:1}:4, {y:2}:12.0 }",
                        "sum(tensor0, x)", "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        assertEvaluates("{ {x:1}:6, {x:2}:10.0 }",
                        "sum(tensor0, y)", "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");
        assertEvaluates("{ {}:16 }",
                        "sum(tensor0, x, y)", "{ {x:1,y:1}:1.0, {x:2,y:1}:3.0, {x:1,y:2}:5.0, {x:2,y:2}:7.0 }");

        // tensor join
        assertEvaluates("{ {x:1,y:1}:15, {x:2,y:1}:35 }", "join(tensor0, tensor1, f(x,y) (x*y))", "{ {x:1}:3, {x:2}:7 }", "{ {y:1}:5 }");
        // -- join composites
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
        assertEvaluates("{ {x:1,y:1}:5, {x:2,y:1}:7 }",
                        "max(tensor0, tensor1)", "{ {x:1}:3, {x:2}:7 }", "{ {y:1}:5 }");
        assertEvaluates("{ {x:1,y:1}:3, {x:2,y:1}:5 }",
                        "min(tensor0, tensor1)", "{ {x:1}:3, {x:2}:7 }", "{ {y:1}:5 }");
        assertEvaluates("{ {x:1,y:1,z:1}:7, {x:1,y:1,z:2}:13, {x:2,y:1,z:1}:21, {x:2,y:1,z:2}:39, {x:1,y:2,z:1}:55 }",
                        "tensor0 * tensor1", "{ {x:1,y:1}:1, {x:2,y:1}:3, {x:1,y:2}:5 }", "{ {y:1,z:1}:7, {y:2,z:1}:11, {y:1,z:2}:13 }");
        assertEvaluates("{ {x:1,y:2,z:1}:35, {x:1,y:2,z:2}:65 }",
                        "tensor0 * tensor1", "{ {x:1,y:1}:1, {x:2,y:1}:3, {x:1,y:2}:5 }", "{ {y:2,z:1}:7, {y:3,z:1}:11, {y:2,z:2}:13 }");
        assertEvaluates("{{x:1,y:1}:0.0}","tensor1 * tensor2 * tensor3", "{ {x:1}:1 }", "{ {x:2,y:1}:1, {x:1,y:1}:1 }", "{ {x:1,y:1}:1 }");
        assertEvaluates("{ {d1:l1}:50, {d1:l2}:500, {d1:l3}:5000 }",
                        "5 * tensor0", "{ {d1:l1}:10, {d1:l2}:100, {d1:l3}:1000 }");
        assertEvaluates("{ {d1:l1}:13, {d1:l2}:103, {d1:l3}:1003 }",
                        "tensor0 + 3","{ {d1:l1}:10, {d1:l2}:100, {d1:l3}:1000 }");
        assertEvaluates("{ {d1:l1}:1, {d1:l2}:10, {d1:l3 }:100 }",
                        "tensor0 / 10", "{ {d1:l1}:10, {d1:l2}:100, {d1:l3}:1000 }");
        assertEvaluates("{ {h:1}:1.5, {h:2}:1.5 }", "0.5 + tensor0", "{ {h:1}:1.0,{h:2}:1.0 }");
        assertEvaluates("{ {x:1,y:1}:0, {x:2,y:1}:0 }",
                        "atan2(tensor0, tensor1)", "{ {x:1}:0, {x:2}:0 }", "{ {y:1}:1 }");
        assertEvaluates("{ {x:1,y:1}:0, {x:2,y:1}:1 }",
                        "tensor0 > tensor1", "{ {x:1}:3, {x:2}:7 }", "{ {y:1}:5 }");
        assertEvaluates("{ {x:1,y:1}:1, {x:2,y:1}:0 }",
                        "tensor0 < tensor1", "{ {x:1}:3, {x:2}:7 }", "{ {y:1}:5 }");
        assertEvaluates("{ {x:1,y:1}:0, {x:2,y:1}:1 }",
                        "tensor0 >= tensor1", "{ {x:1}:3, {x:2}:7 }", "{ {y:1}:5 }");
        assertEvaluates("{ {x:1,y:1}:1, {x:2,y:1}:0 }",
                        "tensor0 <= tensor1", "{ {x:1}:3, {x:2}:7 }", "{ {y:1}:5 }");
        assertEvaluates("{ {x:1,y:1}:0, {x:2,y:1}:1 }",
                        "tensor0 == tensor1", "{ {x:1}:3, {x:2}:7 }", "{ {y:1}:7 }");
        assertEvaluates("{ {x:1,y:1}:1, {x:2,y:1}:0 }",
                        "tensor0 != tensor1", "{ {x:1}:3, {x:2}:7 }", "{ {y:1}:7 }");
        // TODO
        // argmax
        // argmin        
        assertEvaluates("{ {x:1,y:1}:1, {x:2,y:1}:0 }",
                        "tensor0 != tensor1", "{ {x:1}:3, {x:2}:7 }", "{ {y:1}:7 }");
        
        // tensor rename
        assertEvaluates("{ {newX:1,y:2}:3 }", "rename(tensor0, x, newX)", "{ {x:1,y:2}:3.0 }");
        assertEvaluates("{ {x:2,y:1}:3 }", "rename(tensor0, (x, y), (y, x))", "{ {x:1,y:2}:3.0 }");
        
        // tensor generate - TODO
        // assertEvaluates("{ {x:0,y:0}:1, {x:1,y:0}:0, {x:2,y:2}:1, {x:1,y:2}:0 }", "tensor(x[2],y[2])(x==y)");
        // range
        // diag
        // fill
        // random
        
        // composite functions
        assertEvaluates("{ {x:1}:0.25, {x:2}:0.75 }", "l1_normalize(tensor0, x)", "{ {x:1}:1, {x:2}:3 }");
        assertEvaluates("{ {x:1}:0.31622776601683794, {x:2}:0.9486832980505138 }", "l2_normalize(tensor0, x)", "{ {x:1}:1, {x:2}:3 }");
        assertEvaluates("{ {y:1}:81.0 }", "matmul(tensor0, tensor1, x)", "{ {x:1}:15, {x:2}:12 }", "{ {y:1}:3 }");
        assertEvaluates("{ {x:1}:0.5, {x:2}:0.5 }", "softmax(tensor0, x)", "{ {x:1}:1, {x:2}:1 }", "{ {y:1}:1 }");
        assertEvaluates("{ {x:1,y:1}:88.0 }", "xw_plus_b(tensor0, tensor1, tensor2, x)", "{ {x:1}:15, {x:2}:12 }", "{ {y:1}:3 }", "{ {x:1}:7 }");

        // expressions combining functions
        assertEvaluates(String.valueOf(7.5 + 45 + 1.7),
                        "sum( " +                              // model computation:
                        "      tensor0 * tensor1 * tensor2 " + // - feature combinations
                        "      * tensor3" +                    // - model weights application
                        ") + 1.7",
                        "{ {x:1}:1, {x:2}:2 }", "{ {y:1}:3, {y:2}:4 }", "{ {z:1}:5 }",
                        "{ {x:1,y:1,z:1}:0.5, {x:2,y:1,z:1}:1.5, {x:1,y:1,z:2}:4.5 }");
        assertEvaluates("1.0", "sum(tensor0 * tensor1 + 0.5)", "{ {x:1}:0, {x:2}:0 }", "{ {x:1}:1, {x:2}:1 }");
        assertEvaluates("0.0", "sum(tensor0 * tensor1 + 0.5)", "{}",                   "{ {x:1}:1, {x:2}:1 }");

        // tensor result dimensions are given from argument dimensions, not the resulting values
        assertEvaluates("tensor(x{}):{}", "tensor0 * tensor1", "{ {x:1}:1 }", "{ {x:2}:1 }");
        assertEvaluates("tensor(x{},y{}):{}", "tensor0 * tensor1", "{ {x:1}:1 }", "{ {x:2,y:1}:1, {x:3,y:2}:1 }");
    }

    public void testTmp() {
        assertEvaluates("{ {newX:1,y:2}:3 }", "rename(tensor0, x, newX)", "{ {x:1,y:2}:3.0 }");
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
