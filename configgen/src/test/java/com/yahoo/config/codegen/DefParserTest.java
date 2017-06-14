// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import static org.hamcrest.CoreMatchers.not;
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
 * @author <a href="gv@yahoo-inc.com">G. Voldengen</a>
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
        assertThat(children.length, is(31));

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
        assertThat(root.defMd5, is("eb2d24dbbcf054b21be729e2cfaafd93"));
    }

    @Test
    public void testMd5Sum2() {
        String def = "version=1\na string\n";
        CNode root = new DefParser("testMd5Sum2", new StringReader(def)).getTree();
        assertThat(root.defMd5, is("a5e5fdbb2b27e56ba7d5e60e335c598b"));
    }

    @Test
    public void testExplicitNamespace() {
        DefParser parser = createParser("version=1\nnamespace=myproject.config\na string\n");
        CNode root = parser.getTree();
        assertThat(root.getNamespace(), is("myproject.config"));

        // with spaces
        parser = createParser("version=1\nnamespace =  myproject.config\na string\n");
        root = parser.getTree();
        assertThat(root.getNamespace(), is("myproject.config"));

        // invalid
        parser = createParser("version=1\nnamespace \na string\n");
        try {
            parser.getTree();
            fail();
        } catch (Exception e) {
            //e.printStackTrace();
            assertExceptionAndMessage(e, CodegenRuntimeException.class,
                    "Error parsing or reading config definition.Error when parsing line 2: namespace \n" +
                            "namespace");
        }

        // invalid
        parser = createParser("version=1\nnamespace=a..b\na string\n");
        try {
            parser.getTree();
            fail();
        } catch (Exception e) {
            //e.printStackTrace();
            assertExceptionAndMessage(e, CodegenRuntimeException.class,
                    "Error parsing or reading config definition.Error when parsing line 2: namespace=a..b\n" +
                            "namespace=a..b");
        }
    }

    @Test
    public void verifyThatExplicitNamespaceAltersDefMd5() {
        DefParser parser = createParser("version=1\na string\n");
        CNode root = parser.getTree();

        parser = createParser("version=1\nnamespace=myproject.config\na string\n");
        CNode namespaceRoot = parser.getTree();

        assertThat(root.defMd5, not(namespaceRoot.defMd5));
    }


    @Test(expected = CodegenRuntimeException.class)
    public void verify_fail_on_illegal_char_in_namespace() {
        createParser("version=1\nnamespace=Foo\na string\n").getTree();
    }

    @Test(expected = CodegenRuntimeException.class)
    public void verify_fail_on_com_yahoo_in_explicit_namespace() {
        createParser("version=1\n" +
                "namespace=com.yahoo.myproject.config\n" +
                "a string\n").getTree();
    }

    @Test
    public void testInvalidType() {
        Class<?> exceptionClass = DefParser.DefParserException.class;
        try {
            createParser("version=1\n" +
                    "# comment\n" +
                    "a sting").getTree();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage((Exception) e.getCause(), exceptionClass,
                    "Error when parsing line 3: a sting", false);
        }
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
    public void testMissingVersion() {
        try {
            createParser("a string\n").parse();
        } catch (Exception e) {
            fail("Should not get an exception here");
        }
    }

    private DefParser createParser(String def) {
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
        Class<?> exceptionClass = DefParser.DefParserException.class;
        DefParser parser = createParser("version=1\nf file default=\"file1.txt\"\n");
        try {
            parser.getTree();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage((Exception) e.getCause(), exceptionClass,
                    "Error when parsing line 2: f file default=\"file1.txt\"\n" +
                            "Invalid default value", false);
        }
    }

    // Helper method for checking correct exception class and message
    void assertExceptionAndMessage(Exception e, Class<?> exceptionClass, String message) {
        assertExceptionAndMessage(e, exceptionClass, message, true);
    }

    // Helper method for checking correct exception class and message
    void assertExceptionAndMessage(Exception e, Class<?> exceptionClass, String message, boolean exact) {
        if (exact) {
            assertEquals(message, e.getMessage());
        } else {
            assertTrue(e.getMessage().startsWith(message));
        }
        assertEquals(exceptionClass.getName(), e.getClass().getName());
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
    public void testInvalidLine() {
        Class<?> exceptionClass = DefParser.DefParserException.class;
        StringBuilder sb = createDefTemplate();
        String invalidLine = "a inta\n";
        sb.append(invalidLine);
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                    "Error when parsing line 3: " + invalidLine + "Could not create inta a");
        }
    }

    @Test
    public void testDuplicateDefinition() {
        Class<?> exceptionClass = DefParser.DefParserException.class;
        StringBuilder sb = createDefTemplate();
        String invalidLine = "b int\n";
        sb.append(invalidLine);
        // Add a duplicate line, which should be illegal
        sb.append(invalidLine);
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                    "Error when parsing line 4: " + invalidLine + "b is already defined");
        }
    }

    @Test
    public void testIllegalCharacterInName() {
        Class<?> exceptionClass = DefParser.DefParserException.class;
        StringBuilder sb = createDefTemplate();
        String invalidLine = "a-b int\n";
        sb.append(invalidLine);
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                    "Error when parsing line 3: " + invalidLine + "a-b contains unexpected character");
        }
    }

    @Test
    public void require_that_parameter_name_starting_with_digit_is_illegal() {
        Class<?> exceptionClass = DefParser.DefParserException.class;
        StringBuilder sb = createDefTemplate();
        String invalidLine = "1a int\n";
        sb.append(invalidLine);
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                    "Error when parsing line 3: " + invalidLine + "1a must start with a non-digit character");
        }
    }

    @Test
    public void require_that_parameter_name_starting_with_uppercase_is_illegal() {
        Class<?> exceptionClass = DefParser.DefParserException.class;
        StringBuilder sb = createDefTemplate();
        String invalidLine = "SomeInt int\n";
        sb.append(invalidLine);
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                    "Error when parsing line 3: " + invalidLine + "'SomeInt' cannot start with an uppercase letter");
        }
    }

    @Test
    public void require_that_parameter_name_starting_with_the_internal_prefix_is_illegal() {
        String internalPrefix = ReservedWords.INTERNAL_PREFIX;
        Class<?> exceptionClass = DefParser.DefParserException.class;
        StringBuilder sb = createDefTemplate();
        String invalidLine = internalPrefix + "i int\n";
        sb.append(invalidLine);
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                    "Error when parsing line 3: " + invalidLine +
                            "'" + internalPrefix + "i' cannot start with '" + internalPrefix + "'");
        }
    }

    @Test
    public void testIllegalArray() {
        Class<?> exceptionClass = DefParser.DefParserException.class;
        StringBuilder sb = createDefTemplate();
        String invalidLine = "intArr[ int\n";
        sb.append(invalidLine);
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                    "Error when parsing line 3: " + invalidLine + "intArr[ Expected ] to terminate array definition");
        }
    }

    @Test
    public void testIllegalDefault() {
        Class<?> exceptionClass = DefParser.DefParserException.class;
        StringBuilder sb = createDefTemplate();
        String invalidLine = "a int deflt 10\n";
        sb.append(invalidLine);
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                    "Error when parsing line 3: " + invalidLine + " deflt 10");
        }
    }

    @Test
    public void testReservedWordInC() {
        Class<?> exceptionClass = DefParser.DefParserException.class;
        StringBuilder sb = createDefTemplate();
        String invalidLine = "auto int\n";
        sb.append(invalidLine);
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                    "Error when parsing line 3: " + invalidLine + "auto is a reserved word in C");
        }
    }

    @Test
    public void testReservedWordInJava() {
        Class<?> exceptionClass = DefParser.DefParserException.class;
        StringBuilder sb = createDefTemplate();
        String invalidLine = "abstract int\n";
        sb.append(invalidLine);
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                    "Error when parsing line 3: " + invalidLine + "abstract is a reserved word in Java");
        }
    }

    @Test
    public void testReservedWordInCAndJava() {
        Class<?> exceptionClass = DefParser.DefParserException.class;
        StringBuilder sb = createDefTemplate();
        String invalidLine = "continue int\n";
        sb.append(invalidLine);
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                    "Error when parsing line 3: " + invalidLine + "continue is a reserved word in C and Java");
        }
    }

    @Test
    public void testNumberInNamespace() throws IOException, DefParser.DefParserException {
        StringBuilder sb = createDefTemplate();
        String line = "namespace=a.b.c2\nfoo int\n";
        sb.append(line);
        createParser(sb.toString()).parse();

        sb = createDefTemplate();
        line = "namespace=2.a.b\n";
        sb.append(line);
        Class<?> exceptionClass = DefParser.DefParserException.class;
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                    "Error when parsing line 3: " + line + "namespace=2.a.b");
        }

        sb = createDefTemplate();
        line = "namespace=a.b.2c\n";
        sb.append(line);
        exceptionClass = DefParser.DefParserException.class;
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                    "Error when parsing line 3: " + line + "namespace=a.b.2c");
        }
    }

    @Test
    public void testUnderscoreInNamespace() throws IOException, DefParser.DefParserException {
        StringBuilder sb = createDefTemplate();
        String line = "namespace=a_b.c\nfoo int\n";
        sb.append(line);
        createParser(sb.toString()).parse();

        sb = createDefTemplate();
        line = "namespace=a_b.c_d\nfoo int\n";
        sb.append(line);
        createParser(sb.toString()).parse();
    }

    private StringBuilder createDefTemplate() {
        StringBuilder sb = new StringBuilder();
        sb.append("version=8\n");
        // Add a comment line to check that we get correct line number with comments
        sb.append("# comment\n");

        return sb;
    }
}
