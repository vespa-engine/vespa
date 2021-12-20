// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import org.junit.Test;

import java.io.IOException;

import static com.yahoo.config.codegen.DefParserTest.assertLineFails;
import static com.yahoo.config.codegen.DefParserTest.createDefTemplate;
import static com.yahoo.config.codegen.DefParserTest.createParser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests setting explicit java package in the def file.
 *
 * @author gjoranv
 */
public class DefParserPackageTest {
    String PACKAGE = "com.github.myproject";

    @Test
    public void package_is_set_on_root_node() {
        DefParser parser = createParser("package=" + PACKAGE + "\n");
        CNode root = parser.getTree();
        assertEquals(PACKAGE, root.getPackage());
    }

    @Test
    public void package_and_namespace_can_coexist() {
        String namespace = "test.namespace";
        DefParser parser = createParser("package=" + PACKAGE +
                                                "\nnamespace=" + namespace +"\n");
        CNode root = parser.getTree();
        assertEquals(PACKAGE, root.getPackage());
        assertEquals(namespace, root.getNamespace());
    }

    // Required by JavaClassBuilder ctor.
    @Test
    public void package_is_null_when_not_explicitly_given() {
        String namespace = "test.namespace";
        DefParser parser = createParser("namespace=" + namespace + "\n");
        CNode root = parser.getTree();
        assertNull(root.getPackage());
    }

    @Test(expected = CodegenRuntimeException.class)
    public void uppercase_chars_are_not_allowed() {
        createParser("package=Foo.bar\n").getTree();
    }

    @Test
    public void spaces_are_allowed_around_equals_sign() {
        DefParser parser = createParser("package =  " + PACKAGE + "\n");
        CNode root = parser.getTree();
        assertEquals(PACKAGE, root.getPackage());
    }

    @Test
    public void empty_package_is_not_allowed() {
       assertLineFails("package");
    }

    @Test
    public void consecutive_dots_are_not_allowed() {
        assertLineFails("package=a..b");
    }

    @Test
    public void package_alters_def_md5() {
        DefParser parser = createParser("a string\n");
        CNode root = parser.getTree();

        parser = createParser("package=" + PACKAGE + "\na string\n");
        CNode rootWithPackage = parser.getTree();

        assertNotEquals(root.defMd5, rootWithPackage.defMd5);
    }


    @Test
    public void number_is_allowed_as_non_leading_char() throws IOException, DefParser.DefParserException {
        StringBuilder sb = createDefTemplate();
        String line = "package=a.b.c2\n";
        sb.append(line);
        createParser(sb.toString()).parse();
    }

    @Test
    public void number_is_not_allowed_as_package_start_char() {
        assertLineFails("package=2.a.b");
    }

    @Test
    public void number_is_not_allowed_as_leading_char_in_package_token() {
        assertLineFails("package=a.b.2c");
    }

    @Test
    public void underscore_in_package_is_allowed() throws IOException, DefParser.DefParserException {
        StringBuilder sb = createDefTemplate();
        String line = "package=a_b.c\n";
        sb.append(line);
        createParser(sb.toString()).parse();

        sb = createDefTemplate();
        line = "package=a_b.c_d\n";
        sb.append(line);
        createParser(sb.toString()).parse();
    }

}
