// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.document.DocumentId;
import com.yahoo.document.GlobalId;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.searchlib.aggregation.hll.SparseSketch;
import com.yahoo.searchlib.expression.AddFunctionNode;
import com.yahoo.searchlib.expression.AttributeNode;
import com.yahoo.searchlib.expression.CatFunctionNode;
import com.yahoo.searchlib.expression.ConstantNode;
import com.yahoo.searchlib.expression.DebugWaitFunctionNode;
import com.yahoo.searchlib.expression.DivideFunctionNode;
import com.yahoo.searchlib.expression.DocumentFieldNode;
import com.yahoo.searchlib.expression.ExpressionNode;
import com.yahoo.searchlib.expression.FixedWidthBucketFunctionNode;
import com.yahoo.searchlib.expression.FloatBucketResultNode;
import com.yahoo.searchlib.expression.FloatBucketResultNodeVector;
import com.yahoo.searchlib.expression.FloatResultNode;
import com.yahoo.searchlib.expression.GetDocIdNamespaceSpecificFunctionNode;
import com.yahoo.searchlib.expression.IntegerBucketResultNode;
import com.yahoo.searchlib.expression.IntegerBucketResultNodeVector;
import com.yahoo.searchlib.expression.IntegerResultNode;
import com.yahoo.searchlib.expression.MD5BitFunctionNode;
import com.yahoo.searchlib.expression.MaxFunctionNode;
import com.yahoo.searchlib.expression.MinFunctionNode;
import com.yahoo.searchlib.expression.ModuloFunctionNode;
import com.yahoo.searchlib.expression.MultiplyFunctionNode;
import com.yahoo.searchlib.expression.NegateFunctionNode;
import com.yahoo.searchlib.expression.NormalizeSubjectFunctionNode;
import com.yahoo.searchlib.expression.RangeBucketPreDefFunctionNode;
import com.yahoo.searchlib.expression.RawBucketResultNode;
import com.yahoo.searchlib.expression.RawBucketResultNodeVector;
import com.yahoo.searchlib.expression.RawResultNode;
import com.yahoo.searchlib.expression.ReverseFunctionNode;
import com.yahoo.searchlib.expression.SortFunctionNode;
import com.yahoo.searchlib.expression.StringBucketResultNode;
import com.yahoo.searchlib.expression.StringBucketResultNodeVector;
import com.yahoo.searchlib.expression.StringResultNode;
import com.yahoo.searchlib.expression.TimeStampFunctionNode;
import com.yahoo.searchlib.expression.XorBitFunctionNode;
import com.yahoo.searchlib.expression.XorFunctionNode;
import com.yahoo.searchlib.expression.ZCurveFunctionNode;
import com.yahoo.vespa.objects.BufferSerializer;
import com.yahoo.vespa.objects.Identifiable;
import com.yahoo.vespa.objects.ObjectDumper;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.fail;

/**
 * Tests serialization compatibility across Java and C++. The comparison is performed by comparing serialized Java
 * object graphs with the content of specific binary files. C++ unit tests serializes
 * identical data structures into these files.
 * Note: This test relies heavily on proper implementation of {@link Object#equals(Object)}!
 */
public class GroupingSerializationTest {

    @BeforeClass
    public static void forceLoadingOfSerializableClasses() {
        com.yahoo.searchlib.aggregation.ForceLoad.forceLoad();
        com.yahoo.searchlib.expression.ForceLoad.forceLoad();
    }

