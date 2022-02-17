// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        ListElement A0 = template.add("listA");
        ListElement A0B0 = A0.add("listB");
        ListElement A0B1 = A0.add("listB");

        ListElement A1 = template.add("listA");
        ListElement A1B0 = A1.add("listB");
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

    @Test
    void badTemplates() {
        assertException(BadTemplateException.class, "Unknown section 'zoo' at line 2 and column 6",
                        () -> Template.from("foo\nbar%{zoo}"));

        assertException(BadTemplateException.class, "Expected identifier at line 1 and column 4",
                        () -> Template.from("%{="));

        assertException(BadTemplateException.class, "Expected identifier at line 1 and column 4",
                        () -> Template.from("%{=&notatoken}"));

        assertException(BadTemplateException.class, "Expected identifier at line 1 and column 8",
                        () -> Template.from("%{list &notatoken}"));

        assertException(BadTemplateException.class, "Missing end directive for section started at line 1 and column 12",
                        () -> Template.from("%{list foo}missing end"));

        assertException(BadTemplateException.class, "Stray 'end' at line 1 and column 3",
                        () -> Template.from("%{end}stray end"));

        assertException(TemplateNameNotSetException.class, "Variable at line 1 and column 4 has not been set: notset",
                        () -> Template.from("%{=notset}").render());

        assertException(TemplateNameNotSetException.class, "Variable at line 1 and column 6 has not been set: cond",
                        () -> Template.from("%{if cond}%{end}").render());

        assertException(NotBooleanValueTemplateException.class, "cond was set to a non-boolean value: must be true or false",
                        () -> Template.from("%{if cond}%{end}").set("cond", 1).render());

        assertException(NoSuchNameTemplateException.class, "No such element 'listname' in the template section starting at " +
                                                           "line 1 and column 1, and ending at line 1 and column 4",
                        () -> Template.from("foo").add("listname"));

        assertException(NameAlreadyExistsTemplateException.class,
                        "The name 'a' of the list section at line 1 and column 16 is in conflict with the identically " +
                        "named list section at line 1 and column 1",
                        () -> Template.from("%{list a}%{end}%{list a}%{end}"));

        assertException(NameAlreadyExistsTemplateException.class,
                        "The name 'a' of the list section at line 1 and column 6 is in conflict with the identically " +
                        "named variable section at line 1 and column 1",
                        () -> Template.from("%{=a}%{list a}%{end}"));

        assertException(NameAlreadyExistsTemplateException.class,
                        "The name 'a' of the variable section at line 1 and column 16 is in conflict with the identically " +
                        "named list section at line 1 and column 1",
                        () -> Template.from("%{list a}%{end}%{=a}"));

        assertException(NameAlreadyExistsTemplateException.class,
                        "The name 'a' of the list section at line 1 and column 14 is in conflict with the identically " +
                        "named if section at line 1 and column 1",
                        () -> Template.from("%{if a}%{end}%{list a}%{end}"));

        assertException(NameAlreadyExistsTemplateException.class,
                        "The name 'a' of the if section at line 1 and column 16 is in conflict with the identically " +
                        "named list section at line 1 and column 1",
                        () -> Template.from("%{list a}%{end}%{if a}%{end}"));
    }

    private <T extends Throwable> void assertException(Class<T> class_, String message, Runnable runnable) {
        T exception = assertThrows(class_, runnable::run);
        assertEquals(message, exception.getMessage());
    }

    private Template getTemplate(String filename) {
        return Template.at(Path.of("src/test/resources/" + filename));
    }
}
