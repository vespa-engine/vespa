// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.objects.BufferSerializer;
import com.yahoo.vespa.objects.Identifiable;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author baldersheim
 */
public class ExpressionTestCase {

    private static final double delta = 0.00000001;

    @Test
    public void testRangeBucketPreDefFunctionNode() {
        assertMultiArgFunctionNode(new RangeBucketPreDefFunctionNode(new StringBucketResultNodeVector().add(new StringBucketResultNode("10", "20")), new AttributeNode("foo")));
        assertEquals(new RangeBucketPreDefFunctionNode(), new RangeBucketPreDefFunctionNode());
        assertEquals(new RangeBucketPreDefFunctionNode(new StringBucketResultNodeVector().add(new StringBucketResultNode("10", "20")), new AttributeNode("foo")),
                     new RangeBucketPreDefFunctionNode(new StringBucketResultNodeVector().add(new StringBucketResultNode("10", "20")), new AttributeNode("foo")));
        assertNotEquals(new RangeBucketPreDefFunctionNode(new StringBucketResultNodeVector().add(new StringBucketResultNode("10", "20")), new AttributeNode("foo")),
                        new RangeBucketPreDefFunctionNode(new StringBucketResultNodeVector().add(new StringBucketResultNode("10", "21")), new AttributeNode("foo")));
        assertNotEquals(new RangeBucketPreDefFunctionNode(new StringBucketResultNodeVector().add(new StringBucketResultNode("10", "20")), new AttributeNode("foo")),
                        new RangeBucketPreDefFunctionNode(new StringBucketResultNodeVector().add(new StringBucketResultNode("10", "20")), new AttributeNode("bar")));
    }

    @Test
    public void testFixedWidthBucketFunctionNode() {
        assertMultiArgFunctionNode(new FixedWidthBucketFunctionNode());
        assertEquals(new FixedWidthBucketFunctionNode(), new FixedWidthBucketFunctionNode());
        assertEquals(new FixedWidthBucketFunctionNode(new IntegerResultNode(5), new AttributeNode("foo")),
                     new FixedWidthBucketFunctionNode(new IntegerResultNode(5), new AttributeNode("foo")));
        assertNotEquals(new FixedWidthBucketFunctionNode(new IntegerResultNode(5), new AttributeNode("foo")),
                        new FixedWidthBucketFunctionNode(new IntegerResultNode(6), new AttributeNode("foo")));
        assertNotEquals(new FixedWidthBucketFunctionNode(new IntegerResultNode(5), new AttributeNode("foo")),
                        new FixedWidthBucketFunctionNode(new IntegerResultNode(5), new AttributeNode("bar")));
    }

    @Test
    public void testIntegerBucketResultNodeVector() {
        assertResultNode(new IntegerBucketResultNodeVector().add(new IntegerBucketResultNode(10, 20)));
        assertEquals(new IntegerBucketResultNodeVector().add(new IntegerBucketResultNode(10, 20)),
                     new IntegerBucketResultNodeVector().add(new IntegerBucketResultNode(10, 20)));
        assertNotEquals(new IntegerBucketResultNodeVector().add(new IntegerBucketResultNode(10, 20)),
                        new IntegerBucketResultNodeVector());
        assertNotEquals(new IntegerBucketResultNodeVector().add(new IntegerBucketResultNode(10, 20)),
                        new IntegerBucketResultNodeVector().add(new IntegerBucketResultNode(11, 20)));
    }

    @Test
    public void testFloatBucketResultNodeVector() {
        assertResultNode(new FloatBucketResultNodeVector().add(new FloatBucketResultNode(10, 20)));
        assertEquals(new FloatBucketResultNodeVector().add(new FloatBucketResultNode(10, 20)),
                     new FloatBucketResultNodeVector().add(new FloatBucketResultNode(10, 20)));
        assertNotEquals(new FloatBucketResultNodeVector().add(new FloatBucketResultNode(10, 20)),
                        new FloatBucketResultNodeVector());
        assertNotEquals(new FloatBucketResultNodeVector().add(new FloatBucketResultNode(10, 20)),
                        new FloatBucketResultNodeVector().add(new FloatBucketResultNode(11, 20)));
    }

    @Test
    public void testStringBucketResultNodeVector() {
        assertResultNode(new StringBucketResultNodeVector().add(new StringBucketResultNode("10", "20")));
        assertEquals(new StringBucketResultNodeVector().add(new StringBucketResultNode("10", "20")),
                     new StringBucketResultNodeVector().add(new StringBucketResultNode("10", "20")));
        assertNotEquals(new StringBucketResultNodeVector().add(new StringBucketResultNode("10", "20")),
                        new StringBucketResultNodeVector());
        assertNotEquals(new StringBucketResultNodeVector().add(new StringBucketResultNode("10", "20")),
                        new StringBucketResultNodeVector().add(new StringBucketResultNode("11", "20")));
    }

    @Test
    public void testIntegerBucketResultNode() {
        assertResultNode(new IntegerBucketResultNode(10, 20));
        assertEquals(new IntegerBucketResultNode(10, 20), new IntegerBucketResultNode(10, 20));
        assertNotEquals(new IntegerBucketResultNode(10, 20), new IntegerBucketResultNode(11, 20));
        assertNotEquals(new IntegerBucketResultNode(10, 20), new IntegerBucketResultNode(10, 21));
    }

    @Test
    public void testFloatBucketResultNode() {
        assertResultNode(new FloatBucketResultNode(10.0, 20.0));
        assertEquals(new FloatBucketResultNode(10.0, 20.0), new FloatBucketResultNode(10.0, 20.0));
        assertNotEquals(new FloatBucketResultNode(10.0, 20.0), new FloatBucketResultNode(11.0, 20.0));
        assertNotEquals(new FloatBucketResultNode(10.0, 20.0), new FloatBucketResultNode(10.0, 21.0));
    }

