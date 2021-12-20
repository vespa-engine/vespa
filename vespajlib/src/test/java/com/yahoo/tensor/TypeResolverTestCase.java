// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author arnej
 */
public class TypeResolverTestCase {

    private static List<String> mkl(String ...values) {
        return Arrays.asList(values);
    }

    @Test
    public void verifyMap() {
        checkMap("tensor()", "tensor()");
        checkMap("tensor(x[10])", "tensor(x[10])");
        checkMap("tensor(a[10],b[20],c[30])", "tensor(a[10],b[20],c[30])");
        checkMap("tensor(y{})", "tensor(y{})");
        checkMap("tensor(x[10],y{})", "tensor(x[10],y{})");
        checkMap("tensor<float>(x[10])", "tensor<float>(x[10])");
        checkMap("tensor<float>(y{})", "tensor<float>(y{})");
        checkMap("tensor<bfloat16>(x[10])", "tensor<float>(x[10])");
        checkMap("tensor<bfloat16>(y{})", "tensor<float>(y{})");
        checkMap("tensor<int8>(x[10])", "tensor<float>(x[10])");
        checkMap("tensor<int8>(y{})", "tensor<float>(y{})");
    }

    @Test
    public void verifyJoin() {
        checkJoin("tensor()",                    "tensor()",                                       "tensor()");
        checkJoin("tensor()",                    "tensor(x{})",                                    "tensor(x{})");
        checkJoin("tensor(x{})",                 "tensor()",                                       "tensor(x{})");
        checkJoin("tensor(x{})",                 "tensor(x{})",                                    "tensor(x{})");
        checkJoin("tensor(x{})",                 "tensor(y{})",                                    "tensor(x{},y{})");
        checkJoin("tensor(x{},y{})",             "tensor(y{},z{})",                                "tensor(x{},y{},z{})");
        checkJoin("tensor(y{})",                 "tensor()",                                       "tensor(y{})");
        checkJoin("tensor(y{})",                 "tensor(y{})",                                    "tensor(y{})");
        checkJoin("tensor(a[10])",               "tensor(a[10])",                                  "tensor(a[10])");
        checkJoin("tensor(a[10])",               "tensor()",                                       "tensor(a[10])");
        checkJoin("tensor(a[10])",               "tensor(x{},y{},z{})",                            "tensor(a[10],x{},y{},z{})");
        // with cell types
        checkJoin("tensor<bfloat16>(x[5])",      "tensor<bfloat16>(x[5])",                         "tensor<float>(x[5])");
        checkJoin("tensor<bfloat16>(x[5])",      "tensor<float>(x[5])",                            "tensor<float>(x[5])");
        checkJoin("tensor<bfloat16>(x[5])",      "tensor<int8>(x[5])",                             "tensor<float>(x[5])");
        checkJoin("tensor<bfloat16>(x[5])",      "tensor()",                                       "tensor<float>(x[5])");
        checkJoin("tensor<bfloat16>(x[5])",      "tensor(x[5])",                                   "tensor(x[5])");
        checkJoin("tensor<bfloat16>(x{})",       "tensor<bfloat16>(y{})",                          "tensor<float>(x{},y{})");
        checkJoin("tensor<bfloat16>(x{})",       "tensor<int8>(y{})",                              "tensor<float>(x{},y{})");
        checkJoin("tensor<bfloat16>(x{})",       "tensor()",                                       "tensor<float>(x{})");
        checkJoin("tensor<float>(x[5])",         "tensor<float>(x[5])",                            "tensor<float>(x[5])");
        checkJoin("tensor<float>(x[5])",         "tensor<int8>(x[5])",                             "tensor<float>(x[5])");
        checkJoin("tensor<float>(x[5])",         "tensor()",                                       "tensor<float>(x[5])");
        checkJoin("tensor<float>(x[5])",         "tensor(x[5])",                                   "tensor(x[5])");
        checkJoin("tensor<float>(x{})",          "tensor<bfloat16>(y{})",                          "tensor<float>(x{},y{})");
        checkJoin("tensor<float>(x{})",          "tensor<float>(y{})",                             "tensor<float>(x{},y{})");
        checkJoin("tensor<float>(x{})",          "tensor<int8>(y{})",                              "tensor<float>(x{},y{})");
        checkJoin("tensor<float>(x{})",          "tensor()",                                       "tensor<float>(x{})");
        checkJoin("tensor<int8>(x[5])",          "tensor<int8>(x[5])",                             "tensor<float>(x[5])");
        checkJoin("tensor<int8>(x{})",           "tensor<int8>(y{})",                              "tensor<float>(x{},y{})");
        checkJoin("tensor<int8>(x{})",           "tensor()",                                       "tensor<float>(x{})");
        checkJoin("tensor()",                    "tensor<int8>(x[5])",                             "tensor<float>(x[5])");
        checkJoin("tensor(x[5])",                "tensor<int8>(x[5])",                             "tensor(x[5])");
        checkJoin("tensor(x[5])",                "tensor(x[5])",                                   "tensor(x[5])");
        checkJoin("tensor(x{})",                 "tensor<bfloat16>(y{})",                          "tensor(x{},y{})");
        checkJoin("tensor(x{})",                 "tensor<float>(y{})",                             "tensor(x{},y{})");
        checkJoin("tensor(x{})",                 "tensor<int8>(y{})",                              "tensor(x{},y{})");
        // specific for Java
        checkJoin("tensor(x[])",                 "tensor(x{})",                                    "tensor(x{})");
        checkJoin("tensor(x[3])",                "tensor(x{})",                                    "tensor(x{})");
        checkJoin("tensor(x{})",                 "tensor(x[])",                                    "tensor(x{})");
        checkJoin("tensor(x{})",                 "tensor(x[3])",                                   "tensor(x{})");
        // dimension mismatch should fail:
        checkJoinFails("tensor(x[3])",           "tensor(x[5])");
        checkJoinFails("tensor(x[5])",           "tensor(x[3])");
    }

