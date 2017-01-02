package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ConcatTestCase {
    
    @Test
    public void testConcat() {
        {
            Tensor a = Tensor.from("{1}");
            Tensor b = Tensor.from("{2}");
            assertEquals(Tensor.from("tensor(x[2]):{ {x:0}:1, {x:1}:2 }"), a.concat(b, "x"));
            assertEquals(Tensor.from("tensor(x[2]):{ {x:0}:2, {x:1}:1 }"), b.concat(a, "x"));
        }

        {
            Tensor a = Tensor.from("tensor(x[]):{ {x:0}:1, {x:1}:2, {x:2}:3 }");
            Tensor b = Tensor.from("tensor(x[]):{ {x:0}:4, {x:1}:5, {x:2}:6 }");
            assertEquals(Tensor.from("tensor(x[6]):{ {x:0}:1, {x:1}:2, {x:2}:3, {x:3}:4, {x:4}:5, {x:5}:6 }"), a.concat(b, "x"));
            assertEquals(Tensor.from("tensor(x[3],y[2]):{ {x:0,y:0}:1, {x:1,y:0}:2, {x:2,y:0}:3, " +
                                     "{x:0,y:1}:4, {x:1,y:1}:5, {x:2,y:1}:6  }"), a.concat(b, "y"));
        }

        {
            Tensor a = Tensor.from("{1}");
            Tensor b = Tensor.from("tensor(x[]):{ {x:0}:2, {x:1}:3, {x:2}:4 }");
            assertEquals(Tensor.from("tensor(x[4]):{ {x:0}:1, {x:1}:2, {x:2}:3, {x:3}:4 }"), a.concat(b, "x"));
            assertEquals(Tensor.from("tensor(x[3],y[2]):{ {x:0,y:0}:1, {x:1,y:0}:1, {x:2,y:0}:1, " +
                                     "{x:0,y:1}:2, {x:1,y:1}:3, {x:2,y:1}:4  }"), a.concat(b, "y"));
        }
    }
    
}
