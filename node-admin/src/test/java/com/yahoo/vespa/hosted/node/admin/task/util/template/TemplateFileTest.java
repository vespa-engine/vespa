// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author hakonhall
 */
class TemplateFileTest {
    @Test
    void verifyVariableSection() {
        Form form = getForm("template1.tmp");
        form.set("varname", "varvalue");
        assertEquals("variable section 'varvalue'\n" +
                     "end of text\n", form.render());
    }

    @Test
    void verifySimpleSubformSection() {
        Form form = getForm("template1.tmp");
        form.set("varname", "varvalue")
            .add("formname")
            .set("varname", "different varvalue")
            .set("varname2", "varvalue2");
        assertEquals("variable section 'varvalue'\n" +
                     "same variable section 'different varvalue'\n" +
                     "different variable section 'varvalue2'\n" +
                     "between ends\n" +
                     "end of text\n", form.render());
    }

    @Test
    void verifyNestedSubformSection() {
        Form form = getForm("template2.tmp");
        Form A0 = form.add("formA");
        Form A0B0 = A0.add("formB");
        Form A0B1 = A0.add("formB");

        Form A1 = form.add("formA");
        Form A1B0 = A1.add("formB");
        assertEquals("body A\n" +
                     "body B\n" +
                     "body B\n" +
                     "body A\n" +
                     "body B\n",
                     form.render());
    }

    @Test
    void verifyVariableReferences() {
        Form form = getForm("template3.tmp");
        form.set("varname", "varvalue")
            .set("innerVarSetAtTop", "val2");
        form.add("l");
        form.add("l")
            .set("varname", "varvalue2");
        assertEquals("varvalue\n" +
                     "varvalue\n" +
                     "inner varvalue\n" +
                     "val2\n" +
                     "inner varvalue2\n" +
                     "val2\n",
                     form.render());
    }

    @Test
    void verifyNewlineRemoval() {
        Form form = makeForm("a%{form a}\n" +
                             "b%{end}\n" +
                             "c%{form c-}\n" +
                             "d%{end-}\n" +
                             "e\n");
        form.add("a");
        form.add("c");

        assertEquals("a\n" +
                     "b\n" +
                     "cde\n",
                     form.render());
    }

    @Test
    void verifyIfSection() {
        Template template = Template.from("Hello%{if cond} world%{end}!");
        assertEquals("Hello world!", template.instantiate().set("cond", true).render());
        assertEquals("Hello!", template.instantiate().set("cond", false).render());
    }

    @Test
    void verifyComplexIfSection() {
        Template template = Template.from("%{if cond-}\n" +
                                          "var: %{=varname}\n" +
                                          "if: %{if !inner}inner is false%{end}\n" +
                                          "subform: %{form formname}subform%{end}\n" +
                                          "%{end-}\n");

        assertEquals("", template.instantiate().set("cond", false).render());

        assertEquals("var: varvalue\n" +
                     "if: \n" +
                     "subform: \n",
                     template.instantiate()
                             .set("cond", true)
                             .set("varname", "varvalue")
                             .set("inner", true)
                             .render());

        Form form = template.instantiate()
                            .set("cond", true)
                            .set("varname", "varvalue")
                            .set("inner", false);
        form.add("formname");

        assertEquals("var: varvalue\n" +
                     "if: inner is false\n" +
                     "subform: subform\n", form.render());
    }

    private Form getForm(String filename) {
        return TemplateFile.read(Path.of("src/test/resources/" + filename)).instantiate();
    }

    private Form makeForm(String templateText) {
        return Template.from(templateText).instantiate();
    }
}