    @Test
    public void verifyReduce() {
        checkFullReduce("tensor()");
        checkReduce("tensor(x[10],y[20],z[30])", mkl("x"), "tensor(y[20],z[30])");
        checkReduce("tensor(x[10],y[20],z[30])", mkl("y"), "tensor(x[10],z[30])");
        checkReduce("tensor<float>(x[10],y[20],z[30])", mkl("z"), "tensor<float>(x[10],y[20])");
        checkReduce("tensor<bfloat16>(x[10],y[20],z[30])", mkl("z"), "tensor<float>(x[10],y[20])");
        checkReduce("tensor<int8>(x[10],y[20],z[30])", mkl("z"), "tensor<float>(x[10],y[20])");
        checkReduce("tensor(x[10],y[20],z[30])", mkl("x", "z"), "tensor(y[20])");
        checkReduce("tensor<float>(x[10],y[20],z[30])", mkl("z", "x"), "tensor<float>(y[20])");
        checkReduce("tensor<bfloat16>(x[10],y[20],z[30])", mkl("z", "x"), "tensor<float>(y[20])");
        checkReduce("tensor<int8>(x[10],y[20],z[30])", mkl("z", "x"), "tensor<float>(y[20])");
        checkFullReduce("tensor(x[10],y[20],z[30])");
        checkFullReduce("tensor<float>(x[10],y[20],z[30])");
        checkFullReduce("tensor<bfloat16>(x[10],y[20],z[30])");
        checkFullReduce("tensor<int8>(x[10],y[20],z[30])");
        checkReduce("tensor(x[10],y[20],z[30])", mkl("x", "y", "z"), "tensor()");
        checkReduce("tensor<float>(x[10],y[20],z[30])", mkl("x", "y", "z"), "tensor()");
        checkReduce("tensor<bfloat16>(x[10],y[20],z[30])", mkl("x", "y", "z"), "tensor()");
        checkReduce("tensor<int8>(x[10],y[20],z[30])", mkl("x", "y", "z"), "tensor()");
        checkReduce("tensor(x[10],y{},z[30])", mkl("x"), "tensor(y{},z[30])");
        checkReduce("tensor(x[10],y{},z[30])", mkl("y"), "tensor(x[10],z[30])");
        checkReduce("tensor<float>(x[10],y{},z[30])", mkl("z"), "tensor<float>(x[10],y{})");
        checkReduce("tensor<bfloat16>(x[10],y{},z[30])", mkl("z"), "tensor<float>(x[10],y{})");
        checkReduce("tensor<int8>(x[10],y{},z[30])", mkl("z"), "tensor<float>(x[10],y{})");
        checkReduce("tensor(x[10],y{},z[30])", mkl("x", "z"), "tensor(y{})");
        checkReduce("tensor<float>(x[10],y{},z[30])", mkl("z", "x"), "tensor<float>(y{})");
        checkReduce("tensor<bfloat16>(x[10],y{},z[30])", mkl("z", "x"), "tensor<float>(y{})");
        checkReduce("tensor<int8>(x[10],y{},z[30])", mkl("z", "x"), "tensor<float>(y{})");
        checkFullReduce("tensor(x[10],y{},z[30])");
        checkFullReduce("tensor<float>(x[10],y{},z[30])");
        checkFullReduce("tensor<bfloat16>(x[10],y{},z[30])");
        checkFullReduce("tensor<int8>(x[10],y{},z[30])");
        checkReduce("tensor(x[10],y{},z[30])", mkl("x", "y", "z"), "tensor()");
        checkReduce("tensor<float>(x[10],y{},z[30])", mkl("x", "y", "z"), "tensor()");
        checkReduce("tensor<bfloat16>(x[10],y{},z[30])", mkl("x", "y", "z"), "tensor()");
        checkReduce("tensor<int8>(x[10],y{},z[30])", mkl("x", "y", "z"), "tensor()");
        // for now, these will just log a warning
        //checkReduceFails("tensor()", "x");
        //checkReduceFails("tensor(y{})", "x");
        //checkReduceFails("tensor<float>(y[10])", "x");
        //checkReduceFails("tensor<int8>(y[10])", "x");
        checkReduce("tensor()", mkl("x"), "tensor()");
        checkReduce("tensor(y{})", mkl("x"), "tensor(y{})");
        checkReduce("tensor<float>(y[10])", mkl("x"), "tensor<float>(y[10])");
        checkReduce("tensor<int8>(y[10])", mkl("x"), "tensor<float>(y[10])");
    }