    @Test
    public void testResultTypes() throws IOException {
        try (SerializationTester t = new SerializationTester("testResultTypes")) {
            t.assertMatch(new IntegerResultNode(7));
            t.assertMatch(new FloatResultNode(7.3));
            t.assertMatch(new StringResultNode("7.3"));
            t.assertMatch(new StringResultNode(
                    new String(new byte[]{(byte)0xe5, (byte)0xa6, (byte)0x82, (byte)0xe6, (byte)0x9e, (byte)0x9c},
                               StandardCharsets.UTF_8)));
            t.assertMatch(new RawResultNode(new byte[]{'7', '.', '4'}));
            t.assertMatch(new IntegerBucketResultNode());
            t.assertMatch(new FloatBucketResultNode());
            t.assertMatch(new IntegerBucketResultNode(10, 20));
            t.assertMatch(new FloatBucketResultNode(10, 20));
            t.assertMatch(new StringBucketResultNode("10.0", "20.0"));
            t.assertMatch(new RawBucketResultNode(
                    new RawResultNode(new byte[]{1, 0, 0}),
                    new RawResultNode(new byte[]{1, 1, 0})));
            t.assertMatch(new IntegerBucketResultNodeVector()
                    .add(new IntegerBucketResultNode(878, 3246823)));
            t.assertMatch(new FloatBucketResultNodeVector()
                    .add(new FloatBucketResultNode(878, 3246823)));
            t.assertMatch(new StringBucketResultNodeVector()
                    .add(new StringBucketResultNode("878", "3246823")));
            t.assertMatch(new RawBucketResultNodeVector()
                    .add(new RawBucketResultNode(
                            new RawResultNode(new byte[]{1, 0, 0}),
                            new RawResultNode(new byte[]{1, 1, 0}))));
        }

    }

    @Test
    public void testSpecialNodes() throws IOException {
        try (SerializationTester t = new SerializationTester("testSpecialNodes")) {
            t.assertMatch(new AttributeNode("testattribute"));
            t.assertMatch(new DocumentFieldNode("testdocumentfield"));
            t.assertMatch(new GetDocIdNamespaceSpecificFunctionNode(new IntegerResultNode(7)));
        }
    }

    @Test
    public void testFunctionNodes() throws IOException {
        try (SerializationTester t = new SerializationTester("testFunctionNodes")) {
            t.assertMatch(new AddFunctionNode()
                    .addArg(new ConstantNode(new IntegerResultNode(7)))
                    .addArg(new ConstantNode(new IntegerResultNode(8)))
                    .addArg(new ConstantNode(new IntegerResultNode(9))));
            t.assertMatch(new XorFunctionNode()
                    .addArg(new ConstantNode(new IntegerResultNode(7)))
                    .addArg(new ConstantNode(new IntegerResultNode(8)))
                    .addArg(new ConstantNode(new IntegerResultNode(9))));
            t.assertMatch(new MultiplyFunctionNode()
                    .addArg(new ConstantNode(new IntegerResultNode(7)))
                    .addArg(new ConstantNode(new IntegerResultNode(8)))
                    .addArg(new ConstantNode(new IntegerResultNode(9))));
            t.assertMatch(new DivideFunctionNode()
                    .addArg(new ConstantNode(new IntegerResultNode(7)))
                    .addArg(new ConstantNode(new IntegerResultNode(8)))
                    .addArg(new ConstantNode(new IntegerResultNode(9))));
            t.assertMatch(new ModuloFunctionNode()
                    .addArg(new ConstantNode(new IntegerResultNode(7)))
                    .addArg(new ConstantNode(new IntegerResultNode(8)))
                    .addArg(new ConstantNode(new IntegerResultNode(9))));
            t.assertMatch(new MinFunctionNode()
                    .addArg(new ConstantNode(new IntegerResultNode(7)))
                    .addArg(new ConstantNode(new IntegerResultNode(8)))
                    .addArg(new ConstantNode(new IntegerResultNode(9))));
            t.assertMatch(new MaxFunctionNode()
                    .addArg(new ConstantNode(new IntegerResultNode(7)))
                    .addArg(new ConstantNode(new IntegerResultNode(8)))
                    .addArg(new ConstantNode(new IntegerResultNode(9))));
            t.assertMatch(new TimeStampFunctionNode(new ConstantNode(new IntegerResultNode(7)),
                    TimeStampFunctionNode.TimePart.Hour, true));
            t.assertMatch(new ZCurveFunctionNode(new ConstantNode(new IntegerResultNode(7)),
                    ZCurveFunctionNode.Dimension.X));
            t.assertMatch(new ZCurveFunctionNode(new ConstantNode(new IntegerResultNode(7)),
                    ZCurveFunctionNode.Dimension.Y));
            t.assertMatch(new NegateFunctionNode(new ConstantNode(new IntegerResultNode(7))));
            t.assertMatch(new SortFunctionNode(new ConstantNode(new IntegerResultNode(7))));
            t.assertMatch(new NormalizeSubjectFunctionNode(new ConstantNode(
                    new StringResultNode("foo"))));
            t.assertMatch(new ReverseFunctionNode(new ConstantNode(new IntegerResultNode(7))));
            t.assertMatch(new MD5BitFunctionNode(new ConstantNode(new IntegerResultNode(7)), 64));
            t.assertMatch(new XorBitFunctionNode(new ConstantNode(new IntegerResultNode(7)), 64));
            t.assertMatch(new CatFunctionNode()
                    .addArg(new ConstantNode(new IntegerResultNode(7)))
                    .addArg(new ConstantNode(new IntegerResultNode(8)))
                    .addArg(new ConstantNode(new IntegerResultNode(9))));
            t.assertMatch(new FixedWidthBucketFunctionNode());
            t.assertMatch(new FixedWidthBucketFunctionNode().addArg(new AttributeNode("foo")));
            t.assertMatch(new FixedWidthBucketFunctionNode(new IntegerResultNode(10), new AttributeNode("foo")));
            t.assertMatch(new FixedWidthBucketFunctionNode(new FloatResultNode(10.0), new AttributeNode("foo")));
            t.assertMatch(new RangeBucketPreDefFunctionNode());
            t.assertMatch(new RangeBucketPreDefFunctionNode().addArg(new AttributeNode("foo")));
            t.assertMatch(new DebugWaitFunctionNode(new ConstantNode(new IntegerResultNode(5)),
                    3.3, false));
        }

    }

