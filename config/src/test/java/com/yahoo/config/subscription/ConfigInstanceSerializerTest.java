// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.config.ConfigInstance;
import com.yahoo.foo.ArraytypesConfig;
import com.yahoo.foo.MaptypesConfig;
import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.foo.SpecialtypesConfig;
import com.yahoo.foo.StructtypesConfig;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static com.yahoo.test.json.JsonTestHelper.assertJsonEquals;
import static com.yahoo.test.json.JsonTestHelper.inputJson;
import static org.junit.Assert.fail;

/**
 * @author Ulf Lilleengen
 * @author Vegard Sjonfjell
 */
public class ConfigInstanceSerializerTest {

    @Test
    public void test_that_leaf_types_are_serialized_to_json_types() {
        SimpletypesConfig.Builder builder = new SimpletypesConfig.Builder();
        builder.boolval(false);
        builder.stringval("foo");
        builder.intval(13);
        builder.longval(14);
        builder.doubleval(3.14);
        builder.enumval(SimpletypesConfig.Enumval.Enum.VAL2);

        final SimpletypesConfig config = new SimpletypesConfig(builder);
        final String expectedJson = inputJson(
                "{",
                "    'boolval': false,",
                "    'doubleval': 3.14,",
                "    'enumval': 'VAL2',",
                "    'intval': 13,",
                "    'longval': 14,",
                "    'stringval': 'foo'",
                "}"
        );

        assertConfigEquals(expectedJson, config);
    }

    @Test
    public void test_that_nested_structs_are_formatted_to_json() {
        StructtypesConfig.Builder builder = new StructtypesConfig.Builder();
        StructtypesConfig.Nested.Builder nestedBuilder = new StructtypesConfig.Nested.Builder();
        StructtypesConfig.Nested.Inner.Builder innerBuilder = new StructtypesConfig.Nested.Inner.Builder();
        innerBuilder.name("foo");
        innerBuilder.emails("Ulf Lilleengen@foo");
        innerBuilder.emails("Ulf Lilleengen@bar");
        innerBuilder.gender(StructtypesConfig.Nested.Inner.Gender.Enum.MALE);
        nestedBuilder.inner(innerBuilder);
        builder.nested(nestedBuilder);
        StructtypesConfig.Nestedarr.Builder nestedArrBuilder = new StructtypesConfig.Nestedarr.Builder();
        StructtypesConfig.Nestedarr.Inner.Builder innerNestedArrBuilder = new StructtypesConfig.Nestedarr.Inner.Builder();
        innerNestedArrBuilder.emails("foo@bar");
        innerNestedArrBuilder.name("bar");
        innerNestedArrBuilder.gender(StructtypesConfig.Nestedarr.Inner.Gender.Enum.FEMALE);
        nestedArrBuilder.inner(innerNestedArrBuilder);
        builder.nestedarr(nestedArrBuilder);

        final StructtypesConfig config = new StructtypesConfig(builder);
        final String expectedJson = inputJson(
                "{",
                "    'complexarr': [],",
                "    'nested': {",
                "        'inner': {",
                "            'emails': [",
                "                'Ulf Lilleengen@foo',",
                "                'Ulf Lilleengen@bar'",
                "            ],",
                "            'gender': 'MALE',",
                "            'name': 'foo'",
                "        }",
                "    },",
                "    'nestedarr': [",
                "        {",
                "            'inner': {",
                "                'emails': [",
                "                    'foo@bar'",
                "                ],",
                "                'gender': 'FEMALE',",
                "                'name': 'bar'",
                "            }",
                "        }",
                "    ],",
                "    'simple': {",
                "        'emails': [],",
                "        'gender': 'MALE',",
                "        'name': '_default_'",
                "    },",
                "    'simplearr': []",
                "}"
        );

        assertConfigEquals(expectedJson, config);
    }

    @Test
    public void test_that_arrays_are_formatted_to_json() {
        ArraytypesConfig.Builder builder = new ArraytypesConfig.Builder();
        builder.boolarr(true);
        builder.boolarr(false);
        builder.doublearr(1.2);
        builder.doublearr(1.1);
        builder.enumarr(ArraytypesConfig.Enumarr.Enum.VAL1);
        builder.enumarr(ArraytypesConfig.Enumarr.Enum.VAL2);
        builder.intarr(3);
        builder.longarr(4L);
        builder.stringarr("foo");

        final ArraytypesConfig config = new ArraytypesConfig(builder);
        final String expectedJson = inputJson(
                "{",
                "    'boolarr': [",
                "        true,",
                "        false",
                "    ],",
                "    'doublearr': [",
                "        1.2,",
                "        1.1",
                "    ],",
                "    'enumarr': [",
                "        'VAL1',",
                "        'VAL2'",
                "    ],",
                "    'intarr': [",
                "        3",
                "    ],",
                "    'longarr': [",
                "        4",
                "    ],",
                "    'stringarr': [",
                "        'foo'",
                "    ]",
                "}"
        );

        assertConfigEquals(expectedJson, config);
    }

    @Test
    public void test_that_maps_are_formatted_to_json() {
        MaptypesConfig.Builder builder = new MaptypesConfig.Builder();
        builder.boolmap("foo", true);
        builder.intmap("bar", 3);
        builder.stringmap("hei", "hallo");
        MaptypesConfig.Innermap.Builder inner = new MaptypesConfig.Innermap.Builder();
        inner.foo(133);
        builder.innermap("baz", inner);
        MaptypesConfig.Nestedmap.Builder nested = new MaptypesConfig.Nestedmap.Builder();
        nested.inner("foo", 33);
        builder.nestedmap("bim", nested);

        final MaptypesConfig config = new MaptypesConfig(builder);
        final String expectedJson = inputJson(
                "{",
                "  'boolmap': {",
                "        'foo': true",
                "    },",
                "    'doublemap': {},",
                "    'filemap': {},",
                "    'innermap': {",
                "        'baz': {",
                "            'foo': 133",
                "        }",
                "    },",
                "    'intmap': {",
                "        'bar': 3",
                "    },",
                "    'longmap': {},",
                "    'nestedmap': {",
                "        'bim': {",
                "            'inner': {",
                "                'foo': 33",
                "            }",
                "        }",
                "    },",
                "    'stringmap': {",
                "        'hei': 'hallo'",
                "    }",
                "}"
        );

        assertConfigEquals(expectedJson, config);
    }

    @Test
    public void test_that_non_standard_types_are_formatted_as_json_strings() {
        SpecialtypesConfig.Builder builder = new SpecialtypesConfig.Builder();
        builder.myfile("thefilename");
        builder.myref("thereference");

        final SpecialtypesConfig config = new SpecialtypesConfig(builder);
        final String expectedJson = inputJson(
                "{",
                "    'myfile': 'thefilename',",
                "    'myref': 'thereference'",
                "}"
        );

        assertConfigEquals(expectedJson, config);
    }

    static void assertConfigEquals(String expectedJson, ConfigInstance config) {
        Slime slime = new Slime();
        ConfigInstance.serialize(config, new ConfigInstanceSerializer(slime));
        JsonFormat jsonFormat = new JsonFormat(true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            jsonFormat.encode(baos, slime);
        } catch (IOException e) {
            fail();
        }

        assertJsonEquals(baos.toString(), expectedJson);
    }

}
