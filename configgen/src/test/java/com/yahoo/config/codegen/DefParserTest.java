// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.Ignore;

import static org.hamcrest.CoreMatchers.is;

import java.io.*;

/**
 * Unit tests for DefParser.
 *
 * @author hmusum
 * @author gjoranv
 */
public class DefParserTest {

    private static final String TEST_DIR = "target/test-classes/";
    private static final String DEF_NAME = TEST_DIR + "allfeatures.def";

    @Test
    public void testTraverseTree() throws IOException {
        File defFile = new File(DEF_NAME);
        CNode root = new DefParser("test", new FileReader(defFile)).getTree();
        assertNotNull(root);
        CNode[] children = root.getChildren();
        assertThat(children.length, is(34));

        int numGrandChildren = 0;
        int numGreatGrandChildren = 0;
        for (CNode child : children) {
            CNode[] childsChildren = child.getChildren();
            numGrandChildren += childsChildren.length;
            for (CNode grandChild : childsChildren) {
                numGreatGrandChildren += grandChild.getChildren().length;
            }
        }
        assertThat(numGrandChildren, is(14));
        assertThat(numGreatGrandChildren, is(6));

        // Verify that each array creates a sub-tree, and that defaults for leafs are handled correctly.
        CNode myArray = root.getChild("myArray");
        assertThat(myArray.getChildren().length, is(5));
        // int within array
        LeafCNode myArrayInt = (LeafCNode) myArray.getChild("intVal");
        assertThat(myArrayInt.getDefaultValue().getValue(), is("14"));
        // enum within array
        LeafCNode myArrayEnum = (LeafCNode) myArray.getChild("enumVal");
        assertThat(myArrayEnum.getDefaultValue().getValue(), is("TYPE"));

        // Verify array within array and a default value for a leaf in the inner array.
        CNode anotherArray = myArray.getChild("anotherArray");
        assertThat(anotherArray.getChildren().length, is(1));
        LeafCNode foo = (LeafCNode) anotherArray.getChild("foo");
        assertThat(foo.getDefaultValue().getValue(), is("-4"));
    }

    @Test
    public void testFileWithNamespaceInFilename() throws IOException {
        File defFile = new File(TEST_DIR + "bar.foo.def");
        CNode root = new DefParser("test", new FileReader(defFile)).getTree();
        assertThat(root.defMd5, is("31a0f9bda0e5ff929762a29569575a7e"));
    }

    @Test
    public void testMd5Sum() throws IOException {
        File defFile = new File(DEF_NAME);
        CNode root = new DefParser("test", new FileReader(defFile)).getTree();
        assertThat(root.defMd5, is("f901bdc5c96e7005130399c63f247823"));
    }

    @Test
    public void testMd5Sum2() {
        String def = "version=1\na string\n";
        CNode root = new DefParser("testMd5Sum2", new StringReader(def)).getTree();
        assertThat(root.defMd5, is("a5e5fdbb2b27e56ba7d5e60e335c598b"));
    }

    @Test
    public void testInvalidType() {
        String line = "a sting";
        assertLineFails(line, "Could not create sting a");
    }

