// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

import com.yahoo.data.access.simple.Value;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class XmlRendererTestCase {

    @Test
    public void testWeightedSet1() {
        Value.ArrayValue top = new Value.ArrayValue();
        top
            .add(new Value.ArrayValue()
                 .add(new Value.StringValue("per"))
                 .add(new Value.LongValue(10)))
            .add(new Value.ArrayValue()
                 .add(new Value.StringValue("paal"))
                 .add(new Value.LongValue(20)))
            .add(new Value.ArrayValue()
                 .add(new Value.StringValue("espen"))
                 .add(new Value.LongValue(30)));
        String rendered = XmlRenderer.render(new StringBuilder(), top).toString();
        String correct = "\n"
                         + "      <item weight=\"10\">per</item>\n"
                         + "      <item weight=\"20\">paal</item>\n"
                         + "      <item weight=\"30\">espen</item>\n"
                         + "    ";
        assertEquals(correct, rendered);
    }

    @Test
    public void testWeightedSet2() {
        Value.ObjectValue top = new Value.ObjectValue();
        top
            .put("foo", new Value.ArrayValue()
                 .add(new Value.ArrayValue()
                      .add(new Value.StringValue("per"))
                      .add(new Value.LongValue(10)))
                 .add(new Value.ArrayValue()
                      .add(new Value.StringValue("paal"))
                      .add(new Value.LongValue(20)))
                 .add(new Value.ArrayValue()
                      .add(new Value.StringValue("espen"))
                      .add(new Value.LongValue(30))))
            .put("bar", new Value.ArrayValue()
                 .add(new Value.ObjectValue()
                      .put("item",new Value.StringValue("per"))
                      .put("weight",new Value.LongValue(10)))
                 .add(new Value.ObjectValue()
                      .put("item",new Value.StringValue("paal"))
                      .put("weight",new Value.LongValue(20)))
                 .add(new Value.ObjectValue()
                      .put("weight",new Value.LongValue(30))
                      .put("item",new Value.StringValue("espen"))));
        String rendered = XmlRenderer.render(new StringBuilder(), top).toString();
        String correct = "\n"
                         + "      <struct-field name=\"foo\">\n"
                         + "        <item weight=\"10\">per</item>\n"
                         + "        <item weight=\"20\">paal</item>\n"
                         + "        <item weight=\"30\">espen</item>\n"
                         + "      </struct-field>\n"
                         + "      <struct-field name=\"bar\">\n"
                         + "        <item weight=\"10\">per</item>\n"
                         + "        <item weight=\"20\">paal</item>\n"
                         + "        <item weight=\"30\">espen</item>\n"
                         + "      </struct-field>\n"
                         + "    ";
        assertEquals(correct, rendered);
    }

}
