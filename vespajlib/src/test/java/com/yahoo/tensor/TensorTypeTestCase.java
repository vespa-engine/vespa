// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import org.junit.Test;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author geirst
 * @author bratseth
 */
public class TensorTypeTestCase {

    @Test
    public void requireThatAnEmptyTensorTypeCanBeSpecified() {
        assertTensorType("tensor()");
    }

    @Test
    public void requireThatBoundIndexedDimensionsCanBeSpecified() {
        assertTensorType("tensor(x[5])");
        assertTensorType("tensor(x[5],y[10],z[100])");
        assertTensorType("tensor(x[5],y[10],z[100])", "tensor( x[5] , y[10] , z[100] )");
        assertTensorType("tensor(baR_09[10])");
    }

    @Test
    public void requireThatUnboundIndexedDimensionsCanBeSpecified() {
        assertTensorType("tensor(x[])");
        assertTensorType("tensor(x[],y[],z[])");
        assertTensorType("tensor(x[],y[],z[])", "tensor( x[] , y[] , z[] )");
        assertTensorType("tensor(baR_09[])");
    }

    @Test
    public void requireThatMappedDimensionsCanBeSpecified() {
        assertTensorType("tensor(x{})");
        assertTensorType("tensor(x{},y{},z{})");
        assertTensorType("tensor(x{},y{},z{})", "tensor( x{} , y{} , z{} )");
        assertTensorType("tensor(baR_09{})");
    }

    @Test
    public void requireThatIndexedBoundDimensionMustHaveNonZeroSize() {
        assertIllegalTensorType("tensor(x[0])", "Size of bound dimension 'x' must be at least 1");
    }

    @Test
    public void requireThatDimensionsMustHaveUniqueNames() {
        assertIllegalTensorType("tensor(x[10],y[20],x[30])", "Could not add dimension x[30] as this dimension is already present");
        assertIllegalTensorType("tensor(x{},y{},x{})", "Could not add dimension x{} as this dimension is already present");
    }

    @Test
    public void requireThatIllegalSyntaxInSpecThrowsException() {
        assertIllegalTensorType("foo(x[10])", "Tensor type spec must start with 'tensor(' and end with ')', but was 'foo(x[10])'");
        assertIllegalTensorType("tensor(x_@[10])", "Failed parsing element 'x_@[10]' in type spec 'tensor(x_@[10])'");
        assertIllegalTensorType("tensor(x[10a])", "Failed parsing element 'x[10a]' in type spec 'tensor(x[10a])'");
        assertIllegalTensorType("tensor(x{10})", "Failed parsing element 'x{10}' in type spec 'tensor(x{10})'");
    }

    @Test
    public void testAssignableTo() {
        assertIsAssignableTo("tensor(x[])", "tensor(x[])");
        assertUnassignableTo("tensor(x[])", "tensor(y[])");
        assertIsAssignableTo("tensor(x[10])", "tensor(x[])");
        assertUnassignableTo("tensor(x[])", "tensor(x[10])");
        assertUnassignableTo("tensor(x[10])", "tensor(x[5])");
        assertUnassignableTo("tensor(x[5])", "tensor(x[10])");
        assertUnassignableTo("tensor(x{})", "tensor(x[])");
        assertIsAssignableTo("tensor(x{},y[10])", "tensor(x{},y[])");
    }

    @Test
    public void testConvertibleTo() {
        assertIsConvertibleTo("tensor(x[])", "tensor(x[])");
        assertUnconvertibleTo("tensor(x[])", "tensor(y[])");
        assertIsConvertibleTo("tensor(x[10])", "tensor(x[])");
        assertUnconvertibleTo("tensor(x[])", "tensor(x[10])");
        assertUnconvertibleTo("tensor(x[10])", "tensor(x[5])");
        assertIsConvertibleTo("tensor(x[5])", "tensor(x[10])"); // Different from assignable
        assertUnconvertibleTo("tensor(x{})", "tensor(x[])");
        assertIsConvertibleTo("tensor(x{},y[10])", "tensor(x{},y[])");
    }

    private static void assertTensorType(String typeSpec) {
        assertTensorType(typeSpec, typeSpec);
    }

    private static void assertTensorType(String expected, String typeSpec) {
        assertEquals(expected, TensorType.fromSpec(typeSpec).toString());
    }

    private static void assertIllegalTensorType(String typeSpec, String messageSubstring) {
        try {
            TensorType.fromSpec(typeSpec);
            fail("Expoected exception to be thrown with message: '" + messageSubstring + "'");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString(messageSubstring));
        }
    }

    private void assertIsAssignableTo(String specificType, String generalType) {
        assertTrue(TensorType.fromSpec(specificType).isAssignableTo(TensorType.fromSpec(generalType)));
    }

    private void assertUnassignableTo(String specificType, String generalType) {
        assertFalse(TensorType.fromSpec(specificType).isAssignableTo(TensorType.fromSpec(generalType)));
    }

    private void assertIsConvertibleTo(String specificType, String generalType) {
        assertTrue(TensorType.fromSpec(specificType).isConvertibleTo(TensorType.fromSpec(generalType)));
    }

    private void assertUnconvertibleTo(String specificType, String generalType) {
        assertFalse(TensorType.fromSpec(specificType).isConvertibleTo(TensorType.fromSpec(generalType)));
    }

}