    @Test
    public void testStringBucketResultNode() {
        assertResultNode(new StringBucketResultNode("10.0", "20.0"));
        assertEquals(new StringBucketResultNode("10.0", "20.0"), new StringBucketResultNode("10.0", "20.0"));
        assertNotEquals(new StringBucketResultNode("10.0", "20.0"), new StringBucketResultNode("11.0", "20.0"));
        assertNotEquals(new StringBucketResultNode("10.0", "20.0"), new StringBucketResultNode("10.0", "21.0"));
        compare(new StringBucketResultNode("10.0", "20.0"), new StringBucketResultNode("10.0", "21.0"), new StringBucketResultNode("10.0", "22.0"));
        compare(new StringBucketResultNode("10.0", "20.0"), new StringBucketResultNode("11.0", "19.0"), new StringBucketResultNode("11.0", "20.0"));
        compare(new StringBucketResultNode(StringResultNode.getNegativeInfinity(), new StringResultNode("20.0")),
                new StringBucketResultNode("11.0", "19.0"), new StringBucketResultNode("11.0", "20.0"));
        compare(new StringBucketResultNode(StringResultNode.getNegativeInfinity(), new StringResultNode("20.0")),
                new StringBucketResultNode(StringResultNode.getNegativeInfinity(), new StringResultNode("21.0")),
                new StringBucketResultNode("11.0", "20.0"));
        compare(new StringBucketResultNode("10.0", "20.0"), new StringBucketResultNode("10.0", "21.0"),
                new StringBucketResultNode(new StringResultNode("10.0"), StringResultNode.getPositiveInfinity()));
        compare(new StringBucketResultNode(new StringResultNode("10.0"), StringResultNode.getPositiveInfinity()),
                new StringBucketResultNode("11.0", "19.0"), new StringBucketResultNode("11.0", "20.0"));
    }

    @Test
    public void testPositiveInfinity() {
        PositiveInfinityResultNode inf = new PositiveInfinityResultNode();
        PositiveInfinityResultNode inf2 = new PositiveInfinityResultNode();
        assertResultNode(inf);
        assertEquals(inf, inf2);
    }

    @Test
    public void testAddFunctionNode() {
        assertMultiArgFunctionNode(new AddFunctionNode());
        assertFunctionNode(new AddFunctionNode().addArg(new ConstantNode(new IntegerResultNode(2)))
                                                .addArg(new ConstantNode(new IntegerResultNode(3))),
                           5, 5.0, "5", longAsRaw(5));
        assertFunctionNode(new AddFunctionNode().addArg(new ConstantNode(new FloatResultNode(3.0)))
                                                .addArg(new ConstantNode(new IntegerResultNode(2))),
                           5, 5.0, "5.0", doubleAsRaw(5.0));
        assertFunctionNode(new AddFunctionNode().addArg(new ConstantNode(new IntegerResultNode(3)))
                                                .addArg(new ConstantNode(new FloatResultNode(2.0))),
                           5, 5.0, "5.0", doubleAsRaw(5.0));
    }

    @Test
    public void testAndFunctionNode() {
        assertMultiArgFunctionNode(new AndFunctionNode());
        assertFunctionNode(new AndFunctionNode().addArg(new ConstantNode(new IntegerResultNode(3)))
                                                .addArg(new ConstantNode(new IntegerResultNode(7))),
                           3, 3.0, "3", longAsRaw(3));
    }

    @Test
    public void testZCurveFunctionNode() {
        assertMultiArgFunctionNode(
                new ZCurveFunctionNode(new ConstantNode(new IntegerResultNode(7)), ZCurveFunctionNode.Dimension.Y));
    }

    @Test
    public void testTimeStampFunctionNode() {
        assertMultiArgFunctionNode(new TimeStampFunctionNode(new AttributeNode("testattribute"), TimeStampFunctionNode.TimePart.Hour, true));
        assertEquals(new TimeStampFunctionNode(new AttributeNode("testattribute"), TimeStampFunctionNode.TimePart.Hour, true),
                     new TimeStampFunctionNode(new AttributeNode("testattribute"), TimeStampFunctionNode.TimePart.Hour, true));
        assertNotEquals(
                new TimeStampFunctionNode(new AttributeNode("testattribute"), TimeStampFunctionNode.TimePart.Hour,
                                          true),
                new TimeStampFunctionNode(new AttributeNode("testattributt"), TimeStampFunctionNode.TimePart.Hour,
                                          true));
        assertNotEquals(
                new TimeStampFunctionNode(new AttributeNode("testattribute"), TimeStampFunctionNode.TimePart.Hour,
                                          true),
                new TimeStampFunctionNode(new AttributeNode("testattribute"), TimeStampFunctionNode.TimePart.Year,
                                          true));
        assertNotEquals(
                new TimeStampFunctionNode(new AttributeNode("testattribute"), TimeStampFunctionNode.TimePart.Hour,
                                          true),
                new TimeStampFunctionNode(new AttributeNode("testattribute"), TimeStampFunctionNode.TimePart.Hour,
                                          false));
    }

    @Test
    public void testExpressionRefNode() {
        AggregationRefNode ref = new AggregationRefNode(3);
        assertEquals(3, ref.getIndex());
    }

