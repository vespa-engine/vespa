// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.yahoo.config.codegen.DefParserTest.assertLineFails;
import static com.yahoo.config.codegen.DefParserTest.createDefTemplate;
import static com.yahoo.config.codegen.DefParserTest.createParser;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author gjoranv
 * @author musum
 */
public class DefParserNamespaceTest {

    @Test
    void namespace_is_set_on_root_node() {
        DefParser parser = createParser("namespace=myproject.config\n");
        CNode root = parser.getTree();
        assertEquals("myproject.config", root.getNamespace());
    }

    @Test
    void package_is_used_as_namespace_when_no_namespace_is_given() {
        String PACKAGE = "com.github.myproject";
        DefParser parser = createParser("package=" + PACKAGE + "\n");
        CNode root = parser.getTree();
        assertEquals(PACKAGE, root.getNamespace());
    }

    @Test
    void uppercase_chars_are_not_allowed() {
        assertThrows(CodegenRuntimeException.class, () -> {
            createParser("namespace=Foo\n").getTree();
        });
    }

    @Test
    void explicit_com_yahoo_prefix_is_not_allowed() {
        assertThrows(CodegenRuntimeException.class, () -> {
            createParser("namespace=com.yahoo.myproject.config\n").getTree();
        });
    }

    @Test
    void spaces_are_allowed_around_equals_sign() {
        DefParser parser = createParser("namespace =  myproject.config\n");
        CNode root = parser.getTree();
        assertEquals("myproject.config", root.getNamespace());
    }

    @Test
    void empty_namespace_is_not_allowed() {
        assertLineFails("namespace");
    }

    @Test
    void consecutive_dots_are_not_allowed() {
        assertLineFails("namespace=a..b");
    }

    @Test
    void namespace_alters_def_md5() {
        DefParser parser = createParser("");
        CNode root = parser.getTree();

        parser = createParser("namespace=myproject.config\n");
        CNode namespaceRoot = parser.getTree();

        assertNotEquals(root.defMd5, namespaceRoot.defMd5);
    }


    @Test
    void number_is_allowed_as_non_leading_char_in_namespace() throws IOException, DefParser.DefParserException {
        StringBuilder sb = createDefTemplate();
        String line = "namespace=a.b.c2\n";
        sb.append(line);
        createParser(sb.toString()).parse();
    }

    @Test
    void number_is_not_allowed_as_namespace_start_char() {
        assertLineFails("namespace=2.a.b");
    }

    @Test
    void number_is_not_allowed_as_leading_char_in_namespace_token() {
        assertLineFails("namespace=a.b.2c");
    }

    @Test
    void underscore_in_namespace_is_allowed() throws IOException, DefParser.DefParserException {
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
