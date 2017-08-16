package com.yahoo.config.codegen;

import org.junit.Test;

import java.io.IOException;

import static com.yahoo.config.codegen.DefParserTest.assertExceptionAndMessage;
import static com.yahoo.config.codegen.DefParserTest.createDefTemplate;
import static com.yahoo.config.codegen.DefParserTest.createParser;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author gjoranv
 * @author musum
 */
public class DefParserNamespaceTest {

    @Test
    public void namespace_is_set_on_root_node() {
        DefParser parser = createParser("version=1\nnamespace=myproject.config\na string\n");
        CNode root = parser.getTree();
        assertThat(root.getNamespace(), is("myproject.config"));
    }

    @Test(expected = CodegenRuntimeException.class)
    public void uppercase_chars_are_not_allowed() {
        createParser("version=1\nnamespace=Foo\na string\n").getTree();
    }

    @Test(expected = CodegenRuntimeException.class)
    public void explicit_com_yahoo_prefix_is_not_allowed() {
        createParser("version=1\n" +
                             "namespace=com.yahoo.myproject.config\n" +
                             "a string\n").getTree();
    }

    @Test
    public void spaces_are_allowed_around_equals_sign() {
        DefParser parser = createParser("version=1\nnamespace =  myproject.config\na string\n");
        CNode root = parser.getTree();
        assertThat(root.getNamespace(), is("myproject.config"));
    }

    @Test
    public void empty_namespace_is_not_allowed() {
        // invalid
        DefParser parser = createParser("version=1\nnamespace \na string\n");
        try {
            parser.getTree();
            fail();
        } catch (Exception e) {
            //e.printStackTrace();
            assertExceptionAndMessage(e, CodegenRuntimeException.class,
                                      "Error parsing or reading config definition.Error when parsing line 2: namespace \n" +
                                              "namespace");
        }
    }

    @Test
    public void consecutive_dots_are_not_allowed() {
        // invalid
        DefParser parser = createParser("version=1\nnamespace=a..b\na string\n");
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
    public void namespace_alters_def_md5() {
        DefParser parser = createParser("version=1\na string\n");
        CNode root = parser.getTree();

        parser = createParser("version=1\nnamespace=myproject.config\na string\n");
        CNode namespaceRoot = parser.getTree();

        assertThat(root.defMd5, not(namespaceRoot.defMd5));
    }


    @Test
    public void number_is_allowed_as_non_leading_char_in_namespace() throws IOException, DefParser.DefParserException {
        StringBuilder sb = createDefTemplate();
        String line = "namespace=a.b.c2\nfoo int\n";
        sb.append(line);
        createParser(sb.toString()).parse();
    }

    @Test
    public void number_is_not_allowed_as_namespace_start_char() throws IOException, DefParser.DefParserException {
        StringBuilder sb = createDefTemplate();
        String line = "namespace=2.a.b";
        sb.append(line).append("\n");
        Class<?>  exceptionClass = DefParser.DefParserException.class;
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                                      "Error when parsing line 3: " + line + "\n" + line);
        }
    }

    @Test
    public void number_is_not_allowed_as_leading_char_in_namespace_token() throws IOException, DefParser.DefParserException {
        StringBuilder sb = createDefTemplate();
        String line = "namespace=a.b.2c";
        sb.append(line).append("\n");
        Class<?> exceptionClass = DefParser.DefParserException.class;
        try {
            createParser(sb.toString()).parse();
            fail("Didn't find expected exception of type " + exceptionClass);
        } catch (Exception e) {
            assertExceptionAndMessage(e, exceptionClass,
                                      "Error when parsing line 3: " + line + "\n" + line);
        }

    }

    @Test
    public void underscore_in_namespace_is_allowed() throws IOException, DefParser.DefParserException {
        StringBuilder sb = createDefTemplate();
        String line = "namespace=a_b.c\nfoo int\n";
        sb.append(line);
        createParser(sb.toString()).parse();

        sb = createDefTemplate();
        line = "namespace=a_b.c_d\nfoo int\n";
        sb.append(line);
        createParser(sb.toString()).parse();
    }

}