    @Test
    public void testAttributeNode() {
        try {
            new AttributeNode(null);
            fail("Should not be able to set null attribute name.");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new AttributeNode().setAttributeName(null);
            fail("Should not be able to set null attribute name.");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new AttributeNode().prepare();
            fail("Should not be possible to prepare or execute attribute node");
        } catch (RuntimeException e) {
            // expected
        }
        try {
            new AttributeNode().execute();
            fail("Should not be possible to prepare or execute attribute node");
        } catch (RuntimeException e) {
            // expected
        }
        AttributeNode a = new AttributeNode("testattribute");
        assertEquals("testattribute", a.getAttributeName());
        AttributeNode b = (AttributeNode)assertSerialize(a);
        assertEquals("testattribute", b.getAttributeName());
        AttributeNode c = new AttributeNode("testattribute");
        assertEquals(b, c);
        c.setAttributeName("fail");
        assertFalse(b.equals(c));
    }

    @Test
    public void testInterpolatedLookupNode() {
        ExpressionNode argA = new ConstantNode(new FloatResultNode(2.71828182846));
        ExpressionNode argB = new ConstantNode(new FloatResultNode(3.14159265359));
        try {
            new InterpolatedLookupNode(null, argA);
            fail("Should not be able to set null attribute name.");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new InterpolatedLookupNode().setAttributeName(null);
            fail("Should not be able to set null attribute name.");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new InterpolatedLookupNode().prepare();
            fail("Should not be possible to prepare or execute interpolatedlookup node");
        } catch (RuntimeException e) {
            // expected
        }
        try {
            new InterpolatedLookupNode().execute();
            fail("Should not be possible to prepare or execute interpolatedlookup node");
        } catch (RuntimeException e) {
            // expected
        }
        ExpressionNode a1 = new InterpolatedLookupNode().setAttributeName("foo").addArg(argA);
        InterpolatedLookupNode a2 = new InterpolatedLookupNode("foo", argA);
        assertEquals("foo", ((InterpolatedLookupNode)a1).getAttributeName());
        assertEquals("foo", a2.getAttributeName());
        assertEquals(argA, ((InterpolatedLookupNode)a1).getArg());
        assertEquals(argA, a2.getArg());
        assertEquals(a1, a2);
        InterpolatedLookupNode b1 = new InterpolatedLookupNode("foo", argB);
        InterpolatedLookupNode b2 = new InterpolatedLookupNode("bar", argA);
        assertFalse(a1.equals(b1));
        assertFalse(a1.equals(b2));
        assertFalse(a2.equals(b1));
        assertFalse(a2.equals(b2));
        a2.setAttributeName("fail");
        assertFalse(a1.equals(a2));
    }

    @Test
    public void testCatFunctionNode() {
        assertMultiArgFunctionNode(new CatFunctionNode());
        assertFunctionNode(new CatFunctionNode().addArg(new ConstantNode(new RawResultNode(asRaw('1', '2'))))
                                                .addArg(new ConstantNode(new RawResultNode(asRaw('3', '4')))),
                           0, 0.0, "1234", asRaw('1', '2', '3', '4'));
    }

    @Test
    public void testStrCatFunctionNode() {
        assertMultiArgFunctionNode(new StrCatFunctionNode());
        assertFunctionNode(new StrCatFunctionNode().addArg(new ConstantNode(new StringResultNode("foo")))
                                                   .addArg(new ConstantNode(new StringResultNode("bar"))),
                           0, 0.0, "foobar", stringAsRaw("foobar"));
    }

    @Test
    public void testDivideFunctionNode() {
        assertMultiArgFunctionNode(new DivideFunctionNode());
        assertFunctionNode(new DivideFunctionNode().addArg(new ConstantNode(new IntegerResultNode(10)))
                                                   .addArg(new ConstantNode(new IntegerResultNode(2))),
                           5, 5.0, "5", longAsRaw(5));
        assertFunctionNode(new DivideFunctionNode().addArg(new ConstantNode(new IntegerResultNode(6)))
                                                   .addArg(new ConstantNode(new FloatResultNode(2.0))),
                           3, 3.0, "3.0", doubleAsRaw(3.0));
        assertFunctionNode(new DivideFunctionNode().addArg(new ConstantNode(new IntegerResultNode(6)))
                                                   .addArg(new ConstantNode(new FloatResultNode(12.0))),
                           1, 0.5, "0.5", doubleAsRaw(0.5));
    }

    @Test
    public void testDocumentFieldNode() {
        try {
            new DocumentFieldNode(null);
            fail("Should not be able to set null field name.");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new DocumentFieldNode().setDocumentFieldName(null);
            fail("Should not be able to set null field name.");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new DocumentFieldNode("foo").prepare();
            fail("Should not be able to prepare documentfieldnode");
        } catch (RuntimeException e) {
            // expected
        }
        try {
            new DocumentFieldNode("foo").execute();
            fail("Should not be able to execute documentfieldnode");
        } catch (RuntimeException e) {
            // expected
        }
        DocumentFieldNode a = new DocumentFieldNode("testdocumentfield");
        assertEquals("testdocumentfield", a.getDocumentFieldName());
        DocumentFieldNode b = (DocumentFieldNode)assertSerialize(a);
        assertEquals("testdocumentfield", b.getDocumentFieldName());
        DocumentFieldNode c = new DocumentFieldNode("testdocumentfield");
        assertEquals(b, c);
        c.setDocumentFieldName("fail");
        assertFalse(b.equals(c));
    }

