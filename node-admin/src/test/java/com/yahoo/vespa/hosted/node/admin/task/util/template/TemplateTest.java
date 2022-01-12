// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import org.junit.jupiter.api.Test;

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
                                          "e\n");
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
        Template template = Template.from("%{if cond-}\n" +
                                          "var: %{=varname}\n" +
                                          "if: %{if !inner}inner is false%{end}\n" +
                                          "list: %{list formname}element%{end}\n" +
                                          "%{end-}\n");

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
        var template = Template.from("%{if cond-}\n" +
                                     "if body\n" +
                                     "%{else-}\n" +
                                     "else body\n" +
                                     "%{end-}\n");
        assertEquals("if body\n", template.snapshot().set("cond", true).render());
        assertEquals("else body\n", template.snapshot().set("cond", false).render());
    }
}
