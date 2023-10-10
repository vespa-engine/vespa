// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class JoinTestCase {

    /** Test the indexed subspace join optimization */
    @Test
    public void testJoinIndexedSubspace() {
        Tensor t1, t2;

        t1 = Tensor.from("tensor(x[]):{{x:0}:1.0,{x:1}:2.0}");
        t2 = Tensor.from("tensor(x[],y[],z[]):{{x:0,y:0,z:0}:6,{x:0,y:1,z:0}:0.0,{x:1,y:0,z:0}:10,{x:1,y:1,z:0}:0.0}");
        assertEquals(Tensor.from("tensor(x[],y[],z[]):{{x:0,y:0,z:0}:6,{x:0,y:1,z:0}:0.0,{x:1,y:0,z:0}:20.0,{x:1,y:1,z:0}:0.0}"),
                     t1.multiply(t2));
        t1 = Tensor.from("tensor(x[]):{{x:0}:1.0,{x:1}:2.0}");
        t2 = Tensor.from("tensor(x[],y[],z[]):{{x:0,y:0,z:0}:6,{x:0,y:1,z:0}:0.0,{x:1,y:0,z:0}:10,{x:1,y:1,z:0}:0.0}");
        assertEquals(Tensor.from("tensor(x[],y[],z[]):{{x:0,y:0,z:0}:6,{x:0,y:1,z:0}:0.0,{x:1,y:0,z:0}:5.0,{x:1,y:1,z:0}:0.0}"),
                     t2.divide(t1));

        t1 = Tensor.from("tensor(y[]):{{y:0}:1.0,{y:1}:2.0}");
        t2 = Tensor.from("tensor(x[],y[],z[]):{{x:0,y:0,z:0}:6,{x:0,y:1,z:0}:0.0,{x:1,y:0,z:0}:10,{x:1,y:1,z:0}:0.0}");
        assertEquals(Tensor.from("tensor(x[],y[],z[]):{{x:0,y:0,z:0}:6,{x:0,y:1,z:0}:0.0,{x:1,y:0,z:0}:10.0,{x:1,y:1,z:0}:0.0}"),
                     t1.multiply(t2));
        t1 = Tensor.from("tensor(y[]):{{y:0}:1.0,{y:1}:2.0}");
        t2 = Tensor.from("tensor(x[],y[],z[]):{{x:0,y:0,z:0}:6,{x:0,y:1,z:0}:0.0,{x:1,y:0,z:0}:10,{x:1,y:1,z:0}:0.0}");
        assertEquals(Tensor.from("tensor(x[],y[],z[]):{{x:0,y:0,z:0}:6,{x:0,y:1,z:0}:0.0,{x:1,y:0,z:0}:10.0,{x:1,y:1,z:0}:0.0}"),
                     t2.divide(t1));
    }

    @Test
    public void testGeneralJoin() {
        assertEquals(Tensor.from("tensor(x[],y[]):{ {x:0,y:0}:1, {x:1,y:0}:2, {x:2,y:0}:3 }"),
                     Tensor.from("tensor(x[]):{ {x:0}:2, {x:1}:4, {x:2}:6 }")
                                 .divide(Tensor.from("tensor(y[]):{{y:0}:2}")));

        assertEquals(Tensor.from("tensor(x[],y[],z[]):{ {x:0,y:0,z:0}:3, {x:1,y:0,z:0}:4, {x:0,y:1,z:0}:5, {x:1,y:1,z:0}:6 }"),
                     Tensor.from("tensor(x[],y[]):{ {x:0,y:0}:6, {x:1,y:0}:8, {x:0,y:1}:20, {x:1,y:1}:24 }")
                             .divide(Tensor.from("tensor(y[],z[]):{ {y:0,z:0}:2, {y:1,z:0}:4, {y:2,z:0}:6 }")));
    }

}
