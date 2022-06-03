// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import org.junit.Test;

import java.io.IOException;

import static com.yahoo.config.codegen.DefParserTest.assertLineFails;
import static com.yahoo.config.codegen.DefParserTest.createDefTemplate;
import static com.yahoo.config.codegen.DefParserTest.createParser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * @author gjoranv
 * @author musum
 */
public class DefParserNamespaceTest {

    @Test
    public void namespace_is_set_on_root_node() {
        DefParser parser = createParser("namespace=myproject.config\n");
        CNode root = parser.getTree();
        assertEquals("myproject.config", root.getNamespace());
    }

    @Test
    public void package_is_used_as_namespace_when_no_namespace_is_given() {
        String PACKAGE = "com.github.myproject";
        DefParser parser = createParser("package=" + PACKAGE + "\n");
        CNode root = parser.getTree();
        assertEquals(PACKAGE, root.getNamespace());
    }

    @Test(expected = CodegenRuntimeException.class)
    public void uppercase_chars_are_not_allowed() {
        createParser("namespace=Foo\n").getTree();
    }

    @Test(expected = CodegenRuntimeException.class)
    public void explicit_com_yahoo_prefix_is_not_allowed() {
        createParser("namespace=com.yahoo.myproject.config\n").getTree();
    }

    @Test
    public void spaces_are_allowed_around_equals_sign() {
        DefParser parser = createParser("namespace =  myproject.config\n");
        CNode root = parser.getTree();
        assertEquals("myproject.config", root.getNamespace());
    }

    @Test
    public void empty_namespace_is_not_allowed() {
        assertLineFails("namespace");
    }

    @Test
    public void consecutive_dots_are_not_allowed() {
        assertLineFails("namespace=a..b");
    }

    @Test
    public void namespace_alters_def_md5() {
        DefParser parser = createParser("");
        CNode root = parser.getTree();

        parser = createParser("namespace=myproject.config\n");
        CNode namespaceRoot = parser.getTree();

        assertNotEquals(root.defMd5, namespaceRoot.defMd5);
    }


    @Test
    public void number_is_allowed_as_non_leading_char_in_namespace() throws IOException, DefParser.DefParserException {
        StringBuilder sb = createDefTemplate();
        String line = "namespace=a.b.c2\n";
        sb.append(line);
        createParser(sb.toString()).parse();
    }

    @Test
    public void number_is_not_allowed_as_namespace_start_char() {
        assertLineFails("namespace=2.a.b");
    }

    @Test
    public void number_is_not_allowed_as_leading_char_in_namespace_token() {
        assertLineFails("namespace=a.b.2c");
    }

    @Test
    public void underscore_in_namespace_is_allowed() throws IOException, DefParser.DefParserException {
        StringBuilder sb = createDefTemplate();
        String line = "namespace=a_b.c\n";
        sb.append(line);
        createParser(sb.toString()).parse();

        sb = createDefTemplate();
        line = "namespace=a_b.c_d\n";
        sb.append(line);
        createParser(sb.toString()).parse();
    }

}
