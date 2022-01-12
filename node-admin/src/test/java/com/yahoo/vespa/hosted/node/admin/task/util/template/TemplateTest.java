// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author hakonhall
 */
public class TemplateTest {
    @Test
    void verifyNewlineRemoval() {
        Template template = Template.from("a%{list a}\n" +
                                          "b%{end}\n" +
                                          "c%{list c-}\n" +
                                          "d%{end-}\n" +
                                          "e\n",
                                          new TemplateDescriptor().setRemoveNewline(false));
        template.add("a");
        template.add("c");

        assertEquals("a\n" +
                     "b\n" +
                     "cde\n",
                     template.render());
    }

    @Test
    void verifyIfSection() {
        Template template = Template.from("Hello%{if cond} world%{end}!");
        assertEquals("Hello world!", template.snapshot().set("cond", true).render());
        assertEquals("Hello!", template.snapshot().set("cond", false).render());
    }

    @Test
    void verifyComplexIfSection() {
        Template template = Template.from("%{if cond}\n" +
                                          "var: %{=varname}\n" +
                                          "if: %{if !inner}inner is false%{end-}\n" +
                                          "list: %{list formname}element%{end-}\n" +
                                          "%{end}\n");

        assertEquals("", template.snapshot().set("cond", false).render());

        assertEquals("var: varvalue\n" +
                     "if: \n" +
                     "list: \n",
                     template.snapshot()
                             .set("cond", true)
                             .set("varname", "varvalue")
                             .set("inner", true)
                             .render());

        Template template2 = template.snapshot()
                                     .set("cond", true)
                                     .set("varname", "varvalue")
                                     .set("inner", false);
        template2.add("formname");

        assertEquals("var: varvalue\n" +
                     "if: inner is false\n" +
                     "list: element\n", template2.render());
    }

    @Test
    void verifyElse() {
        var template = Template.from("%{if cond}\n" +
                                     "if body\n" +
                                     "%{else}\n" +
                                     "else body\n" +
                                     "%{end}\n");
        assertEquals("if body\n", template.snapshot().set("cond", true).render());
        assertEquals("else body\n", template.snapshot().set("cond", false).render());
    }

    @Test
    void verifySnapshotPreservesList() {
        var template = Template.from("%{list foo}hello %{=area}%{end}");
        template.add("foo")
                .set("area", "world");

        assertEquals("hello world", template.render());
        assertEquals("hello world", template.snapshot().render());

        Template snapshot = template.snapshot();
        snapshot.add("foo")
                .set("area", "Norway");
        assertEquals("hello worldhello Norway", snapshot.render());
    }

    @Test
    void verifyVariableSection() {
        Template template = getTemplate("template1.tmp");
        template.set("varname", "varvalue");
        assertEquals("variable section 'varvalue'\n" +
                     "end of text\n", template.render());
    }

    @Test
    void verifySimpleListSection() {
        Template template = getTemplate("template1.tmp");
        template.set("varname", "varvalue")
                .add("listname")
                .set("varname", "different varvalue")
                .set("varname2", "varvalue2");
        assertEquals("variable section 'varvalue'\n" +
                     "same variable section 'different varvalue'\n" +
                     "different variable section 'varvalue2'\n" +
                     "between ends\n" +
                     "end of text\n", template.render());
    }

    @Test
    void verifyNestedListSection() {
        Template template = getTemplate("template2.tmp");
        Template A0 = template.add("listA");
        Template A0B0 = A0.add("listB");
        Template A0B1 = A0.add("listB");

        Template A1 = template.add("listA");
        Template A1B0 = A1.add("listB");
        assertEquals("body A\n" +
                     "body B\n" +
                     "body B\n" +
                     "body A\n" +
                     "body B\n",
                     template.render());
    }

    @Test
    void verifyVariableReferences() {
        Template template = getTemplate("template3.tmp");
        template.set("varname", "varvalue")
                .set("innerVarSetAtTop", "val2");
        template.add("l");
        template.add("l")
                .set("varname", "varvalue2");
        assertEquals("varvalue\n" +
                     "varvalue\n" +
                     "inner varvalue\n" +
                     "val2\n" +
                     "inner varvalue2\n" +
                     "val2\n",
                     template.render());
    }

    private Template getTemplate(String filename) {
        return Template.at(Path.of("src/test/resources/" + filename));
    }
}
