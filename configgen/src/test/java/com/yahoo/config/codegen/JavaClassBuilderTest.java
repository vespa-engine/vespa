// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.List;

import static com.yahoo.config.codegen.ConfiggenUtil.createClassName;
import static com.yahoo.config.codegen.JavaClassBuilder.createUniqueSymbol;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author gjoranv
 * @author ollivir
 */
public class JavaClassBuilderTest {

    private static final String TEST_DIR = "target/test-classes/";
    private static final String DEF_NAME = TEST_DIR + "configgen.allfeatures.def";
    private static final String REFERENCE_NAME = TEST_DIR + "allfeatures.reference";

    @Disabled
    @Test
    void visual_inspection_of_generated_class() {
        final String testDefinition =
                "namespace=test\n" + //
                        "p path\n" + //
                        "pathArr[] path\n" + //
                        "u url\n" + //
                        "urlArr[] url\n" + //
                        "modelArr[] model\n" + //
                        "f file\n" + //
                        "fileArr[] file\n" + //
                        "i int default=0\n" + //
                        "# A long value\n" + //
                        "l long default=0\n" + //
                        "s string default=\"\"\n" + //
                        "b bool\n" + //
                        "# An enum value\n" + //
                        "e enum {A, B, C}\n" + //
                        "intArr[] int\n" + //
                        "boolArr[] bool\n" + //
                        "enumArr[] enum {FOO, BAR}\n" + //
                        "intMap{} int\n" + //
                        "# A struct\n" + //
                        "# with multi-line\n" + //
                        "# comment and \"quotes\".\n" + //
                        "myStruct.i int\n" + //
                        "myStruct.s string\n" + //
                        "# An inner array\n" + //
                        "myArr[].i int\n" + //
                        "myArr[].newStruct.s string\n" + //
                        "myArr[].newStruct.b bool\n" + //
                        "myArr[].intArr[] int\n" + //
                        "# An inner map\n" + //
                        "myMap{}.i int\n" + //
                        "myMap{}.newStruct.s string\n" + //
                        "myMap{}.newStruct.b bool\n" + //
                        "myMap{}.intArr[] int\n" + //
                        "intMap{} int\n";

        DefParser parser = new DefParser("test", new StringReader(testDefinition));
        InnerCNode root = parser.getTree();
        JavaClassBuilder builder = new JavaClassBuilder(root, parser.getNormalizedDefinition(), null, null);
        String configClass = builder.getConfigClass("TestConfig");
        System.out.print(configClass);
    }

    @Test
    void testCreateUniqueSymbol() {
        final String testDefinition =
                "namespace=test\n" + //
                        "m int\n" + //
                        "n int\n";
        InnerCNode root = new DefParser("test", new StringReader(testDefinition)).getTree();

        assertEquals("f", createUniqueSymbol(root, "foo"));
        assertEquals("na", createUniqueSymbol(root, "name"));
        assertTrue(createUniqueSymbol(root, "m").startsWith(ReservedWords.INTERNAL_PREFIX + "m"));

        // The basis string is not a legal return value, even if unique, to avoid
        // multiple symbols with the same name if the same basis string is given twice.
        assertTrue(createUniqueSymbol(root, "my").startsWith(ReservedWords.INTERNAL_PREFIX + "my"));
    }

    @Test
    void testCreateClassName() {
        assertEquals("SimpleConfig", createClassName("simple"));
        assertEquals("AConfig", createClassName("a"));
        assertEquals("ABCConfig", createClassName("a-b-c"));
        assertEquals("A12bConfig", createClassName("a-1-2b"));
        assertEquals("MyAppConfig", createClassName("my-app"));
        assertEquals("MyAppConfig", createClassName("MyApp"));
    }

    @Test
    void testIllegalClassName() {
        assertThrows(CodegenRuntimeException.class, () -> {
            createClassName("+illegal");
        });
    }

    @Test
    void verify_generated_class_against_reference() throws IOException {
        String testDefinition = String.join("\n", Files.readAllLines(FileSystems.getDefault().getPath(DEF_NAME)));
        List<String> referenceClassLines = Files.readAllLines(FileSystems.getDefault().getPath(REFERENCE_NAME));

        DefParser parser = new DefParser("allfeatures", new StringReader(testDefinition));
        InnerCNode root = parser.getTree();
        JavaClassBuilder builder = new JavaClassBuilder(root, parser.getNormalizedDefinition(), null, null);
        String[] configClassLines = builder.getConfigClass("AllfeaturesConfig").split("\n");

        for (var line : configClassLines) {
            System.out.println(line);
        }
        for (int i = 0; i < referenceClassLines.size(); i++) {
            if (configClassLines.length <= i)
                fail("Missing lines i generated config class. First missing line:\n" + referenceClassLines.get(i));
            assertEquals(referenceClassLines.get(i), configClassLines[i], "Line " + i);
        }
    }

}
