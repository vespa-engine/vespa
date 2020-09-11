// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.config.ConfigInstance;
import com.yahoo.foo.FunctionTestConfig;
import com.yahoo.config.InnerNode;
import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.vespa.config.ConfigPayload;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.yahoo.test.json.JsonTestHelper.assertJsonEquals;
import static com.yahoo.test.json.JsonTestHelper.inputJson;
import static org.junit.Assert.assertEquals;

/**
 * @author hmusum
 * @author Vegard Sjonfjell
 */
public class CfgConfigPayloadBuilderTest {

    @Test
    public void createConfigPayload() {
        final FunctionTestConfig config = ConfigInstancePayloadTest.createVariableAccessConfigWithBuilder();

        final String expectedJson = inputJson(
                "{",
                "    'double_val': '41.23',",
                "    'refarr': [",
                "        ':parent:',",
                "        ':parent',",
                "        'parent:'",
                "    ],",
                "    'pathVal': 'src/test/resources/configs/def-files/function-test.def',",
                "    'string_val': 'foo',",
                "    'myStructMap': {",
                "        'one': {",
                "            'myString': 'bull',",
                "            'anotherMap': {",
                "                'anotherOne': {",
                "                    'anInt': '3',",
                "                    'anIntDef': '4'",
                "                }",
                "            },",
                "            'myInt': '1',",
                "            'myStringDef': 'bear',",
                "            'myIntDef': '2'",
                "        }",
                "    },",
                "    'boolarr': [",
                "        'false'",
                "    ],",
                "    'intMap': {",
                "        'dotted.key': '3',",
                "        'spaced key': '4',",
                "        'two': '2',",
                "        'one': '1'",
                "    },",
                "    'int_val': '5',",
                "    'stringarr': [",
                "        'bar'",
                "    ],",
                "    'enum_val': 'FOOBAR',",
                "    'myarray': [",
                "        {",
                "            'anotherarray': [",
                "                {",
                "                    'foo': '7'",
                "                }",
                "            ],",
                "            'intval': '-5',",
                "            'fileVal': 'file0',",
                "            'refval': ':parent:',",
                "            'myStruct': {",
                "                'a': '1',",
                "                'b': '2'",
                "            },",
                "            'stringval': [",
                "                'baah',",
                "                'yikes'",
                "            ],",
                "            'enumval': 'INNER'",
                "        },",
                "        {",
                "            'anotherarray': [",
                "                {",
                "                    'foo': '2'",
                "                }",
                "            ],",
                "            'intval': '5',",
                "            'fileVal': 'file1',",
                "            'refval': ':parent:',",
                "            'myStruct': {",
                "                'a': '-1',",
                "                'b': '-2'",
                "            },",
                "            'enumval': 'INNER'",
                "        }",
                "    ],",
                "    'fileArr': [",
                "        'bin'",
                "    ],",
                "    'enumwithdef': 'BAR2',",
                "    'bool_with_def': 'true',",
                "    'enumarr': [",
                "        'VALUES'",
                "    ],",
                "    'pathMap': {",
                "        'one': 'pom.xml'",
                "    },",
                "    'long_with_def': '-9876543210',",
                "    'double_with_def': '-12.0',",
                "    'stringMap': {",
                "        'double spaced key': 'third',",
                "        'double.dotted.key': 'second',",
                "        'one': 'first'",
                "    },",
                "    'refwithdef': ':parent:',",
                "    'stringwithdef': 'bar and foo',",
                "    'doublearr': [",
                "        '2344.0',",
                "        '123.0'",
                "    ],",
                "    'int_with_def': '-14',",
                "    'pathArr': [",
                "        'pom.xml'",
                "    ],",
                "    'rootStruct': {",
                "        'innerArr': [",
                "            {",
                "                'stringVal': 'deep',",
                "                'boolVal': 'true'",
                "            },",
                "            {",
                "                'stringVal': 'blue a=\\'escaped\\'',",
                "                'boolVal': 'false'",
                "            }",
                "        ],",
                "        'inner0': {",
                "            'index': '11',",
                "            'name': 'inner0'",
                "        },",
                "        'inner1': {",
                "            'index': '12',",
                "            'name': 'inner1'",
                "        }",
                "    },",
                "    'fileVal': 'etc',",
                "    'refval': ':parent:',",
                "    'onechoice': 'ONLYFOO',",
                "    'bool_val': 'false',",
                "    'longarr': [",
                "        '9223372036854775807',",
                "        '-9223372036854775808'",
                "    ],",
                "    'basicStruct': {",
                "        'intArr': [",
                "            '310',",
                "            '311'",
                "        ],",
                "        'foo': 'basicFoo',",
                "        'bar': '3'",
                "    },",
                "    'long_val': '12345678901'",
                "}"
        );

        CfgConfigPayloadBuilderTest.assertDeserializedConfigEqualsJson(config, expectedJson);
    }

    // FIXME: We need to define the behavior here.
    @Test
    public void test_empty_struct_arrays() {
        assertDeserializedConfigEqualsJson("myArray[1]", inputJson("{}"));
    }

    @Test
    public void test_escaped_string() {
        assertDeserializedConfigEqualsJson("a b=\"escaped\"",
                inputJson(
                        "{",
                        " 'a': 'b=\\'escaped\\''",
                        "}"
                )
        );
    }

