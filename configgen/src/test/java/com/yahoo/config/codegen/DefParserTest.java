// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefParser.
 *
 * @author hmusum
 * @author gjoranv
 */
public class DefParserTest {

    private static final String TEST_DIR = "target/test-classes/";
    private static final String DEF_NAME = TEST_DIR + "configgen.allfeatures.def";

    @Test
    void testTraverseTree() throws IOException {
        File defFile = new File(DEF_NAME);
        CNode root = new DefParser("test", new FileReader(defFile)).getTree();
        assertNotNull(root);
        CNode[] children = root.getChildren();
        assertEquals(37, children.length);

        int numGrandChildren = 0;
        int numGreatGrandChildren = 0;
        for (CNode child : children) {
            CNode[] childsChildren = child.getChildren();
            numGrandChildren += childsChildren.length;
            for (CNode grandChild : childsChildren) {
                numGreatGrandChildren += grandChild.getChildren().length;
            }
        }
        assertEquals(14, numGrandChildren);
        assertEquals(6, numGreatGrandChildren);

        // Verify that each array creates a sub-tree, and that defaults for leafs are handled correctly.
        CNode myArray = root.getChild("myArray");
        assertEquals(5, myArray.getChildren().length);
        // int within array
        LeafCNode myArrayInt = (LeafCNode) myArray.getChild("intVal");
        assertEquals("14", myArrayInt.getDefaultValue().getValue());
        // enum within array
        LeafCNode myArrayEnum = (LeafCNode) myArray.getChild("enumVal");
        assertEquals("TYPE", myArrayEnum.getDefaultValue().getValue());

        // Verify array within array and a default value for a leaf in the inner array.
        CNode anotherArray = myArray.getChild("anotherArray");
        assertEquals(1, anotherArray.getChildren().length);
        LeafCNode foo = (LeafCNode) anotherArray.getChild("foo");
        assertEquals("-4", foo.getDefaultValue().getValue());
    }

    @Test
    void testFileWithNamespaceInFilename() throws IOException {
        File defFile = new File(TEST_DIR + "baz.bar.foo.def");
        CNode root = new DefParser("test", new FileReader(defFile)).getTree();
        assertEquals("31a0f9bda0e5ff929762a29569575a7e", root.defMd5);
    }

    @Test
    void testMd5Sum() throws IOException {
        File defFile = new File(DEF_NAME);
        CNode root = new DefParser("test", new FileReader(defFile)).getTree();
        assertEquals("0501f9e2c4ecc8c283e100e0b1178ca4", root.defMd5);
    }

    @Test
    void testMd5Sum2() {
        String def = "a string\n";
        CNode root = new DefParser("testMd5Sum2", new StringReader(def)).getTree();
        assertEquals("a5e5fdbb2b27e56ba7d5e60e335c598b", root.defMd5);
    }

    // TODO: Version is not used anymore, remove test in Vespa 9
    @Test
    void testValidVersions() {
        try {
            parse("version=8");
            parse("version=8-1");
            parse("version =8");
            parse("version = 8");
            parse("version = 8 ");
            parse("version =\t8");
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    private void parse(String versionLine) {
        InnerCNode ignored = createParser(versionLine).getTree();
    }

    @Test
    void testInvalidType() {
        String line = "a sting";
        assertLineFails(line, "Could not create sting a");
    }

    static DefParser createParser(String def) {
        return new DefParser("test", new StringReader(def));
    }

    @Test
    void verify_fail_on_default_for_file() {
        assertLineFails("f file default=\"file1.txt\"",
                "Invalid default value");
    }

    @Test
    @Disabled("Not implemented yet")
    void testInvalidEnum() {
        assertThrows(CodegenRuntimeException.class, () -> {
            DefParser parser = createParser("anEnum enum {A, B, A}\n");
            //parser.validateDef(def);
        });
        //parser.validateDef(def);
    }

    @Test
    void testEnum() {
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
        assertEquals("A", node.getDefaultValue().getStringRepresentation());
    }

    @Test
    void testInvalidCommaInEnum() throws DefParser.DefParserException, IOException {
        assertThrows(DefParser.DefParserException.class, () -> {
            String invalidEnum = "anEnum enum {A, B, } default=A\n";
            String validEnum = "anotherEnum enum {A, B} default=A\n";
            StringBuilder sb = createDefTemplate();
            sb.append(invalidEnum);
            sb.append(validEnum);
            DefParser parser = createParser(sb.toString());
            parser.parse();
        });
    }

    @Disabled //TODO: finish this! The numeric leaf nodes must contain their range.
    @Test
    void testRanges() {
        StringBuilder sb = new StringBuilder();
        sb.append("i int range=[0,10]");
        sb.append("l long range=[-1e20,0]");
        sb.append("d double range=[0,1]");

        DefParser parser = createParser(sb.toString());
        CNode root = parser.getTree();
        LeafCNode.IntegerLeaf intNode = (LeafCNode.IntegerLeaf) root.getChild("i");
    }

    @Test
    void duplicate_parameter_is_illegal() {
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
                    "Error when parsing line 3: " + duplicateLine + "b is already defined");
        }
    }

    @Test
    void testIllegalCharacterInName() {
        assertLineFails("a-b int",
                "'a-b' contains an unexpected character");
    }

    @Test
    void parameter_name_starting_with_digit_is_illegal() {
        assertLineFails("1a int",
                "1a must start with a non-digit character");
    }

    @Test
    void parameter_name_starting_with_uppercase_is_illegal() {
        assertLineFails("SomeInt int",
                "'SomeInt' cannot start with an uppercase letter");
    }

    @Test
    void parameter_name_starting_with_the_internal_prefix_is_illegal() {
        String internalPrefix = ReservedWords.INTERNAL_PREFIX;
        assertLineFails(internalPrefix + "i int",
                "'" + internalPrefix + "i' cannot start with '" + internalPrefix + "'");
    }

    @Test
    void testIllegalArray() {
        assertLineFails("intArr[ int",
                "intArr[ Expected ] to terminate array definition");
    }

    @Test
    void testIllegalDefault() {
        assertLineFails("a int deflt 10",
                " deflt 10");
    }

    @Test
    void testReservedWordInC() {
        assertLineFails("auto int",
                "auto is a reserved word in C");
    }

    @Test
    void testReservedWordInCForArray() {
        assertLineFails("auto[] int",
                "auto is a reserved word in C");
    }

    @Test
    void testReservedWordInJava() {
        assertLineFails("abstract int",
                "abstract is a reserved word in Java");
    }

    @Test
    void testReservedWordInJavaForMap() {
        assertLineFails("abstract{} int",
                "abstract is a reserved word in Java");
    }

    @Test
    void testReservedWordInCAndJava() {
        assertLineFails("continue int",
                "continue is a reserved word in C and Java");
    }

    @Test
    void testReservedWordInCAndJavaForArray() {
        assertLineFails("continue[] int",
                "continue is a reserved word in C and Java");
    }

    static StringBuilder createDefTemplate() {
        StringBuilder sb = new StringBuilder();
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
                                      "Error when parsing line 2: " + line + "\n" + message);
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