    @Test
    public void testValidVersions() {
        try {
            testExpectedVersion("version=8", "8");
            testExpectedVersion("version=8-1", "8-1");
            testExpectedVersion("version =8", "8");
            testExpectedVersion("version = 8", "8");
            testExpectedVersion("version = 8 ", "8");
            testExpectedVersion("version =\t8", "8");
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    private void testExpectedVersion(String versionLine, String expectedVersion) {
        InnerCNode root = createParser(versionLine).getTree();
        assertThat(root.defVersion, is(expectedVersion));
    }

    @Test
    public void version_is_not_mandatory() {
        try {
            createParser("a string\n").parse();
        } catch (Exception e) {
            fail("Should not get an exception here");
        }
    }

    static DefParser createParser(String def) {
        return new DefParser("test", new StringReader(def));
    }

    @Test
    public void testInvalidVersion() {
        Class<?> exceptionClass = DefParser.DefParserException.class;
        testInvalidVersion("version=a\n", exceptionClass,
                "Error when parsing line 1: version=a\nversion=a");
        testInvalidVersion("version = a\n", exceptionClass,
                "Error when parsing line 1: version = a\n a");
    }

    private void testInvalidVersion(String versionLine, Class<?> exceptionClass, String exceptionMessage) {
        try {
            createParser(versionLine).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass, exceptionMessage);
        }
    }

    @Test
    public void verify_fail_on_default_for_file() {
        assertLineFails("f file default=\"file1.txt\"",
                        "Invalid default value");
    }

    @Test(expected = CodegenRuntimeException.class)
    @Ignore("Not implemented yet")
    public void testInvalidEnum() throws DefParser.DefParserException {
        DefParser parser = createParser("version=1\nanEnum enum {A, B, A}\n");
        //parser.validateDef(def);
    }

    @Test
    public void testEnum() {
        StringBuilder sb = createDefTemplate();
        sb.append("enum1 enum {A,B} default=A\n");
        sb.append("enum2 enum {A, B} default=A\n");
        sb.append("enum3 enum { A, B} default=A\n");
        sb.append("enum4 enum { A, B } default=A\n");
        sb.append("enum5 enum { A , B } default=A\n");
        sb.append("enum6 enum {A , B } default=A\n");
        sb.append("enumVal enum { FOO, BAR, FOOBAR }\n");

        DefParser parser = createParser(sb.toString());
        try {
            parser.getTree();
        } catch (Exception e) {
            assertNotNull(null);
        }
        CNode root = parser.getTree();
        LeafCNode node = (LeafCNode) root.getChild("enum1");
        assertNotNull(node);
        assertThat(node.getDefaultValue().getStringRepresentation(), is("A"));
    }

    @Test(expected = DefParser.DefParserException.class)
    public void testInvalidCommaInEnum() throws DefParser.DefParserException, IOException {
        String invalidEnum = "anEnum enum {A, B, } default=A\n";
        String validEnum = "anotherEnum enum {A, B} default=A\n";
        StringBuilder sb = createDefTemplate();
        sb.append(invalidEnum);
        sb.append(validEnum);
        DefParser parser = createParser(sb.toString());
        parser.parse();
    }

    @Ignore //TODO: finish this! The numeric leaf nodes must contain their range.
    @Test
    public void testRanges() {
        StringBuilder sb = new StringBuilder("version=1\n");
        sb.append("i int range=[0,10]");
        sb.append("l long range=[-1e20,0]");
        sb.append("d double range=[0,1]");

        DefParser parser = createParser(sb.toString());
        CNode root = parser.getTree();
        LeafCNode.IntegerLeaf intNode = (LeafCNode.IntegerLeaf) root.getChild("i");
    }

    @Test
    public void duplicate_parameter_is_illegal() {
        Class<?> exceptionClass = DefParser.DefParserException.class;
        StringBuilder sb = createDefTemplate();
        String duplicateLine = "b int\n";
        sb.append(duplicateLine);
        sb.append(duplicateLine);
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                    "Error when parsing line 4: " + duplicateLine + "b is already defined");
        }
    }

    @Test
    public void testIllegalCharacterInName() {
       assertLineFails("a-b int",
                       "a-b contains unexpected character");
    }

    @Test
    public void parameter_name_starting_with_digit_is_illegal() {
        assertLineFails("1a int",
                        "1a must start with a non-digit character");
    }

    @Test
    public void parameter_name_starting_with_uppercase_is_illegal() {
        assertLineFails("SomeInt int",
                        "'SomeInt' cannot start with an uppercase letter");
    }

    @Test
    public void parameter_name_starting_with_the_internal_prefix_is_illegal() {
        String internalPrefix = ReservedWords.INTERNAL_PREFIX;
        assertLineFails(internalPrefix + "i int",
                        "'" + internalPrefix + "i' cannot start with '" + internalPrefix + "'");
    }

    @Test
    public void testIllegalArray() {
        assertLineFails("intArr[ int",
                        "intArr[ Expected ] to terminate array definition");
    }

    @Test
    public void testIllegalDefault() {
        assertLineFails("a int deflt 10",
                        " deflt 10");
    }

    @Test
    public void testReservedWordInC() {
        assertLineFails("auto int",
                        "auto is a reserved word in C");
    }

    @Test
    public void testReservedWordInCForArray() {
        assertLineFails("auto[] int",
                        "auto is a reserved word in C");
    }

    @Test
    public void testReservedWordInJava() {
        assertLineFails("abstract int",
                        "abstract is a reserved word in Java");
    }

    @Test
    public void testReservedWordInJavaForMap() {
        assertLineFails("abstract{} int",
                        "abstract is a reserved word in Java");
    }

    @Test
    public void testReservedWordInCAndJava() {
        assertLineFails("continue int",
                        "continue is a reserved word in C and Java");
    }

    @Test
    public void testReservedWordInCAndJavaForArray() {
        assertLineFails("continue[] int",
                        "continue is a reserved word in C and Java");
    }

    static StringBuilder createDefTemplate() {
        StringBuilder sb = new StringBuilder();
        sb.append("version=8\n");
        // Add a comment line to check that we get correct line number with comments
        sb.append("# comment\n");

        return sb;
    }

    static void assertLineFails(String line) {
        assertLineFails(line, line);
    }

    static void assertLineFails(String line, String message) {
        StringBuilder sb = createDefTemplate();
        sb.append(line).append("\n");
        Class<?> exceptionClass = DefParser.DefParserException.class;
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                                      "Error when parsing line 3: " + line + "\n" + message);
        }
    }

    // Helper method for checking correct exception class and message
    private static void assertExceptionAndMessage(Exception e, Class<?> exceptionClass, String message) {
        assertExceptionAndMessage(e, exceptionClass, message, true);
    }

    // Helper method for checking correct exception class and message
    private static void assertExceptionAndMessage(Exception e, Class<?> exceptionClass, String message, boolean exact) {
        if (exact) {
            assertEquals(message, e.getMessage());
        } else {
            assertTrue(e.getMessage().startsWith(message));
        }
        assertEquals(exceptionClass.getName(), e.getClass().getName());
    }

}
