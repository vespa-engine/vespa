// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * @author glebashnik
 */
public class MultipartExpressionTestCase {

    private final TypeContext context = new TypeContext(new SimpleTestAdapter(new Field("myText", DataType.STRING),
                                                                               new Field("myImage", DataType.RAW)));

    @Test
    public void testEachPartMustProduceItsOwnType() {
        var multipart = new MultipartExpression(List.of(new InputExpression("myText"), new InputExpression("myImage")));
        assertEquals(AnyDataType.instance,
                     multipart.setOutputType(new MultipartDataType(List.of(DataType.STRING, DataType.RAW)), context));

        var e = assertThrows(VerificationException.class,
                             () -> multipart.setOutputType(new MultipartDataType(List.of(DataType.RAW, DataType.STRING)), context));
        assertEquals("Invalid expression 'input myText': This produces type string but raw is required", e.getMessage());
    }

    @Test
    public void testPartWithUnknownTypeIsNotChecked() {
        var multipart = new MultipartExpression(List.of(new InputExpression("myText"), new InputExpression("myImage")));
        assertEquals(AnyDataType.instance, multipart.setOutputType(new MultipartDataType(Arrays.asList(DataType.STRING, null)), context));
        assertEquals(AnyDataType.instance, multipart.setOutputType(null, context));
    }

    @Test
    public void testNumberOfPartsMustMatch() {
        var multipart = new MultipartExpression(List.of(new InputExpression("myText"), new InputExpression("myImage")));
        var e = assertThrows(VerificationException.class,
                             () -> multipart.setOutputType(new MultipartDataType(List.of(DataType.STRING)), context));
        assertEquals("Invalid expression 'input myText, input myImage': " +
                     "This has 2 parts, but the required number of parts is 1", e.getMessage());
    }

    @Test
    public void testOutputMustBeMultipartType() {
        var multipart = new MultipartExpression(List.of(new InputExpression("myText"), new InputExpression("myImage")));
        var e = assertThrows(VerificationException.class, () -> multipart.setOutputType(DataType.STRING, context));
        assertEquals("Invalid expression 'input myText, input myImage': " +
                     "Required to produce string, but this produces a multipart input", e.getMessage());

        e = assertThrows(VerificationException.class,
                         () -> multipart.setOutputType(new ArrayDataType(AnyDataType.instance), context));
        assertEquals("Invalid expression 'input myText, input myImage': " +
                     "Required to produce Array<any>, but this produces a multipart input", e.getMessage());
    }

    @Test
    public void testInputTypeIsWhatAllPartsNeed() {
        var multipart = new MultipartExpression(List.of(new LowerCaseExpression(), new InputExpression("myImage")));
        assertEquals(DataType.STRING,
                     multipart.setOutputType(new MultipartDataType(List.of(DataType.STRING, DataType.RAW)), context));
        assertEquals(DataType.STRING, multipart.setOutputType(null, context));
    }

}