    @Test
    public void testFloatResultNode() {
        FloatResultNode a = new FloatResultNode(7.3);
        assertEquals(a.getInteger(), 7);
        assertEquals(a.getFloat(), 7.3, delta);
        assertEquals(a.getString(), "7.3");
        assertEquals(a.getNumber(), Double.valueOf(7.3));
        byte[] raw = a.getRaw();
        assertEquals(raw.length, 8);
        assertResultNode(a);
        compare(new FloatResultNode(-1), new FloatResultNode(0), new FloatResultNode(1));
        a.set(new FloatResultNode(4));
        assertResultNode(a);

        FloatResultNode b = new FloatResultNode(7.5);
        assertEquals(b.getInteger(), 8);
        assertEquals(b.getFloat(), 7.5, delta);
        assertEquals(b.getString(), "7.5");
        assertEquals(b.getNumber(), Double.valueOf(7.5));
    }

    @Test
    public void testGetDocIdNamespaceSpecificFunctionNode() {
        GetDocIdNamespaceSpecificFunctionNode a = new GetDocIdNamespaceSpecificFunctionNode(new IntegerResultNode(7));
        assertTrue(a.getResult() instanceof IntegerResultNode);
        GetDocIdNamespaceSpecificFunctionNode b = (GetDocIdNamespaceSpecificFunctionNode)assertSerialize(a);
        assertTrue(b.getResult() instanceof IntegerResultNode);
        assertEquals(7, b.getResult().getInteger());
        GetDocIdNamespaceSpecificFunctionNode c = new GetDocIdNamespaceSpecificFunctionNode(new IntegerResultNode(7));
        assertEquals(b, c);
        try {
            new GetDocIdNamespaceSpecificFunctionNode(new IntegerResultNode(7)).prepare();
            fail("Should not be able to prepare documentfieldnode");
        } catch (RuntimeException e) {
            // expected
        }
        try {
            new GetDocIdNamespaceSpecificFunctionNode(new IntegerResultNode(7)).execute();
            fail("Should not be able to execute documentfieldnode");
        } catch (RuntimeException e) {
            // expected
        }
    }

    @Test
    public void testGetYMUMChecksumFunctionNode() {
        GetYMUMChecksumFunctionNode a = new GetYMUMChecksumFunctionNode();
        assertTrue(a.getResult() instanceof IntegerResultNode);
        assertSerialize(a);
        try {
            new GetYMUMChecksumFunctionNode().prepare();
            fail("Should not be able to prepare documentfieldnode");
        } catch (RuntimeException e) {
            // expected
        }
        try {
            new GetYMUMChecksumFunctionNode().execute();
            fail("Should not be able to execute documentfieldnode");
        } catch (RuntimeException e) {
            // expected
        }
    }

    @Test
    public void testIntegerResultNode() {
        IntegerResultNode a = new IntegerResultNode(7);
        assertEquals(a.getInteger(), 7);
        assertEquals(a.getFloat(), 7.0, delta);
        assertEquals(a.getString(), "7");
        assertEquals(a.getNumber(), Long.valueOf(7));
        byte[] raw = a.getRaw();
        assertEquals(raw.length, 8);
        assertResultNode(a);
        compare(new IntegerResultNode(-1), new IntegerResultNode(0), new IntegerResultNode(1));
        compare(new IntegerResultNode(-1), new IntegerResultNode(0), new IntegerResultNode(0x80000000L));
    }

    @Test
    public void testMaxFunctionNode() {
        assertMultiArgFunctionNode(new MaxFunctionNode());
        assertFunctionNode(new MaxFunctionNode().addArg(new ConstantNode(new IntegerResultNode(3)))
                                                .addArg(new ConstantNode(new IntegerResultNode(5))),
                           5, 5.0, "5", longAsRaw(5));
        assertFunctionNode(new MaxFunctionNode().addArg(new ConstantNode(new FloatResultNode(4.9999999)))
                                                .addArg(new ConstantNode(new IntegerResultNode(5))),
                           5, 5.0, "5.0", doubleAsRaw(5.0));
    }

    @Test
    public void testMD5BitFunctionNode() {
        try {
            new MD5BitFunctionNode(null, 64);
            fail("Should not be able to set null argument.");
        } catch (NullPointerException e) {
            // expected
        }
        try {
            new MD5BitFunctionNode().prepare();
            fail("Should not be able to run prepare.");
        } catch (RuntimeException e) {
            // expected
        }
        try {
            new MD5BitFunctionNode().execute();
            fail("Should not be able to run execute.");
        } catch (RuntimeException e) {
            // expected
        }
        assertUnaryBitFunctionNode(new MD5BitFunctionNode());
    }

    @Test
    public void testMinFunctionNode() {
        assertMultiArgFunctionNode(new MinFunctionNode());
        assertFunctionNode(new MinFunctionNode().addArg(new ConstantNode(new IntegerResultNode(3)))
                                                .addArg(new ConstantNode(new IntegerResultNode(5))),
                           3, 3.0, "3", longAsRaw(3));
        assertFunctionNode(new MinFunctionNode().addArg(new ConstantNode(new FloatResultNode(4.9999999)))
                                                .addArg(new ConstantNode(new IntegerResultNode(5))),
                           5, 4.9999999, "4.9999999", doubleAsRaw(4.9999999));
    }

    @Test
    public void testModuloFunctionNode() {
        assertMultiArgFunctionNode(new ModuloFunctionNode());
        assertFunctionNode(new ModuloFunctionNode().addArg(new ConstantNode(new IntegerResultNode(13)))
                                                   .addArg(new ConstantNode(new IntegerResultNode(5))),
                           3, 3.0, "3", longAsRaw(3));
        assertFunctionNode(new ModuloFunctionNode().addArg(new ConstantNode(new FloatResultNode(4.9999999)))
                                                   .addArg(new ConstantNode(new IntegerResultNode(5))),
                           5, 4.9999999, "4.9999999", doubleAsRaw(4.9999999));
    }

