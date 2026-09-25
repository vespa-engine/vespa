// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.tensor.TensorType;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class SchemaInfoTest {

    @Test
    void testSchemaInfoConfiguration() {
        assertEquals(SchemaInfoTester.createSchemaInfoFromConfig(), SchemaInfoTester.createSchemaInfo());
    }

    @Test
    void testFastMapSearchConfiguration() {
        var schemaConfig = new SchemaInfoConfig.Schema.Builder().name("a");
        schemaConfig.field(new SchemaInfoConfig.Schema.Field.Builder().name("plain").type("map<string,string>")
                                                                      .index(false).attribute(false).bitPacked(false));
        schemaConfig.field(new SchemaInfoConfig.Schema.Field.Builder().name("fastmap").type("map<string,int>")
                                                                      .index(false).attribute(false).bitPacked(false)
                                                                      .fastMapSearchFields(new SchemaInfoConfig.Schema.Field.FastMapSearchFields.Builder()
                                                                                                   .keyType("string").valueType("int")));
        schemaConfig.field(new SchemaInfoConfig.Schema.Field.Builder().name("fastarray").type("array<entry>")
                                                                      .index(false).attribute(false).bitPacked(false)
                                                                      .fastMapSearchFields(new SchemaInfoConfig.Schema.Field.FastMapSearchFields.Builder()
                                                                                                   .keyField("mykey").keyType("string")
                                                                                                   .valueField("myvalue").valueType("long")));
        var schema = SchemaInfoConfigurer.toSchemas(new SchemaInfoConfig.Builder().schema(schemaConfig).build()).get(0);

        assertFalse(schema.fields().get("plain").hasFastMapSearch());
        assertTrue(schema.fields().get("plain").fastMapSearch().isEmpty());

        var fastMap = schema.fields().get("fastmap").fastMapSearch().orElseThrow();
        assertEquals("key", fastMap.keyField());
        assertEquals(Field.Type.Kind.STRING, fastMap.keyType().kind());
        assertEquals("value", fastMap.valueField());
        assertEquals(Field.Type.Kind.INT, fastMap.valueType().kind());

        assertTrue(schema.fields().get("fastarray").hasFastMapSearch());
        var fastArray = schema.fields().get("fastarray").fastMapSearch().orElseThrow();
        assertEquals("mykey", fastArray.keyField());
        assertEquals(Field.Type.Kind.STRING, fastArray.keyType().kind());
        assertEquals("myvalue", fastArray.valueField());
        assertEquals(Field.Type.Kind.LONG, fastArray.valueType().kind());
        assertEquals(new Field.FastMapSearchFields("mykey", Field.Type.from("string"), "myvalue", Field.Type.from("long")),
                     fastArray);
        assertEquals(Field.FastMapSearchFields.of((Field.MapFieldType)Field.Type.from("map<string,int>")), fastMap);
    }

    @Test
    void testFieldTypeEquality() {
        assertEquals(Field.Type.from("string"), Field.Type.from("string"));
        assertEquals(Field.Type.from("string").hashCode(), Field.Type.from("string").hashCode());
        assertFalse(Field.Type.from("string").equals(Field.Type.from("int")));
        assertEquals(Field.Type.from("map<string, int>"), Field.Type.from("map<string,int>"));
        assertFalse(Field.Type.from("map<string,int>").equals(Field.Type.from("map<string,long>")));
        assertFalse(Field.Type.from("map<string,int>").equals(Field.Type.from("array<int>")));
        assertEquals(Field.Type.from("tensor(x[3])"), Field.Type.from("tensor(x[3])"));
        assertFalse(Field.Type.from("tensor(x[3])").equals(Field.Type.from("tensor(x[4])")));
    }

    @Test
    void testInputResolution() {
        var tester = new SchemaInfoTester();
        tester.assertInput(TensorType.fromSpec("tensor(a{},b{})"),
                "", "", "commonProfile", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(a{},b{})"),
                "ab", "", "commonProfile", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(a{},b{})"),
                "a", "", "commonProfile", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(a{},b{})"),
                "b", "", "commonProfile", "query(myTensor1)");

        tester.assertInputConflict(TensorType.fromSpec("tensor(a{},b{})"),
                "", "", "inconsistent", "query(myTensor1)");
        tester.assertInputConflict(TensorType.fromSpec("tensor(a{},b{})"),
                "ab", "", "inconsistent", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(a{},b{})"),
                "ab", "a", "inconsistent", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(x[10])"),
                "ab", "b", "inconsistent", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(a{},b{})"),
                "a", "", "inconsistent", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(x[10])"),
                "b", "", "inconsistent", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(a{},b{})"),
                "ab", "", "bOnly", "query(myTensor1)");
        try {
            tester.assertInput(null,
                               "a", "", "bOnly", "query(myTensor1)");
            fail("Expected exception since bOnly is not in a");
        }
        catch (IllegalArgumentException e) {
            assertEquals("No profile named 'bOnly' exists in schemas [a]",
                         Exceptions.toMessageString(e));
        }
    }

}