    @Test
    public void verifyMerge() {
        checkMerge("tensor(a[10])",               "tensor(a[10])",                                  "tensor(a[10])");
        checkMerge("tensor<bfloat16>(x[5])",      "tensor<bfloat16>(x[5])",                         "tensor<float>(x[5])");
        checkMerge("tensor<bfloat16>(x[5])",      "tensor<float>(x[5])",                            "tensor<float>(x[5])");
        checkMerge("tensor<bfloat16>(x[5])",      "tensor<int8>(x[5])",                             "tensor<float>(x[5])");
        checkMerge("tensor<bfloat16>(x[5])",      "tensor(x[5])",                                   "tensor(x[5])");
        checkMerge("tensor<bfloat16>(y{})",       "tensor<bfloat16>(y{})",                          "tensor<float>(y{})");
        checkMerge("tensor<bfloat16>(y{})",       "tensor<int8>(y{})",                              "tensor<float>(y{})");
        checkMerge("tensor<float>(x[5])",         "tensor<float>(x[5])",                            "tensor<float>(x[5])");
        checkMerge("tensor<float>(x[5])",         "tensor<int8>(x[5])",                             "tensor<float>(x[5])");
        checkMerge("tensor<float>(x[5])",         "tensor(x[5])",                                   "tensor(x[5])");
        checkMerge("tensor<float>(y{})",          "tensor<bfloat16>(y{})",                          "tensor<float>(y{})");
        checkMerge("tensor<float>(y{})",          "tensor<float>(y{})",                             "tensor<float>(y{})");
        checkMerge("tensor<float>(y{})",          "tensor<int8>(y{})",                              "tensor<float>(y{})");
        checkMerge("tensor<int8>(x[5])",          "tensor<int8>(x[5])",                             "tensor<float>(x[5])");
        checkMerge("tensor<int8>(y{})",           "tensor<int8>(y{})",                              "tensor<float>(y{})");
        checkMerge("tensor()",                    "tensor()",                                       "tensor()");
        checkMerge("tensor(x[5])",                "tensor<int8>(x[5])",                             "tensor(x[5])");
        checkMerge("tensor(x[5])",                "tensor(x[5])",                                   "tensor(x[5])");
        checkMerge("tensor(x{})",                 "tensor(x{})",                                    "tensor(x{})");
        checkMerge("tensor(x{},y{})",             "tensor<bfloat16>(x{},y{})",                      "tensor(x{},y{})");
        checkMerge("tensor(x{},y{})",             "tensor<float>(x{},y{})",                         "tensor(x{},y{})");
        checkMerge("tensor(x{},y{})",             "tensor<int8>(x{},y{})",                          "tensor(x{},y{})");
        checkMerge("tensor(y{})",                 "tensor(y{})",                                    "tensor(y{})");
        checkMerge("tensor(x{})",                 "tensor(x[5])",                                   "tensor(x{})");
        checkMergeFails("tensor(a[10])",          "tensor()");
        checkMergeFails("tensor(a[10])",          "tensor(x{},y{},z{})");
        checkMergeFails("tensor<bfloat16>(x[5])", "tensor()");
        checkMergeFails("tensor<bfloat16>(x{})",  "tensor()");
        checkMergeFails("tensor<float>(x[5])",    "tensor()");
        checkMergeFails("tensor<float>(x{})",     "tensor()");
        checkMergeFails("tensor<int8>(x{})",      "tensor()");
        checkMergeFails("tensor()",               "tensor<int8>(x[5])");
        checkMergeFails("tensor()",               "tensor(x{})");
        checkMergeFails("tensor(x[3])",           "tensor(x[5])");
        checkMergeFails("tensor(x[5])",           "tensor(x[3])");
        checkMergeFails("tensor(x{})",            "tensor()");
        checkMergeFails("tensor(x{},y{})",        "tensor(x{},z{})");
        checkMergeFails("tensor(y{})",            "tensor()");
    }