    @Test
    public void testMultiplyFunctionNode() {
        assertMultiArgFunctionNode(new MultiplyFunctionNode());
        assertFunctionNode(new MultiplyFunctionNode().addArg(new ConstantNode(new IntegerResultNode(3)))
                                                     .addArg(new ConstantNode(new IntegerResultNode(5))),
                           15, 15.0, "15", longAsRaw(15));
        assertFunctionNode(new MultiplyFunctionNode().addArg(new ConstantNode(new FloatResultNode(4.5)))
                                                     .addArg(new ConstantNode(new IntegerResultNode(5))),
                           23, 22.5, "22.5", doubleAsRaw(22.5));
    }

    @Test
    public void testNegateFunctionNode() {
        assertMultiArgFunctionNode(new NegateFunctionNode());
        assertFunctionNode(new NegateFunctionNode().addArg(new ConstantNode(new IntegerResultNode(3))),
                           -3, -3.0, "-3", longAsRaw(-3));
        assertFunctionNode(new NegateFunctionNode().addArg(new ConstantNode(new FloatResultNode(3.0))),
                           -3, -3.0, "-3.0", doubleAsRaw(-3.0));
    }

    @Test
    public void testSortFunctionNode() {
        assertMultiArgFunctionNode(new SortFunctionNode());
    }

    @Test
    public void testReverseFunctionNode() {
        assertMultiArgFunctionNode(new ReverseFunctionNode());
    }

    @Test
    public void testToIntFunctionNode() {
        assertMultiArgFunctionNode(new ToIntFunctionNode());
        assertFunctionNode(new ToIntFunctionNode().addArg(new ConstantNode(new StringResultNode("1337"))),
                           1337, 1337.0, "1337", longAsRaw(1337));
    }

    @Test
    public void testToFloatFunctionNode() {
        assertMultiArgFunctionNode(new ToFloatFunctionNode());
        assertFunctionNode(new ToFloatFunctionNode().addArg(new ConstantNode(new FloatResultNode(3.14))),
                           3, 3.14, "3.14", doubleAsRaw(3.14));
    }

    @Test
    public void testMathFunctionNode() {
        assertMultiArgFunctionNode(new MathFunctionNode(MathFunctionNode.Function.LOG10));
        assertFunctionNode(new MathFunctionNode(MathFunctionNode.Function.LOG10).addArg(new ConstantNode(new IntegerResultNode(100000))),
                           5, 5.0, "5.0", doubleAsRaw(5.0));
    }

    @Test
    public void testStrLenFunctionNode() {
        assertMultiArgFunctionNode(new StrLenFunctionNode());
        assertFunctionNode(new StrLenFunctionNode().addArg(new ConstantNode(new StringResultNode("foo"))),
                           3, 3.0, "3", longAsRaw(3));
    }

    @Test
    public void testNormalizeSubjectFunctionNode() {
        assertMultiArgFunctionNode(new NormalizeSubjectFunctionNode());
        assertFunctionNode(new NormalizeSubjectFunctionNode().addArg(new ConstantNode(new StringResultNode("Re: Your mail"))),
                           0, 0, "Your mail", stringAsRaw("Your mail"));
    }

    @Test
    public void testNormalizeSubjectFunctionNode2() {
        assertMultiArgFunctionNode(new NormalizeSubjectFunctionNode());
        assertFunctionNode(new NormalizeSubjectFunctionNode().addArg(new ConstantNode(new StringResultNode("Your mail"))),
                           0, 0, "Your mail", stringAsRaw("Your mail"));
    }

    @Test
    public void testNumElemFunctionNode() {
        assertMultiArgFunctionNode(new NumElemFunctionNode());
        assertFunctionNode(new NumElemFunctionNode().addArg(new ConstantNode(new IntegerResultNode(1337))),
                           1, 1.0, "1", longAsRaw(1));
    }

    @Test
    public void testToStringFunctionNode() {
        assertMultiArgFunctionNode(new ToStringFunctionNode());
        assertFunctionNode(new ToStringFunctionNode().addArg(new ConstantNode(new IntegerResultNode(1337))),
                           1337, 1337.0, "1337", stringAsRaw("1337"));
    }

    @Test
    public void testToRawFunctionNode() {
        assertMultiArgFunctionNode(new ToRawFunctionNode());
        assertFunctionNode(new ToRawFunctionNode().addArg(new ConstantNode(new IntegerResultNode(1337))),
                           1337, 1337.0, "1337", longAsRaw(1337));
    }

    @Test
    public void testOrFunctionNode() {
        assertMultiArgFunctionNode(new OrFunctionNode());
        assertFunctionNode(new OrFunctionNode().addArg(new ConstantNode(new IntegerResultNode(2)))
                                               .addArg(new ConstantNode(new IntegerResultNode(4))),
                           6, 6.0, "6", longAsRaw(6));
    }

    @Test
    public void testDebugWaitFunctionNode() {
    	assertFunctionNode(
                new DebugWaitFunctionNode(new OrFunctionNode().addArg(new ConstantNode(new IntegerResultNode(2)))
                                                              .addArg(new ConstantNode(new IntegerResultNode(4))),
                                          0.01,
                                          true),
                6, 6.0, "6", longAsRaw(6));
    	DebugWaitFunctionNode n = new DebugWaitFunctionNode(new OrFunctionNode().addArg(new ConstantNode(new IntegerResultNode(2)))
				 																.addArg(new ConstantNode(new IntegerResultNode(4))),
				 											0.3,
				 											false);
    	n.prepare();
    	long start = System.currentTimeMillis();
    	n.execute();
    	long end = System.currentTimeMillis();
    	assertTrue(end - start > 250);
    	
    	DebugWaitFunctionNode n2 = new DebugWaitFunctionNode(new OrFunctionNode().addArg(new ConstantNode(new IntegerResultNode(2)))
																  				 .addArg(new ConstantNode(new IntegerResultNode(4))),
														     0.5,
														     true);
    	n2.prepare();
    	start = System.currentTimeMillis();
    	n2.execute();
    	end = System.currentTimeMillis();
    	assertTrue(end - start > 450);
    }

