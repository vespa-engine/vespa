// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class ToTensorTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ToTensorExpression("chunk");
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new ToTensorExpression("other")));
        assertEquals(exp, new ToTensorExpression("chunk"));
        assertEquals(exp.hashCode(), new ToTensorExpression("chunk").hashCode());
    }

    @Test
    public void requireThatArrayOfDenseTensorsIsConverted() throws ParseException {
        var type = documentType("tensor(x[2])", "tensor(chunk[2],x[2])");
        var exp = Expression.fromString("input tensors | to_tensor chunk | attribute combined");

        Document input = new Document(type, "id:scheme:mytype::");
        var array = tensorArray(type, "tensor(x[2]):[1, 2]", "tensor(x[2]):[3, 4]");
        input.setFieldValue("tensors", array);

        Document output = Expression.execute(exp, input);
        assertEquals(Tensor.from("tensor(chunk[2],x[2]):[[1, 2], [3, 4]]"),
                     ((TensorFieldValue)output.getFieldValue("combined")).getTensor().get());
    }

    @Test
    public void requireThatArrayOfMappedTensorsIsConverted() throws ParseException {
        var type = documentType("tensor(cat{})", "tensor(cat{},chunk[2])");
        var exp = Expression.fromString("input tensors | to_tensor chunk | attribute combined");

        Document input = new Document(type, "id:scheme:mytype::");
        var array = tensorArray(type, "tensor(cat{}):{a: 1}", "tensor(cat{}):{b: 2}");
        input.setFieldValue("tensors", array);

        Document output = Expression.execute(exp, input);
        assertEquals(Tensor.from("tensor(cat{},chunk[2]):{a: [1, 0], b: [0, 2]}"),
                     ((TensorFieldValue)output.getFieldValue("combined")).getTensor().get());
    }

    @Test
    public void requireThatArrayOfDenseTensorsIsConvertedToMappedDimension() throws ParseException {
        var type = documentType("tensor(x[2])", "tensor(idx{},x[2])");
        var exp = Expression.fromString("input tensors | to_tensor idx | attribute combined");

        Document input = new Document(type, "id:scheme:mytype::");
        var array = tensorArray(type, "tensor(x[2]):[1, 2]", "tensor(x[2]):[3, 4]", "tensor(x[2]):[5, 6]");
        input.setFieldValue("tensors", array);

        Document output = Expression.execute(exp, input);
        assertEquals(Tensor.from("tensor(idx{},x[2]):{0: [1, 2], 1: [3, 4], 2: [5, 6]}"),
                     ((TensorFieldValue)output.getFieldValue("combined")).getTensor().get());
    }

    @Test
    public void requireThatArrayOfDoublesIsConvertedToMappedDimension() throws ParseException {
        var type = scalarDocumentType(com.yahoo.document.DataType.DOUBLE, "tensor<float>(chunk{})");
        var exp = Expression.fromString("input scalars | to_tensor chunk | attribute combined");

        Document input = new Document(type, "id:scheme:mytype::");
        var array = new Array<DoubleFieldValue>(type.getField("scalars").getDataType());
        array.add(new DoubleFieldValue(1));
        array.add(new DoubleFieldValue(2));
        array.add(new DoubleFieldValue(3));
        input.setFieldValue("scalars", array);

        Document output = Expression.execute(exp, input);
        assertEquals(Tensor.from("tensor<float>(chunk{}):{0: 1, 1: 2, 2: 3}"),
                     ((TensorFieldValue)output.getFieldValue("combined")).getTensor().get());
    }

    @Test
    public void requireThatEmptyArrayProducesNoValue() throws ParseException {
        var type = documentType("tensor(x[2])", "tensor(chunk[2],x[2])");
        var exp = Expression.fromString("input tensors | to_tensor chunk | attribute combined");

        Document input = new Document(type, "id:scheme:mytype::");
        input.setFieldValue("tensors", tensorArray(type));

        Document output = Expression.execute(exp, input);
        assertNull(output.getFieldValue("combined"));
    }

    @Test
    public void requireThatOutputMustContainTheDimension() throws ParseException {
        var type = documentType("tensor(x[2])", "tensor(y[2],x[2])");
        var exp = Expression.fromString("input tensors | to_tensor chunk | attribute combined");
        try {
            exp.resolve(type);
            fail("Expected exception");
        } catch (VerificationException e) {
            assertEquals("Invalid expression 'to_tensor chunk': This produces a tensor containing the " +
                         "dimension 'chunk', but tensor(x[2],y[2]) is required",
                         e.getMessage());
        }
    }

    @Test
    public void requireThatInt8ValueRequireByteInput() throws ParseException {
        var type = scalarDocumentType(com.yahoo.document.DataType.DOUBLE, "tensor<int8>(x{})");
        var exp = Expression.fromString("input scalars | to_tensor x | attribute combined");

        Document input = new Document(type, "id:scheme:mytype::");
        var array = new Array<DoubleFieldValue>(type.getField("scalars").getDataType());
        array.add(new DoubleFieldValue(1));
        array.add(new DoubleFieldValue(2));
        array.add(new DoubleFieldValue(3));
        input.setFieldValue("scalars", array);
        try {
            exp.resolve(type);
            fail("Expected exception");
        } catch (VerificationException e) {
            assertEquals("Invalid expression 'to_tensor x': " +
                         "Input value type of datatype Array<double> (code: -2054976470) " +
                         "is incompatible with output type tensor<int8>(x{})",
                         e.getMessage());
        }
    }

    @Test
    public void requireThatInputTensorsCannotAlreadyContainTheDimension() throws ParseException {
        var type = documentType("tensor(chunk[2])", "tensor(chunk[2],x[2])");
        var exp = Expression.fromString("input tensors | to_tensor chunk | attribute combined");
        try {
            exp.resolve(type);
            fail("Expected exception");
        } catch (VerificationException e) {
            assertEquals("Invalid expression 'to_tensor chunk': The input tensors already contain the dimension 'chunk'",
                         e.getMessage());
        }
    }

    @Test
    public void requireThatNonArrayInputFails() throws ParseException {
        var type = new DocumentType("mytype");
        type.addField("myText", com.yahoo.document.DataType.STRING);
        type.addField("combined", new TensorDataType(TensorType.fromSpec("tensor(chunk[2],x[2])")));
        var exp = Expression.fromString("input myText | to_tensor chunk | attribute combined");
        try {
            exp.resolve(type);
            fail("Expected exception");
        } catch (VerificationException e) {
            assertEquals("Invalid expression 'to_tensor chunk': Expected an array of tensors or numbers as input, but got string",
                         e.getMessage());
        }
    }

    @Test
    public void requireThatArrayOfFloatsIsConverted() throws ParseException {
        var type = scalarDocumentType(com.yahoo.document.DataType.FLOAT, "tensor<float>(chunk[3])");
        var exp = Expression.fromString("input scalars | to_tensor chunk | attribute combined");

        Document input = new Document(type, "id:scheme:mytype::");
        var array = new Array<FloatFieldValue>(type.getField("scalars").getDataType());
        array.add(new FloatFieldValue(1));
        array.add(new FloatFieldValue(2));
        array.add(new FloatFieldValue(3));
        input.setFieldValue("scalars", array);

        Document output = Expression.execute(exp, input);
        assertEquals(Tensor.from("tensor<float>(chunk[3]):[1, 2, 3]"),
                     ((TensorFieldValue)output.getFieldValue("combined")).getTensor().get());
    }

    @Test
    public void requireThatArrayOfIntsIsConverted() throws ParseException {
        var type = scalarDocumentType(com.yahoo.document.DataType.INT, "tensor(chunk[2])");
        var exp = Expression.fromString("input scalars | to_tensor chunk | attribute combined");

        Document input = new Document(type, "id:scheme:mytype::");
        var array = new Array<IntegerFieldValue>(type.getField("scalars").getDataType());
        array.add(new IntegerFieldValue(6));
        array.add(new IntegerFieldValue(9));
        input.setFieldValue("scalars", array);

        Document output = Expression.execute(exp, input);
        assertEquals(Tensor.from("tensor(chunk[2]):[6, 9]"),
                     ((TensorFieldValue)output.getFieldValue("combined")).getTensor().get());
    }

    @Test
    public void requireThatScalarInputRequiresSingleDimensionOutput() throws ParseException {
        var type = scalarDocumentType(com.yahoo.document.DataType.FLOAT, "tensor(chunk[2],x[2])");
        var exp = Expression.fromString("input scalars | to_tensor chunk | attribute combined");
        try {
            exp.resolve(type);
            fail("Expected exception");
        } catch (VerificationException e) {
            assertEquals("Invalid expression 'to_tensor chunk': The input is an array of numbers, so this can only " +
                         "produce a tensor with the single dimension 'chunk', but tensor(chunk[2],x[2]) is required",
                         e.getMessage());
        }
    }

    private DocumentType scalarDocumentType(com.yahoo.document.DataType elementType, String combinedTypeSpec) {
        var type = new DocumentType("mytype");
        type.addField("scalars", new ArrayDataType(elementType));
        type.addField("combined", new TensorDataType(TensorType.fromSpec(combinedTypeSpec)));
        return type;
    }

    private DocumentType documentType(String elementTypeSpec, String combinedTypeSpec) {
        var type = new DocumentType("mytype");
        type.addField("tensors", new ArrayDataType(new TensorDataType(TensorType.fromSpec(elementTypeSpec))));
        type.addField("combined", new TensorDataType(TensorType.fromSpec(combinedTypeSpec)));
        return type;
    }

    private Array<TensorFieldValue> tensorArray(DocumentType type, String ... tensorSpecs) {
        var array = new Array<TensorFieldValue>(type.getField("tensors").getDataType());
        for (String tensorSpec : tensorSpecs)
            array.add(new TensorFieldValue(Tensor.from(tensorSpec)));
        return array;
    }

}