    @Test
    public void testAggregatorResults() throws IOException {
        try (SerializationTester t = new SerializationTester("testAggregatorResults")) {
            t.assertMatch(new SumAggregationResult(new IntegerResultNode(7))
                    .setExpression(new AttributeNode("attributeA")));
            t.assertMatch(new XorAggregationResult()
                    .setXor(7)
                    .setExpression(new AttributeNode("attributeA")));
            t.assertMatch(new CountAggregationResult()
                    .setCount(7)
                    .setExpression(new AttributeNode("attributeA")));
            t.assertMatch(new MinAggregationResult(new IntegerResultNode(7))
                    .setExpression(new AttributeNode("attributeA")));
            t.assertMatch(new MaxAggregationResult(new IntegerResultNode(7))
                    .setExpression(new AttributeNode("attributeA")));
            t.assertMatch(new AverageAggregationResult(new IntegerResultNode(7), 0)
                    .setExpression(new AttributeNode("attributeA")));
            SparseSketch sketch = new SparseSketch();
            sketch.aggregate(1955583074);
            t.assertMatch(new ExpressionCountAggregationResult(sketch, s -> 42)
                    .setExpression(new ConstantNode(new IntegerResultNode(67))));
            t.assertMatch(new StandardDeviationAggregationResult(1, 67, 67 * 67)
                    .setExpression(new ConstantNode(new IntegerResultNode(67))));
        }
    }