    @Test
    public void verifyRename() {
        checkRename("tensor(x[10],y[20],z[30])", mkl("y"), mkl("a"), "tensor(a[20],x[10],z[30])");
        checkRename("tensor(x{})", mkl("x"), mkl("y"), "tensor(y{})");
        checkRename("tensor(x{},y[5])", mkl("x","y"), mkl("y","x"), "tensor(x[5],y{})");
        checkRename("tensor(x[10],y[20],z[30])", mkl("x", "y", "z"), mkl("c", "a", "b"), "tensor(a[20],b[30],c[10])");
        checkRename("tensor(x{})", mkl("x"), mkl("x"), "tensor(x{})");
        checkRename("tensor(x{})", mkl("x"), mkl("y"), "tensor(y{})");
        checkRename("tensor<float>(x{})", mkl("x"), mkl("y"), "tensor<float>(y{})");
        checkRename("tensor<bfloat16>(x{})", mkl("x"), mkl("y"), "tensor<bfloat16>(y{})");
        checkRename("tensor<int8>(x{})", mkl("x"), mkl("y"), "tensor<int8>(y{})");

        checkRenameFails("tensor(x{})", mkl(), mkl());
        checkRenameFails("tensor()", mkl(), mkl());
        checkRenameFails("tensor(x{},y{})", mkl("x"), mkl("y","z"));
        checkRenameFails("tensor(x{},y{})", mkl("x","y"), mkl("z"));
        checkRenameFails("tensor(x[10],y[20],z[30])", mkl("y","z"), mkl("a", "x"));

        // allowed (with warning) for now:
        checkRename("tensor()", mkl("a"), mkl("b"), "tensor()");
        checkRename("tensor(x{},y[10])", mkl("a"), mkl("b"), "tensor(x{},y[10])");
        //checkRenameFails("tensor()", mkl("a"), mkl("b"));

    }

