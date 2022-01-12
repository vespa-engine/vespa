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
        return TemplateFile.read(Path.of("src/test/resources/" + filename));
    }
}