    @Test
    public void test_empty_payload() {
        assertDeserializedConfigEqualsJson("", inputJson("{}"));
    }

    @Test
    public void test_leading_whitespace() {
        assertDeserializedConfigEqualsJson(" a 0",
                inputJson(
                        "{",
                        "    'a': '0'",
                        "}")
        );
    }

    @Test
    public void test_leading_and_trailing_whitespace_string() {
        assertDeserializedConfigEqualsJson(
                "a \" foo \"",
                inputJson(
                        "{",
                        "    'a': ' foo '",
                        "}"));
    }

    @Test
    public void test_config_with_comments() {
        CfgConfigPayloadBuilderTest.assertDeserializedConfigEqualsJson(
                Arrays.asList(
                        "fielda b\n",
                        "#fielda c\n",
                        "#fieldb c\n",
                        "# just a comment"),
                inputJson(
                        "{",
                        "    'fielda': 'b'",
                        "}")
        );
    }

    @Test
    public void testConvertingMaps() {
        List<String> payload = Arrays.asList(
                "intmap{\"foo\"} 1337",
                "complexmap{\"key\"}.foo 1337",
                "nestedmap{\"key1\"}.foo{\"key2\"}.bar 1337"
        );

        final String expectedJson = inputJson(
                "{",
                "    'nestedmap': {",
                "        'key1': {",
                "            'foo': {",
                "                'key2': {",
                "                    'bar': '1337'",
                "                }",
                "            }",
                "        }",
                "    },",
                "    'intmap': {",
                "        'foo': '1337'",
                "    },",
                "    'complexmap': {",
                "        'key': {",
                "            'foo': '1337'",
                "        }",
                "    }",
                "}"
        );

        CfgConfigPayloadBuilderTest.assertDeserializedConfigEqualsJson(payload, expectedJson);
    }

    @Test
    public void createConfigPayloadUtf8() {
        SimpletypesConfig.Builder builder = new SimpletypesConfig.Builder().stringval("Hei \u00E6\u00F8\u00E5 \uBC14\uB451 \u00C6\u00D8\u00C5 hallo");
        SimpletypesConfig config = new SimpletypesConfig(builder);

        String expectedJson = inputJson(
                "{",
                "    'longval': '0',",
                "    'intval': '0',",
                "    'stringval': 'Hei \u00e6\u00f8\u00e5 \ubc14\ub451 \u00c6\u00d8\u00c5 hallo',",
                "    'boolval': 'false',",
                "    'doubleval': '0.0',",
                "    'enumval': 'VAL1'",
                "}"
        );

        CfgConfigPayloadBuilderTest.assertDeserializedConfigEqualsJson(config, expectedJson);
    }

    @Test
    public void testLineParsing() {
        CfgConfigPayloadBuilder builder = new CfgConfigPayloadBuilder();
        assertEquals(builder.parseFieldAndValue("foo bar").getFirst(), "foo");
        assertEquals(builder.parseFieldAndValue("foo bar").getSecond(), "bar");
        assertEquals(builder.parseFieldAndValue("foo.bar.baz{my key} baR").getFirst(), "foo.bar.baz{my key}");
        assertEquals(builder.parseFieldAndValue("foo.bar.baz{my key} baR").getSecond(), "baR");
        assertEquals(builder.parseFieldAndValue("foo.bar.baz{my.key} baRR").getFirst(), "foo.bar.baz{my.key}");
        assertEquals(builder.parseFieldAndValue("foo.bar.baz{my.key} baRR").getSecond(), "baRR");
        assertEquals(builder.parseFieldAndValue("foo.bar.baz{my key.dotted}.biz baRO").getFirst(), "foo.bar.baz{my key.dotted}.biz");
        assertEquals(builder.parseFieldAndValue("foo.bar.baz{my key.dotted}.biz baRO").getSecond(), "baRO");

        assertEquals(builder.parseFieldList("foo.bar.baz").get(0), "foo");
        assertEquals(builder.parseFieldList("foo.bar.baz").get(1), "bar");
        assertEquals(builder.parseFieldList("foo.bar.baz").get(2), "baz");
        assertEquals(builder.parseFieldList("foo.bar{f.b}.baz{f.h h}").get(0), "foo");
        assertEquals(builder.parseFieldList("foo.bar{f.b}.baz{f.h h}").get(1), "bar{f.b}");
        assertEquals(builder.parseFieldList("foo.bar{f.b}.baz{f.h h}").get(2), "baz{f.h h}");
    }

    private static void assertDeserializedConfigEqualsJson(InnerNode config, String expectedJson) {
        assertDeserializedConfigEqualsJson(ConfigInstance.serialize(config), expectedJson);
    }

    private static void assertDeserializedConfigEqualsJson(String serializedConfig, String expectedJson) {
        assertDeserializedConfigEqualsJson(List.of(serializedConfig), expectedJson);
    }

    private static void assertDeserializedConfigEqualsJson(List<String> inputConfig, String expectedJson) {
        ConfigPayload payload = new CfgConfigPayloadBuilder().deserialize(inputConfig);
        assertJsonEquals(payload.toString(), expectedJson);
    }
}