    @Test
    public void verifyConcat() {
        // types can be concatenated
        checkConcat("tensor(y[7])",      "tensor(x{})",  "z", "tensor(x{},y[7],z[2])");
        checkConcat("tensor()",          "tensor()",     "x", "tensor(x[2])");
        checkConcat("tensor(x[2])",      "tensor()",     "x", "tensor(x[3])");
        checkConcat("tensor(x[3])",      "tensor(x[2])", "x", "tensor(x[5])");
        checkConcat("tensor(x[2])",      "tensor()",     "y", "tensor(x[2],y[2])");
        checkConcat("tensor(x[2])",      "tensor(x[2])", "y", "tensor(x[2],y[2])");
        checkConcat("tensor(x[2],y[2])", "tensor(x[3])", "x", "tensor(x[5],y[2])");
        checkConcat("tensor(x[2],y[2])", "tensor(y[7])", "y", "tensor(x[2],y[9])");
        checkConcat("tensor(x[5])",      "tensor(y[7])", "z", "tensor(x[5],y[7],z[2])");
        // cell type is handled correctly for concat
        checkConcat("tensor(x[3])", "tensor(x[2])",                     "x", "tensor(x[5])");
        checkConcat("tensor(x[3])", "tensor<float>(x[2])",              "x", "tensor(x[5])");
        checkConcat("tensor(x[3])", "tensor<bfloat16>(x[2])",           "x", "tensor(x[5])");
        checkConcat("tensor(x[3])", "tensor<int8>(x[2])",               "x", "tensor(x[5])");
        checkConcat("tensor<float>(x[3])", "tensor<float>(x[2])",       "x", "tensor<float>(x[5])");
        checkConcat("tensor<float>(x[3])", "tensor<bfloat16>(x[2])",    "x", "tensor<float>(x[5])");
        checkConcat("tensor<float>(x[3])", "tensor<int8>(x[2])",        "x", "tensor<float>(x[5])");
        checkConcat("tensor<bfloat16>(x[3])", "tensor<bfloat16>(x[2])", "x", "tensor<bfloat16>(x[5])");
        checkConcat("tensor<bfloat16>(x[3])", "tensor<int8>(x[2])",     "x", "tensor<float>(x[5])");
        checkConcat("tensor<int8>(x[3])", "tensor<int8>(x[2])",         "x", "tensor<int8>(x[5])");
        // concat with number preserves cell type
        checkConcat("tensor(x[3])",           "tensor()", "x", "tensor(x[4])");
        checkConcat("tensor<float>(x[3])",    "tensor()", "x", "tensor<float>(x[4])");
        checkConcat("tensor<bfloat16>(x[3])", "tensor()", "x", "tensor<bfloat16>(x[4])");
        checkConcat("tensor<int8>(x[3])",     "tensor()", "x", "tensor<int8>(x[4])");
        // specific for Java
        checkConcat("tensor(x[])",            "tensor(x[2])", "x", "tensor(x[])");
        checkConcat("tensor(x[])",            "tensor(x[2])", "y", "tensor(x[],y[2])");
        checkConcat("tensor(x[3])",           "tensor(x[2])", "y", "tensor(x[2],y[2])");
        // invalid combinations must fail
        checkConcatFails("tensor(x{})",       "tensor(x[2])", "x");
        checkConcatFails("tensor(x{})",       "tensor(x{})",  "x");
        checkConcatFails("tensor(x{})",       "tensor()",     "x");
    }

