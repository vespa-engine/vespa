// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        assertIllegalTensorType("foo(x[10])", "but was 'foo(x[10])'.");
        assertIllegalTensorType("tensor(x_@[10])", "Dimension 'x_@[10]' is on the wrong format");
        assertIllegalTensorType("tensor(x[10a])", "Dimension 'x[10a]' is on the wrong format");
        assertIllegalTensorType("tensor(x{10})", "Dimension 'x{10}' is on the wrong format");
        assertIllegalTensorType("tensor<(x{})", " Value type spec must be enclosed in <>");
        assertIllegalTensorType("tensor<>(x{})", "Value type must be");
        assertIllegalTensorType("tensor<notavalue>(x{})", "Value type must be");
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

    @Test
    public void testValueType() {
        assertValueType(TensorType.Value.DOUBLE, "tensor(x[])");
        assertValueType(TensorType.Value.DOUBLE, "tensor<double>(x[])");
        assertValueType(TensorType.Value.FLOAT, "tensor<float>(x[])");
        assertValueType(TensorType.Value.BFLOAT16, "tensor<bfloat16>(x[])");
        assertValueType(TensorType.Value.INT8, "tensor<int8>(x[])");
        assertEquals("tensor(x[])", TensorType.fromSpec("tensor<double>(x[])").toString());
        assertEquals("tensor<float>(x[])", TensorType.fromSpec("tensor<float>(x[])").toString());
        assertEquals("tensor<bfloat16>(x[])", TensorType.fromSpec("tensor<bfloat16>(x[])").toString());
        assertEquals("tensor<int8>(x[])", TensorType.fromSpec("tensor<int8>(x[])").toString());
    }

    @Test
    public void testIndexedSubtype() {
        assertEquals(TensorType.fromSpec("tensor(x[10])"),
                     TensorType.fromSpec("tensor(x[10])").indexedSubtype());
        assertEquals(TensorType.fromSpec("tensor(x[10])"),
                     TensorType.fromSpec("tensor(x[10],a{})").indexedSubtype());
        assertEquals(TensorType.fromSpec("tensor(x[10],y[5])"),
                     TensorType.fromSpec("tensor(x[10],y[5],a{},b{})").indexedSubtype());
        assertEquals(TensorType.fromSpec("tensor()"),
                     TensorType.fromSpec("tensor(a{})").indexedSubtype());
    }

    @Test
    public void testMappedSubtype() {
        assertEquals(TensorType.fromSpec("tensor(a{})"),
                     TensorType.fromSpec("tensor(a{})").mappedSubtype());
        assertEquals(TensorType.fromSpec("tensor(a{})"),
                     TensorType.fromSpec("tensor(x[10],a{})").mappedSubtype());
        assertEquals(TensorType.fromSpec("tensor(a{},b{})"),
                     TensorType.fromSpec("tensor(x[10],y[5],a{},b{})").mappedSubtype());
        assertEquals(TensorType.fromSpec("tensor()"),
                     TensorType.fromSpec("tensor(x[10])").mappedSubtype());
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
            fail("Expected exception to be thrown with message: '" + messageSubstring + "'");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(messageSubstring));
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

    private void assertValueType(TensorType.Value expectedValueType, String tensorTypeSpec) {
        assertEquals(expectedValueType, TensorType.fromSpec(tensorTypeSpec).valueType());
    }

}