    @Test
    public void testRawResultNode() {
        try {
            new RawResultNode(null);
            fail("Should not be able to set null value.");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new RawResultNode().setValue(null);
            fail("Should not be able to set null value.");
        } catch (IllegalArgumentException e) {
            // expected
        }
        byte[] b = { '7', '.', '4' };
        RawResultNode a = new RawResultNode(b);
        byte[] raw = a.getRaw();
        assertEquals(raw.length, 3);
        assertEquals(raw[0], '7');
        assertEquals(raw[1], '.');
        assertEquals(raw[2], '4');
        assertEquals(a.getInteger(), 0);
        assertEquals(a.getFloat(), 0.0, delta);
        assertEquals(a.getString(), "7.4");
        assertResultNode(a);
        compare(new RawResultNode(), new RawResultNode(new byte [] {'z'}), new RawResultNode(new byte [] {'z', 'z'}));
        compare(new RawResultNode(new byte [] {'z'}), new RawResultNode(new byte [] {'z', 'z'}), new RawResultNode(new byte [] {'z','z','z'}));
        compare(new RawResultNode(new byte [] {'z'}), new RawResultNode(new byte [] {'z','z'}), new PositiveInfinityResultNode());
        byte [] b1 = {0x00};
        byte [] b2 = {0x07};
        byte [] b3 = {0x7f};
        byte [] b4 = {(byte)0x80};
        byte [] b5 = {(byte)0xb1};
        byte [] b6 = {(byte)0xff};

        assertEquals(0x00, b1[0]);
        assertEquals(0x07, b2[0]);
        assertEquals(0x7f, b3[0]);
        assertEquals(0x80, ((int)b4[0]) & 0xff);
        assertEquals(0xb1, ((int)b5[0]) & 0xff);
        assertEquals(0xff, ((int)b6[0]) & 0xff);

        RawResultNode r1 = new RawResultNode(b1);
        RawResultNode r2 = new RawResultNode(b2);
        RawResultNode r3 = new RawResultNode(b3);
        RawResultNode r4 = new RawResultNode(b4);
        RawResultNode r5 = new RawResultNode(b5);
        RawResultNode r6 = new RawResultNode(b6);

        assertTrue(r1.compareTo(r1) == 0);
        assertTrue(r1.compareTo(r2) < 0);
        assertTrue(r1.compareTo(r3) < 0);
        assertTrue(r1.compareTo(r4) < 0);
        assertTrue(r1.compareTo(r5) < 0);
        assertTrue(r1.compareTo(r6) < 0);

        assertTrue(r2.compareTo(r1) > 0);
        assertTrue(r2.compareTo(r2) == 0);
        assertTrue(r2.compareTo(r3) < 0);
        assertTrue(r2.compareTo(r4) < 0);
        assertTrue(r2.compareTo(r5) < 0);
        assertTrue(r2.compareTo(r6) < 0);

        assertTrue(r3.compareTo(r1) > 0);
        assertTrue(r3.compareTo(r2) > 0);
        assertTrue(r3.compareTo(r3) == 0);
        assertTrue(r3.compareTo(r4) < 0);
        assertTrue(r3.compareTo(r5) < 0);
        assertTrue(r3.compareTo(r6) < 0);

        assertTrue(r4.compareTo(r1) > 0);
        assertTrue(r4.compareTo(r2) > 0);
        assertTrue(r4.compareTo(r3) > 0);
        assertTrue(r4.compareTo(r4) == 0);
        assertTrue(r4.compareTo(r5) < 0);
        assertTrue(r4.compareTo(r6) < 0);

        assertTrue(r5.compareTo(r1) > 0);
        assertTrue(r5.compareTo(r2) > 0);
        assertTrue(r5.compareTo(r3) > 0);
        assertTrue(r5.compareTo(r4) > 0);
        assertTrue(r5.compareTo(r5) == 0);
        assertTrue(r5.compareTo(r6) < 0);

        assertTrue(r6.compareTo(r1) > 0);
        assertTrue(r6.compareTo(r2) > 0);
        assertTrue(r6.compareTo(r3) > 0);
        assertTrue(r6.compareTo(r4) > 0);
        assertTrue(r6.compareTo(r5) > 0);
        assertTrue(r6.compareTo(r6) == 0);

    }

    private void compare(ResultNode small, ResultNode medium, ResultNode large) {
        assertTrue(small.compareTo(medium) < 0);
        assertTrue(small.compareTo(large) < 0);
        assertTrue(medium.compareTo(large) < 0);
        assertTrue(medium.compareTo(small) > 0);
        assertTrue(large.compareTo(small) > 0);
        assertTrue(large.compareTo(medium) > 0);
        assertEquals(0, small.compareTo(small));
        assertEquals(0, medium.compareTo(medium));
        assertEquals(0, large.compareTo(large));
    }