    @Test
    public void verifyPeek() {
        checkPeek("tensor(x[10],y[20],z[30])", mkl("x"), "tensor(y[20],z[30])");
        checkPeek("tensor(x[10],y[20],z[30])", mkl("y"), "tensor(x[10],z[30])");
        checkPeek("tensor<float>(x[10],y[20],z[30])", mkl("z"), "tensor<float>(x[10],y[20])");
        checkPeek("tensor<bfloat16>(x[10],y[20],z[30])", mkl("z"), "tensor<bfloat16>(x[10],y[20])");
        checkPeek("tensor<int8>(x[10],y[20],z[30])", mkl("z"), "tensor<int8>(x[10],y[20])");
        checkPeek("tensor(x[10],y[20],z[30])", mkl("x", "z"), "tensor(y[20])");
        checkPeek("tensor<float>(x[10],y[20],z[30])", mkl("z", "x"), "tensor<float>(y[20])");
        checkPeek("tensor<bfloat16>(x[10],y[20],z[30])", mkl("z", "x"), "tensor<bfloat16>(y[20])");
        checkPeek("tensor<int8>(x[10],y[20],z[30])", mkl("z", "x"), "tensor<int8>(y[20])");
        checkPeek("tensor(x[10],y[20],z[30])", mkl("x", "y", "z"), "tensor()");
        checkPeek("tensor<float>(x[10],y[20],z[30])", mkl("x", "y", "z"), "tensor()");
        checkPeek("tensor<bfloat16>(x[10],y[20],z[30])", mkl("x", "y", "z"), "tensor()");
        checkPeek("tensor<int8>(x[10],y[20],z[30])", mkl("x", "y", "z"), "tensor()");
        checkPeek("tensor(x[10],y{},z[30])", mkl("x"), "tensor(y{},z[30])");
        checkPeek("tensor(x[10],y{},z[30])", mkl("y"), "tensor(x[10],z[30])");
        checkPeek("tensor<float>(x[10],y{},z[30])", mkl("z"), "tensor<float>(x[10],y{})");
        checkPeek("tensor<bfloat16>(x[10],y{},z[30])", mkl("z"), "tensor<bfloat16>(x[10],y{})");
        checkPeek("tensor<int8>(x[10],y{},z[30])", mkl("z"), "tensor<int8>(x[10],y{})");
        checkPeek("tensor(x[10],y{},z[30])", mkl("x", "z"), "tensor(y{})");
        checkPeek("tensor<float>(x[10],y{},z[30])", mkl("z", "x"), "tensor<float>(y{})");
        checkPeek("tensor<bfloat16>(x[10],y{},z[30])", mkl("z", "x"), "tensor<bfloat16>(y{})");
        checkPeek("tensor<int8>(x[10],y{},z[30])", mkl("z", "x"), "tensor<int8>(y{})");
        checkPeek("tensor(x[10],y{},z[30])", mkl("x", "y", "z"), "tensor()");
        checkPeek("tensor<float>(x[10],y{},z[30])", mkl("x", "y", "z"), "tensor()");
        checkPeek("tensor<bfloat16>(x[10],y{},z[30])", mkl("x", "y", "z"), "tensor()");
        checkPeek("tensor<int8>(x[10],y{},z[30])", mkl("x", "y", "z"), "tensor()");
        checkFullPeek("tensor(x[10],y[20],z[30])");
        checkFullPeek("tensor<float>(x[10],y[20],z[30])");
        checkFullPeek("tensor<bfloat16>(x[10],y[20],z[30])");
        checkFullPeek("tensor<int8>(x[10],y[20],z[30])");
        checkFullPeek("tensor(x[10],y{},z[30])");
        checkFullPeek("tensor<float>(x[10],y{},z[30])");
        checkFullPeek("tensor<bfloat16>(x[10],y{},z[30])");
        checkFullPeek("tensor<int8>(x[10],y{},z[30])");
        checkPeekFails("tensor()", mkl());
        checkPeekFails("tensor()", mkl("x"));
        checkPeekFails("tensor(y{})", mkl("x"));
        checkPeekFails("tensor(y{})", mkl("y", "y"));
        checkPeekFails("tensor<float>(y[10])", mkl("x"));
    }

    @Test
    public void verifyCellCast() {
        checkCast("tensor(x[10],y[20],z[30])", TensorType.Value.FLOAT, "tensor<float>(x[10],y[20],z[30])");
        checkCasts("tensor<double>(x[10])");
        checkCasts("tensor<float>(x[10])");
        checkCasts("tensor<bfloat16>(x[10])");
        checkCasts("tensor<int8>(x[10])");
        checkCasts("tensor<double>(x{})");
        checkCasts("tensor<float>(x{})");
        checkCasts("tensor<bfloat16>(x{})");
        checkCasts("tensor<int8>(x{})");
        checkCasts("tensor<double>(x{},y[5])");
        checkCasts("tensor<float>(x{},y[5])");
        checkCasts("tensor<bfloat16>(x{},y[5])");
        checkCasts("tensor<int8>(x{},y[5])");
        checkCast("tensor()", TensorType.Value.DOUBLE, "tensor()");
        checkCastFails("tensor()", TensorType.Value.FLOAT);
        checkCastFails("tensor()", TensorType.Value.BFLOAT16);
        checkCastFails("tensor()", TensorType.Value.INT8);
    }

    private static void checkMap(String specA, String expected) {
        var a = TensorType.fromSpec(specA);
        var result = TypeResolver.map(a);
        assertEquals(expected, result.toString());
    }

    private static void checkJoin(String specA, String specB, String expected) {
        var a = TensorType.fromSpec(specA);
        var b = TensorType.fromSpec(specB);
        var result = TypeResolver.join(a, b);
        assertEquals(expected, result.toString());
    }