    @Test
    public void testHitCollection() throws IOException {
        try (SerializationTester t = new SerializationTester("testHitCollection")) {
            t.assertMatch(new FS4Hit(0, new GlobalId(new byte[GlobalId.LENGTH]), 0, -1));
            t.assertMatch(new FS4Hit(0, createGlobalId(100), 50.0, -1));
            t.assertMatch(new VdsHit());
            //TODO Verify the two structures below
            t.assertMatch(new VdsHit("100", new byte[0], 50.0));
            t.assertMatch(new VdsHit("100", "rawsummary".getBytes(), 50.0));
            t.assertMatch(new HitsAggregationResult());
            t.assertMatch(new HitsAggregationResult()
                    .setMaxHits(5)
                    .addHit(new FS4Hit(0, createGlobalId(10), 1.0, -1))
                    .addHit(new FS4Hit(0, createGlobalId(20), 2.0, -1))
                    .addHit(new FS4Hit(0, createGlobalId(30), 3.0, -1))
                    .addHit(new FS4Hit(0, createGlobalId(40), 4.0, -1))
                    .addHit(new FS4Hit(0, createGlobalId(50), 5.0, -1))
                    .setExpression(new ConstantNode(new IntegerResultNode(5))));
            t.assertMatch(new HitsAggregationResult()
                    .setMaxHits(3)
                    .addHit(new FS4Hit(0, createGlobalId(10), 1.0, 100))
                    .addHit(new FS4Hit(0, createGlobalId(20), 2.0, 200))
                    .addHit(new FS4Hit(0, createGlobalId(30), 3.0, 300))
                    .setExpression(new ConstantNode(new IntegerResultNode(5))));
            //TODO Verify content
            t.assertMatch(new HitsAggregationResult()
                    .setMaxHits(3)
                    .addHit(new VdsHit("10", "100".getBytes(), 1.0))
                    .addHit(new VdsHit("20", "200".getBytes(), 2.0))
                    .addHit(new VdsHit("30", "300".getBytes(), 3.0))
                    .setExpression(new ConstantNode(new IntegerResultNode(5))));
        }
    }

    @Test
    public void testGroupingLevel() throws IOException {
        try (SerializationTester t = new SerializationTester("testGroupingLevel")) {
            GroupingLevel groupingLevel = new GroupingLevel();
            groupingLevel.setMaxGroups(100)
                    .setExpression(createDummyExpression())
                    .getGroupPrototype()
                    .addAggregationResult(
                            new SumAggregationResult()
                                    .setExpression(createDummyExpression()));
            t.assertMatch(groupingLevel);
        }
    }

    @Test
    public void testGroup() throws IOException {
        try (SerializationTester t = new SerializationTester("testGroup")) {
            t.assertMatch(new Group());
            t.assertMatch(new Group().setId(new IntegerResultNode(50))
                    .setRank(10));
            t.assertMatch(new Group().setId(new IntegerResultNode(100))
                    .addChild(new Group().setId(new IntegerResultNode(110)))
                    .addChild(new Group().setId(new IntegerResultNode(120))
                            .setRank(20.5)
                            .addAggregationResult(new SumAggregationResult()
                                    .setExpression(createDummyExpression()))
                            .addAggregationResult(new SumAggregationResult()
                                    .setExpression(createDummyExpression())))
                    .addChild(new Group().setId(new IntegerResultNode(130))
                            .addChild(new Group().setId(new IntegerResultNode(131)))));
        }
    }

    @Test
    public void testGrouping() throws IOException {
        try (SerializationTester t = new SerializationTester("testGrouping")) {
            t.assertMatch(new Grouping());

            GroupingLevel level1 = new GroupingLevel();
            level1.setMaxGroups(100)
                  .setExpression(createDummyExpression())
                  .getGroupPrototype()
                      .addAggregationResult(
                              new SumAggregationResult()
                                      .setExpression(createDummyExpression()));
            GroupingLevel level2 = new GroupingLevel();
            level2.setMaxGroups(10)
                    .setExpression(createDummyExpression())
                    .getGroupPrototype()
                        .addAggregationResult(
                                new SumAggregationResult()
                                        .setExpression(createDummyExpression()))
                        .addAggregationResult(
                                new SumAggregationResult()
                                        .setExpression(createDummyExpression()));
            t.assertMatch(new Grouping()
                    .addLevel(level1)
                    .addLevel(level2));

            GroupingLevel level3 = new GroupingLevel();
            level3.setExpression(new AttributeNode("folder"))
                    .getGroupPrototype()
                    .addAggregationResult(
                            new XorAggregationResult()
                                    .setExpression(new MD5BitFunctionNode(new AttributeNode("docid"), 64)))
                    .addAggregationResult(
                            new SumAggregationResult()
                                    .setExpression(new MinFunctionNode()
                                            .addArg(new AttributeNode("attribute1"))
                                            .addArg(new AttributeNode("attribute2"))))
                    .addAggregationResult(
                            new XorAggregationResult()
                                    .setExpression(
                                            new XorBitFunctionNode(new CatFunctionNode()
                                                    .addArg(new GetDocIdNamespaceSpecificFunctionNode(new StringResultNode("")))
                                                    .addArg(new DocumentFieldNode("folder"))
                                                    .addArg(new DocumentFieldNode("flags")), 64)));
            t.assertMatch(new Grouping()
                    .addLevel(level3));
        }
    }


