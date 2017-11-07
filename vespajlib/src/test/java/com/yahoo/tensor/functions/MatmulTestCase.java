package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class MatmulTestCase {

    @Test
    public void testMatmul2d() {
        // Convention: a is the 'outermost' dimension, etc.
        Tensor.Builder ab = Tensor.Builder.of(TensorType.fromSpec("tensor(a[2],b[3])"));
        ab.cell( 1,0, 0);
        ab.cell( 2,0, 1);
        ab.cell( 3,0, 2);
        ab.cell( 4,1, 0);
        ab.cell( 5,1, 1);
        ab.cell( 6,1, 2);
        Tensor a = ab.build();

        Tensor.Builder bb = Tensor.Builder.of(TensorType.fromSpec("tensor(a[3],b[2])"));
        bb.cell( 7,0, 0);
        bb.cell( 8,0, 1);
        bb.cell( 9,1, 0);
        bb.cell(10,1, 1);
        bb.cell(11,2, 0);
        bb.cell(12,2, 1);
        Tensor b = bb.build();

        Tensor.Builder rb = Tensor.Builder.of(TensorType.fromSpec("tensor(a[2],c[2])"));
        rb.cell( 58,0, 0);
        rb.cell( 64,0, 1);
        rb.cell(139,1, 0);
        rb.cell(154,1, 1);
        Tensor r = rb.build();

        Tensor result = a.matmul(b.rename(ImmutableList.of("a","b"),ImmutableList.of("b","c")), "b");
        assertEquals(r, result);
    }

    @Test
    public void testMatmul3d() {
        // Convention: a is the 'outermost' dimension, etc.
        Tensor.Builder ab = Tensor.Builder.of(TensorType.fromSpec("tensor(a[2],b[2],c[3])"));
        ab.cell( 1,0, 0, 0);
        ab.cell( 2,0, 0, 1);
        ab.cell( 3,0, 0, 2);
        ab.cell( 4,0, 1, 0);
        ab.cell( 5,0, 1, 1);
        ab.cell( 6,0, 1, 2);
        ab.cell( 7,1, 0, 0);
        ab.cell( 8,1, 0, 1);
        ab.cell( 9,1, 0, 2);
        ab.cell(10,1, 1, 0);
        ab.cell(11,1, 1, 1);
        ab.cell(12,1, 1, 2);
        Tensor a = ab.build();

        Tensor.Builder bb = Tensor.Builder.of(TensorType.fromSpec("tensor(a[2],b[3],c[2])"));
        bb.cell(13,0, 0, 0);
        bb.cell(14,0, 0, 1);
        bb.cell(15,0, 1, 0);
        bb.cell(16,0, 1, 1);
        bb.cell(17,0, 2, 0);
        bb.cell(18,0, 2, 1);
        bb.cell(19,1, 0, 0);
        bb.cell(20,1, 0, 1);
        bb.cell(21,1, 1, 0);
        bb.cell(22,1, 1, 1);
        bb.cell(23,1, 2, 0);
        bb.cell(24,1, 2, 1);
        Tensor b = bb.build();

        Tensor.Builder rb = Tensor.Builder.of(TensorType.fromSpec("tensor(a[2],b[2],d[2])"));
        rb.cell( 94,0, 0, 0);
        rb.cell(100,0, 0, 1);
        rb.cell(229,0, 1, 0);
        rb.cell(244,0, 1, 1);
        rb.cell(508,1, 0, 0);
        rb.cell(532,1, 0, 1);
        rb.cell(697,1, 1, 0);
        rb.cell(730,1, 1, 1);
        Tensor r = rb.build();

        Tensor result = a.matmul(b.rename(ImmutableList.of("b","c"),ImmutableList.of("c","d")), "c");
        System.out.println(result);
    }

}
