// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor;

import com.google.common.collect.Sets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Basic mixed tensor tests. Tensor operations are tested in EvaluationTestCase
 *
 * @author lesters
 */
public class MixedTensorTestCase {

    @Test
    public void testEmpty() {
        TensorType type = new TensorType.Builder().mapped("x").indexed("y", 3).build();
        Tensor empty = Tensor.Builder.of(type).build();
        assertTrue(empty instanceof MixedTensor);
        assertTrue(empty.isEmpty());
        assertEquals("tensor(x{},y[3]):{}", empty.toString());
        assertEquals("tensor(x{},y[3]):{}", Tensor.from("tensor(x{},y[3]):{}").toString());
    }

    @Test
    public void testScalar() {
        TensorType type = new TensorType.Builder().build();
        Tensor scalar = MixedTensor.Builder.of(type).cell().value(42.0).build();
        assertEquals(scalar.asDouble(), 42.0, 1e-6);
    }

    @Test
    public void testOneIndexedBuilding() {
        TensorType type = new TensorType.Builder().indexed("y", 3).build();
        Tensor tensor = MixedTensor.Builder.of(type).
                cell().label("y", 0).value(1).
                cell().label("y", 1).value(2).
                // {y:2} should be 0.0 and non NaN since we specify indexed size
                build();
        assertEquals(Sets.newHashSet("y"), tensor.type().dimensionNames());
        assertEquals("tensor(y[3]):[1.0, 2.0, 0.0]",
                tensor.toString());
    }

    @Test
    public void testTwoIndexedBuilding() {
        TensorType type = new TensorType.Builder().indexed("x").indexed("y", 3).build();
        Tensor tensor = MixedTensor.Builder.of(type).
                cell().label("x", 0).label("y", 0).value(1).
                cell().label("x", 0).label("y", 1).value(2).
                // {x:1,y:2} should be 0.0 and non NaN since we specify indexed size
                cell().label("x", 1).label("y", 0).value(4).
                cell().label("x", 1).label("y", 1).value(5).
                cell().label("x", 1).label("y", 2).value(6).
                build();
        assertEquals(Sets.newHashSet("x", "y"), tensor.type().dimensionNames());
        assertEquals("tensor(x[2],y[3]):[[1.0, 2.0, 0.0], [4.0, 5.0, 6.0]]",
                     tensor.toString());
    }

    @Test
    public void testOneMappedBuilding() {
        TensorType type = new TensorType.Builder().mapped("x").build();
        Tensor tensor = MixedTensor.Builder.of(type).
                cell().label("x", "0").value(1).
                cell().label("x", "1").value(2).
                build();
        assertEquals(Sets.newHashSet("x"), tensor.type().dimensionNames());
        assertEquals("tensor(x{}):{0:1.0, 1:2.0}",
                     tensor.toString());
    }

    @Test
    public void testTwoMappedBuilding() {
        TensorType type = new TensorType.Builder().mapped("x").mapped("y").build();
        Tensor tensor = MixedTensor.Builder.of(type).
                cell().label("x", "0").label("y", "0").value(1).
                cell().label("x", "0").label("y", "1").value(2).
                cell().label("x", "1").label("y", "0").value(4).
                cell().label("x", "1").label("y", "1").value(5).
                cell().label("x", "1").label("y", "2").value(6).
                build();
        assertEquals(Sets.newHashSet("x", "y"), tensor.type().dimensionNames());
        assertEquals("tensor(x{},y{}):{{x:0,y:0}:1.0, {x:0,y:1}:2.0, {x:1,y:0}:4.0, {x:1,y:1}:5.0, {x:1,y:2}:6.0}",
                tensor.toString());
    }

    @Test
    public void testOneMappedOneIndexedBuilding() {
        TensorType type = new TensorType.Builder().mapped("x").indexed("y", 3).build();
        Tensor tensor = MixedTensor.Builder.of(type).
                cell().label("x", "1").label("y", 0).value(1).
                cell().label("x", "1").label("y", 1).value(2).
                // {x:1,y:2} should be 0.0 and non NaN since we specify indexed size
                cell().label("x", "2").label("y", 0).value(4).
                cell().label("x", "2").label("y", 1).value(5).
                cell().label("x", "2").label("y", 2).value(6).
                build();
        assertEquals(Sets.newHashSet("x", "y"), tensor.type().dimensionNames());
        assertEquals("tensor(x{},y[3]):{1:[1.0, 2.0, 0.0], 2:[4.0, 5.0, 6.0]}",
                tensor.toString());
    }