    private static void checkJoinFails(String specA, String specB) {
        var a = TensorType.fromSpec(specA);
        var b = TensorType.fromSpec(specB);
        boolean caught = false;
        try {
            var result = TypeResolver.join(a, b);
            System.err.println("join of "+a+" and "+b+" produces: "+result);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    private static void checkReduce(String specA, List<String> dims, String expected) {
        var a = TensorType.fromSpec(specA);
        var result = TypeResolver.reduce(a, dims);
        assertEquals(expected, result.toString());
    }

    private static void checkFullReduce(String specA) {
        String expected = "tensor()";
        List<String> dims = new ArrayList<>();
        checkReduce(specA, dims, expected);
        var a = TensorType.fromSpec(specA);
        for (var dim : a.dimensions()) {
            dims.add(dim.name());
        }
        checkReduce(specA, dims, expected);
    }

    private static void checkReduceFails(String specA, String dim) {
        var a = TensorType.fromSpec(specA);
        boolean caught = false;
        try {
            var result = TypeResolver.reduce(a, mkl(dim));
            System.err.println("Reduce "+specA+" with dim "+dim+" produces: "+result);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    private static void checkMerge(String specA, String specB, String expected) {
        var a = TensorType.fromSpec(specA);
        var b = TensorType.fromSpec(specB);
        var result = TypeResolver.merge(a, b);
        assertEquals(expected, result.toString());
    }

    private static void checkMergeFails(String specA, String specB) {
        var a = TensorType.fromSpec(specA);
        var b = TensorType.fromSpec(specB);
        boolean caught = false;
        try {
            var result = TypeResolver.merge(a, b);
            System.err.println("merge of "+a+" and "+b+" produces: "+result);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    private static void checkRename(String specA, List<String> fromDims, List<String> toDims, String expected) {
        var a = TensorType.fromSpec(specA);
        var result = TypeResolver.rename(a, fromDims, toDims);
        assertEquals(expected, result.toString());
    }

    private static void checkRenameFails(String specA, List<String> fromDims, List<String> toDims) {
        var a = TensorType.fromSpec(specA);
        boolean caught = false;
        try {
            var result = TypeResolver.rename(a, fromDims, toDims);
            System.err.println("rename "+a+" produces: "+result);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    private static void checkConcat(String specA, String specB, String dim, String expected) {
        var a = TensorType.fromSpec(specA);
        var b = TensorType.fromSpec(specB);
        var result = TypeResolver.concat(a, b, dim);
        assertEquals(expected, result.toString());
    }

    private static void checkConcatFails(String specA, String specB, String dim) {
        var a = TensorType.fromSpec(specA);
        var b = TensorType.fromSpec(specB);
        boolean caught = false;
        try {
            var result = TypeResolver.concat(a, b, dim);
            System.err.println("concat "+a+" and "+b+" along "+dim+" produces: "+result);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    private static void checkPeek(String specA, List<String> dims, String expected) {
        var a = TensorType.fromSpec(specA);
        var result = TypeResolver.peek(a, dims);
        assertEquals(expected, result.toString());
    }

    private static void checkFullPeek(String specA) {
        String expected = "tensor()";
        List<String> dims = new ArrayList<>();
        var a = TensorType.fromSpec(specA);
        for (var dim : a.dimensions()) {
            dims.add(dim.name());
        }
        checkPeek(specA, dims, expected);
    }

    private static void checkPeekFails(String specA, List<String> dims) {
        var a = TensorType.fromSpec(specA);
        boolean caught = false;
        try {
            var result = TypeResolver.peek(a, dims);
            System.err.println("Peek "+specA+" with dims "+dims+" produces: "+result);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    private static void checkCast(String specA, TensorType.Value newValueType, String expected) {
        var a = TensorType.fromSpec(specA);
        var result = TypeResolver.cell_cast(a, newValueType);
        assertEquals(expected, result.toString());
    }

    private static void checkCasts(String specA) {
        var a = TensorType.fromSpec(specA);
        for (var newValueType : TensorType.Value.values()) {
            var result = TypeResolver.cell_cast(a, newValueType);
            assertEquals(result.valueType(), newValueType);
            assertEquals(result.dimensions(), a.dimensions());
        }
    }

    private static void checkCastFails(String specA, TensorType.Value newValueType) {
        var a = TensorType.fromSpec(specA);
        boolean caught = false;
        try {
            var result = TypeResolver.cell_cast(a, newValueType);
            System.err.println("cast of "+a+" to "+newValueType+" produces: "+result);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

}
