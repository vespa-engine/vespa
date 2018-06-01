// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;

import static com.yahoo.config.codegen.ConfiggenUtil.createClassName;
import static com.yahoo.config.codegen.JavaClassBuilder.createUniqueSymbol;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 * @author ollivir
 */
public class JavaClassBuilderTest {
    private static final String TEST_DIR = "target/test-classes/";
    private static final String DEF_NAME = TEST_DIR + "allfeatures.def";
    private static final String REFERENCE_NAME = TEST_DIR + "allfeatures.reference";

    @Ignore
    @Test
    public void visual_inspection_of_generated_class() {
        final String testDefinition = "version=1\n" + //
                "namespace=test\n" + //
                "p path\n" + //
                "pathArr[] path\n" + //
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
    public void testCreateUniqueSymbol() {
        final String testDefinition = "version=1\n" + //
                "namespace=test\n" + //
                "m int\n" + //
                "n int\n";
        InnerCNode root = new DefParser("test", new StringReader(testDefinition)).getTree();

        assertThat(createUniqueSymbol(root, "foo"), is("f"));
        assertThat(createUniqueSymbol(root, "name"), is("na"));
        assertTrue(createUniqueSymbol(root, "m").startsWith(ReservedWords.INTERNAL_PREFIX + "m"));

        // The basis string is not a legal return value, even if unique, to avoid
        // multiple symbols with the same name if the same basis string is given twice.
        assertTrue(createUniqueSymbol(root, "my").startsWith(ReservedWords.INTERNAL_PREFIX + "my"));
    }

    @Test
    public void testCreateClassName() {
        assertThat(createClassName("simple"), is("SimpleConfig"));
        assertThat(createClassName("a"), is("AConfig"));
        assertThat(createClassName("a-b-c"), is("ABCConfig"));
        assertThat(createClassName("a-1-2b"), is("A12bConfig"));
        assertThat(createClassName("my-app"), is("MyAppConfig"));
        assertThat(createClassName("MyApp"), is("MyAppConfig"));
    }

    @Test(expected = CodegenRuntimeException.class)
    public void testIllegalClassName() {
        createClassName("+illegal");
    }

    @Test
    public void verify_generated_class_against_reference() throws IOException {
        final String testDefinition = String.join("\n", Files.readAllLines(FileSystems.getDefault().getPath(DEF_NAME)));
        final String referenceClass = String.join("\n", Files.readAllLines(FileSystems.getDefault().getPath(REFERENCE_NAME))) + "\n";

        DefParser parser = new DefParser("allfeatures", new StringReader(testDefinition));
        InnerCNode root = parser.getTree();
        JavaClassBuilder builder = new JavaClassBuilder(root, parser.getNormalizedDefinition(), null, null);
        String configClass = builder.getConfigClass("AllfeaturesConfig");

        assertEquals(referenceClass, configClass);
    }
}