    @Test
    public void testTwoMappedOneIndexedBuilding() {
        TensorType type = new TensorType.Builder().mapped("x").indexed("y").mapped("z").build();
        Tensor tensor = Tensor.Builder.of(type).
                cell().label("x", "x1").label("y", 0).label("z","z1").value(1).
                cell().label("x", "x1").label("y", 0).label("z","z2").value(2).
                cell().label("x", "x1").label("y", 1).label("z","z1").value(3).
                cell().label("x", "x1").label("y", 1).label("z","z2").value(4).
                cell().label("x", "x1").label("y", 2).label("z","z1").value(5).
                cell().label("x", "x1").label("y", 2).label("z","z2").value(6).
                cell().label("x", "x2").label("y", 0).label("z","z1").value(11).
                cell().label("x", "x2").label("y", 0).label("z","z2").value(12).
                cell().label("x", "x2").label("y", 1).label("z","z1").value(13).
                cell().label("x", "x2").label("y", 1).label("z","z2").value(14).
                cell().label("x", "x2").label("y", 2).label("z","z1").value(15).
                cell().label("x", "x2").label("y", 2).label("z","z2").value(16).
                build();
        assertEquals(Sets.newHashSet("x", "y", "z"), tensor.type().dimensionNames());
        assertEquals("tensor(x{},y[3],z{}):{{x:x1,y:0,z:z1}:1.0, {x:x1,y:0,z:z2}:2.0, {x:x1,y:1,z:z1}:3.0, " +
                     "{x:x1,y:1,z:z2}:4.0, {x:x1,y:2,z:z1}:5.0, {x:x1,y:2,z:z2}:6.0, {x:x2,y:0,z:z1}:11.0, " +
                     "{x:x2,y:0,z:z2}:12.0, {x:x2,y:1,z:z1}:13.0, {x:x2,y:1,z:z2}:14.0, {x:x2,y:2,z:z1}:15.0, {x:x2,y:2,z:z2}:16.0}",
                tensor.toString());
    }

    @Test
    public void testTwoMappedTwoIndexedBuilding() {
        TensorType type = new TensorType.Builder().mapped("i").indexed("j", 2).mapped("k").indexed("l", 2).build();
        Tensor tensor = Tensor.Builder.of(type).
                cell().label("i", "a").label("k","c").label("j",0).label("l",0).value(1).
                cell().label("i", "a").label("k","c").label("j",0).label("l",1).value(2).
                cell().label("i", "a").label("k","c").label("j",1).label("l",0).value(3).
                cell().label("i", "a").label("k","c").label("j",1).label("l",1).value(4).
                cell().label("i", "a").label("k","d").label("j",0).label("l",0).value(5).
                cell().label("i", "a").label("k","d").label("j",0).label("l",1).value(6).
                cell().label("i", "a").label("k","d").label("j",1).label("l",0).value(7).
                cell().label("i", "a").label("k","d").label("j",1).label("l",1).value(8).
                cell().label("i", "b").label("k","c").label("j",0).label("l",0).value(9).
                cell().label("i", "b").label("k","c").label("j",0).label("l",1).value(10).
                cell().label("i", "b").label("k","c").label("j",1).label("l",0).value(11).
                cell().label("i", "b").label("k","c").label("j",1).label("l",1).value(12).
                cell().label("i", "b").label("k","d").label("j",0).label("l",0).value(13).
                cell().label("i", "b").label("k","d").label("j",0).label("l",1).value(14).
                cell().label("i", "b").label("k","d").label("j",1).label("l",0).value(15).
                cell().label("i", "b").label("k","d").label("j",1).label("l",1).value(16).
                build();
        assertEquals(Sets.newHashSet("i", "j", "k", "l"), tensor.type().dimensionNames());
        assertEquals("tensor(i{},j[2],k{},l[2]):{{i:a,j:0,k:c,l:0}:1.0, {i:a,j:0,k:c,l:1}:2.0, " +
                     "{i:a,j:0,k:d,l:0}:5.0, {i:a,j:0,k:d,l:1}:6.0, {i:a,j:1,k:c,l:0}:3.0, {i:a,j:1,k:c,l:1}:4.0, " +
                     "{i:a,j:1,k:d,l:0}:7.0, {i:a,j:1,k:d,l:1}:8.0, {i:b,j:0,k:c,l:0}:9.0, {i:b,j:0,k:c,l:1}:10.0, " +
                     "{i:b,j:0,k:d,l:0}:13.0, {i:b,j:0,k:d,l:1}:14.0, {i:b,j:1,k:c,l:0}:11.0, {i:b,j:1,k:c,l:1}:12.0, "+
                     "{i:b,j:1,k:d,l:0}:15.0, {i:b,j:1,k:d,l:1}:16.0}",
                tensor.toString());
    }

}
