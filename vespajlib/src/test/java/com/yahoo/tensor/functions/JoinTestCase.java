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
    
}