    @Test
    public void testStringResultNode() {
        try {
            new StringResultNode(null);
            fail("Should not be able to set null value.");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new StringResultNode().setValue(null);
            fail("Should not be able to set null value.");
        } catch (IllegalArgumentException e) {
            // expected
        }
        StringResultNode a = new StringResultNode("7.3");
        assertEquals(a.getInteger(), 0);
        assertEquals(a.getFloat(), 7.3, delta);
        assertEquals(a.getString(), "7.3");
        byte[] raw = a.getRaw();
        assertEquals(raw.length, 3);
        assertResultNode(a);
        compare(new StringResultNode(), new StringResultNode("z"), new StringResultNode("zz"));
        compare(new StringResultNode("z"), new StringResultNode("zz"), new StringResultNode("zzz"));
        compare(new StringResultNode("a"), new StringResultNode("zz"), new PositiveInfinityResultNode());
    }

    @Test
    public void testXorBitFunctionNode() {
        try {
            new XorBitFunctionNode(null, 64);
            fail("Should not be able to set null argument.");
        } catch (NullPointerException e) {
            // expected
        }
        try {
            new XorBitFunctionNode().prepare();
            fail("Should not be able to run prepare.");
        } catch (RuntimeException e) {
            // expected
        }
        try {
            new XorBitFunctionNode().execute();
            fail("Should not be able to run execute.");
        } catch (RuntimeException e) {
            // expected
        }
        assertUnaryBitFunctionNode(new XorBitFunctionNode());
    }

    @Test
    public void testUcaFunctionNode() {
        try {
            new UcaFunctionNode(null, "foo");
            fail("Should not be able to set null argument.");
        } catch (NullPointerException e) {
            // expected
        }
        try {
            new UcaFunctionNode().prepare();
            fail("Should not be able to run prepare.");
        } catch (RuntimeException e) {
            // expected
        }
        try {
            new UcaFunctionNode().execute();
            fail("Should not be able to run execute.");
        } catch (RuntimeException e) {
            // expected
        }
        assertUcaFunctionNode(new UcaFunctionNode(new ConstantNode(new IntegerResultNode(1337)), "foo", "bar"));
    }

    @Test
    public void testNestedFunctions() {
        assertFunctionNode(new AddFunctionNode()
                                   .addArg(new MultiplyFunctionNode().addArg(new ConstantNode(new IntegerResultNode(3)))
                                                                     .addArg(new ConstantNode(
                                                                             new StringResultNode("4"))))
                                   .addArg(new ConstantNode(new FloatResultNode(2.0))),
                           14, 14.0, "14.0", doubleAsRaw(14.0));
    }

    @Test
    public void testArithmeticNodes() {
        ExpressionNode i1 = new ConstantNode(new IntegerResultNode(1));
        ExpressionNode i2 = new ConstantNode(new IntegerResultNode(2));
        ExpressionNode f2 = new ConstantNode(new FloatResultNode(9.9));
        ExpressionNode s2 = new ConstantNode(new StringResultNode("2"));
        ExpressionNode r2 = new ConstantNode(new RawResultNode(asRaw(2)));

        AddFunctionNode add1 = new AddFunctionNode();
        add1.addArg(i1).addArg(i2);
        ExpressionNode exp1 = add1;
        exp1.prepare();
        assertTrue(exp1.getResult() instanceof IntegerResultNode);
        assertTrue(exp1.execute());
        assertEquals(exp1.getResult().getInteger(), 3);
        assertTrue(exp1.execute());
        assertEquals(exp1.getResult().getInteger(), 3);

        AddFunctionNode add2 = new AddFunctionNode();
        add2.addArg(i1);
        add2.addArg(f2);
        add2.prepare();
        assertTrue(add2.getResult() instanceof FloatResultNode);

        AddFunctionNode add3 = new AddFunctionNode();
        add3.addArg(i1);
        add3.addArg(s2);
        add3.prepare();
        assertTrue(add3.getResult() instanceof IntegerResultNode);

        AddFunctionNode add4 = new AddFunctionNode();
        add4.addArg(i1);
        add4.addArg(r2);
        add4.prepare();
        assertTrue(add4.getResult() instanceof IntegerResultNode);
    }

    @Test
    public void testArithmeticOperations() {
        ExpressionNode i1 = new ConstantNode(new IntegerResultNode(1793253241));
        ExpressionNode i2 = new ConstantNode(new IntegerResultNode(1676521321));
        ExpressionNode f1 = new ConstantNode(new FloatResultNode(1.1109876));
        ExpressionNode f2 = new ConstantNode(new FloatResultNode(9.767681239));

        assertAdd(i1, i2, 3469774562l, 3469774562l);
        assertAdd(i1, f2, 1793253251l, 1793253250.767681239);
        assertAdd(f1, f2, 11, 10.878668839);
        assertMultiply(i1, i2, 3006427292488851361l, 3006427292488851361l);
        assertMultiply(i1, f2, 17515926039l, 1793253241.0 * 9.767681239);
        assertMultiply(f1, f2, 11, 10.8517727372816364);
    }

    // --------------------------------------------------------------------------------
    //
    // Everything below this point is helper functions.
    //
    // --------------------------------------------------------------------------------
    private static void assertNotEquals(Object lhs, Object rhs) {
        assertFalse(lhs.equals(rhs));
    }

    private static void assertUcaFunctionNode(UcaFunctionNode node) {
        UcaFunctionNode obj = node.clone();
        assertEquals(obj, node);
        assertMultiArgFunctionNode((UcaFunctionNode)Identifiable.createFromId(node.getClassId()));
    }

    public byte[] asRaw(int ... extra) {
        byte[] mybytes = new byte[extra.length];
        for (int i = 0; i < mybytes.length; i++) {
            mybytes[i] = (byte)extra[i];
        }
        return mybytes;
    }