    private static GlobalId createGlobalId(int docId) {
        return new GlobalId(
                new DocumentId(String.format("id:test:type::%d", docId)).getGlobalId());
    }

    private static ExpressionNode createDummyExpression() {
        return new AddFunctionNode()
                .addArg(new ConstantNode(new IntegerResultNode(2)))
                .addArg(new ConstantNode(new IntegerResultNode(2)));
    }

    private static class SerializationTester implements AutoCloseable {

        private static final String FILE_PATH = "src/test/files";

        private final DataInputStream in;
        private final String fileName;

        public SerializationTester(String fileName) throws IOException {
            this.fileName = fileName;
            this.in = new DataInputStream(
                    new BufferedInputStream(
                            new FileInputStream(
                                    new File(FILE_PATH, fileName))));
        }

        public SerializationTester assertMatch(Identifiable expectedObject) throws IOException {
            int length = readLittleEndianInt(in);
            byte[] originalData = new byte[length];
            in.readFully(originalData);
            Identifiable deserializedObject = Identifiable.create(new BufferSerializer(originalData));

            if (!deserializedObject.equals(expectedObject)) {
                fail(String.format("Serialized object in file '%s' does not equal expected values.\n" +
                                "==================================================\n" +
                                "Expected:\n" +
                                "==================================================\n" +
                                "%s\n" +
                                "==================================================\n" +
                                "Actual:\n" +
                                "==================================================\n" +
                                "%s\n" +
                                "==================================================\n",
                        fileName, dumpObject(expectedObject), dumpObject(deserializedObject)));
            }
            GrowableByteBuffer buffer = new GrowableByteBuffer(1024 * 8);
            BufferSerializer serializer = new BufferSerializer(buffer);
            deserializedObject.serializeWithId(serializer);
            buffer.flip();

            byte[] newData = new byte[buffer.limit()];
            buffer.get(newData);
            if (!Arrays.equals(newData, originalData)) {
                fail(String.format("Serialized object data does not match the original serialized data from file.\n" +
                                "==================================================\n" +
                                "Original:\n" +
                                "==================================================\n" +
                                "%s\n" +
                                "==================================================\n" +
                                "Serialized:\n" +
                                "==================================================\n" +
                                "%s\n" +
                                "==================================================\n",
                        toHexString(originalData), toHexString(newData)));
            }
            return this;
        }

        private static int readLittleEndianInt(DataInputStream in) throws IOException {
            byte[] data = new byte[4];
            in.readFully(data);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            return buffer.getInt();
        }

        private static String dumpObject(Identifiable obj) {
            ObjectDumper dumper = new ObjectDumper();
            obj.visitMembers(dumper);
            return dumper.toString();
        }

        @Override
        public void close() throws IOException {
            int bytesLeft = 0;
            while (in.read() != -1)
                bytesLeft++;
            in.close();
            if (bytesLeft > 0)
                fail(FILE_PATH + "/" + fileName + " has " + bytesLeft + " bytes left. " +
                     "Did you forget to deserialize an object on Java side?");
        }

        private static String toHexString(byte[] data) {
            char[] table = "0123456789ABCDEF".toCharArray();
            StringBuilder builder = new StringBuilder();
            builder.append("(").append(data.length).append(" bytes)");
            for (int i = 0; i < data.length; i++) {
                if (i % 16 == 0) {
                    builder.append("\n");
                }
                builder.append(table[(data[i] >> 4) & 0xf]);
                builder.append(table[data[i] & 0xf]);
                builder.append(" ");
            }
            return builder.toString();
        }


    }

}