    public byte[] longAsRaw(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    public byte[] doubleAsRaw(double value) {
        return ByteBuffer.allocate(8).putDouble(value).array();
    }

    public byte[] stringAsRaw(String value) {
        return Utf8.toBytes(value);
    }

    private static void assertUnaryBitFunctionNode(UnaryBitFunctionNode node) {
        UnaryBitFunctionNode obj = (UnaryBitFunctionNode)node.clone();
        assertEquals(obj, node);

        obj.setNumBits(obj.getNumBits() + 1);
        assertFalse(obj.equals(node));

        assertMultiArgFunctionNode((UnaryBitFunctionNode)Identifiable.createFromId(node.getClassId()));
    }

    private static void assertMultiArgFunctionNode(MultiArgFunctionNode node) {
        try {
            node.addArg(null);
            fail("Should not be able to add a null argument.");
        } catch (NullPointerException e) {
            // expected
        }
        int initialSz = node.getNumArgs();
        node.addArg(new ConstantNode(new IntegerResultNode(69)));
        assertEquals(1+initialSz, node.getNumArgs());
        node.addArg(new ConstantNode(new IntegerResultNode(6699)));
        assertEquals(2+initialSz, node.getNumArgs());
        node.addArg(new ConstantNode(new IntegerResultNode(666999)));
        assertEquals(3+initialSz, node.getNumArgs());

        MultiArgFunctionNode obj = (MultiArgFunctionNode)assertSerialize(node);
        assertEquals(node, obj);
        assertEquals(node.getNumArgs(), obj.getNumArgs());
        for (int i = 0, len = node.getNumArgs(); i < len; i++) {
            assertEquals(node.getArg(i), obj.getArg(i));
        }

        obj.addArg(new ConstantNode(new IntegerResultNode(69)));
        assertFalse(node.equals(obj));
    }

    public void assertAdd(ExpressionNode arg1, ExpressionNode arg2, long lexpected, double dexpected) {
        assertArith(new AddFunctionNode(), arg1, arg2, lexpected, dexpected);
    }

    public void assertMultiply(ExpressionNode arg1, ExpressionNode arg2, long lexpected, double dexpected) {
        assertArith(new MultiplyFunctionNode(), arg1, arg2, lexpected, dexpected);
    }

    public void assertArith(MultiArgFunctionNode node, ExpressionNode arg1, ExpressionNode arg2, long lexpected, double dexpected) {
        node.addArg(arg1);
        node.addArg(arg2);
        node.prepare();
        node.execute();
        assertEquals(lexpected, node.getResult().getInteger());
        assertEquals(dexpected, node.getResult().getFloat(), delta);
    }

    public void assertFunctionNode(FunctionNode node, long lexpected, double dexpected, String sexpected, byte[] rexpected) {
        node.prepare();
        node.execute();
        assertEquals(lexpected, node.getResult().getInteger());
        assertEquals(dexpected, node.getResult().getFloat(), delta);
        assertEquals(sexpected, node.getResult().getString());
        assertTrue(Arrays.equals(rexpected, node.getResult().getRaw()));
    }

    private static void assertResultNode(ResultNode node) {
        BufferSerializer buf = new BufferSerializer(new GrowableByteBuffer());
        long oldInteger = node.getInteger();
        double oldFloat = node.getFloat();
        String oldString = node.getString();
        byte[] oldRaw = node.getRaw();
        node.serialize(buf);
        buf.flip();
        node.deserialize(buf);
        assertEquals(oldInteger, node.getInteger());
        assertEquals(oldFloat, node.getFloat(), delta);
        assertEquals(oldString, node.getString());
        assertEquals(oldRaw.length, node.getRaw().length);

        buf = new BufferSerializer(new GrowableByteBuffer());
        node.serializeWithId(buf);
        buf.flip();
        node.deserializeWithId(buf);
        assertEquals(oldInteger, node.getInteger());
        assertEquals(oldFloat, node.getFloat(), delta);
        assertEquals(oldString, node.getString());
        assertEquals(oldRaw.length, node.getRaw().length);

        buf = new BufferSerializer(new GrowableByteBuffer());
        node.serializeWithId(buf);
        buf.flip();
        ResultNode obj = (ResultNode)Identifiable.create(buf);
        assertEquals(oldInteger, obj.getInteger());
        assertEquals(oldFloat, obj.getFloat(), delta);
        assertEquals(oldString, obj.getString());
        assertEquals(oldRaw.length, obj.getRaw().length);

        assertSerialize(node);
    }

    private static Identifiable assertSerialize(Identifiable node) {
        BufferSerializer buf = new BufferSerializer(new GrowableByteBuffer());
        node.serializeWithId(buf);
        buf.flip();
        Identifiable created = Identifiable.create(buf);
        assertEquals(node, created);
        assertFalse(buf.getBuf().hasRemaining());
        Identifiable cloned = created.clone();
        assertEquals(node, cloned);
        BufferSerializer createdBuffer = new BufferSerializer(new GrowableByteBuffer());
        BufferSerializer clonedBuffer = new BufferSerializer(new GrowableByteBuffer());
        created.serializeWithId(createdBuffer);
        cloned.serializeWithId(clonedBuffer);
        assertEquals(createdBuffer.getBuf().limit(), clonedBuffer.getBuf().limit());
        assertEquals(createdBuffer.position(), clonedBuffer.position());
        createdBuffer.getBuf().flip();
        clonedBuffer.getBuf().flip();
        for (int i = 0; i < createdBuffer.getBuf().limit(); i++) {
            assertEquals(createdBuffer.getBuf().get(), clonedBuffer.getBuf().get());
        }
        return created;
    }

}
