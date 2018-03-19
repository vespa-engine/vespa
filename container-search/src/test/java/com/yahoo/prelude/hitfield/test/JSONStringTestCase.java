// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield.test;

import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.data.access.simple.Value;
import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Type;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Cursor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the JSONString XML rendering.
 *
 * TODO: Add correct answers. These are not added because this code was checked in before sync with system test
 *
 * @author Steinar Knutsen
 */
public class JSONStringTestCase {

    @Test
    public void testWeightedSet() {
        String json = "[[{\"as1\":[\"per\",\"paal\"],\"l1\":1122334455667788997,\"d1\":87.790001,\"i1\":7,\"al1\":[11223344556677881,11223344556677883],\"s1\":\"string\\n"
                + "espa\u00F1a\\n"
                + "wssf1.s1[0]\"},10],"
                + "[{\"as1\":[\"per\",\"paal\"],\"l1\":1122334455667788998,\"d1\":88.790001,\"i1\":8,\"al1\":[11223344556677881,11223344556677883],\"s1\":\"string\\n"
                + "espa\u00F1a wssf1.s1[1]\"},20]]";
        JSONString js = new JSONString(json);
        String o1 = "      <item weight=\"10\">\n";
        String[] o1Fields = {
                "        <struct-field name=\"l1\">1122334455667788997</struct-field>\n",
                "        <struct-field name=\"al1\">\n"
                        + "          <item>11223344556677881</item>\n"
                        + "          <item>11223344556677883</item>\n"
                        + "        </struct-field>\n",
                "        <struct-field name=\"i1\">7</struct-field>\n",
                "        <struct-field name=\"d1\">87.790001</struct-field>\n",
                "        <struct-field name=\"as1\">\n"
                        + "          <item>per</item>\n"
                        + "          <item>paal</item>\n"
                        + "        </struct-field>\n",
                "        <struct-field name=\"s1\">string\n" + "españa\n"
                        + "wssf1.s1[0]</struct-field>\n" };
        String o2 = "      <item weight=\"20\">\n";
        String[] o2Fields = {
                "        <struct-field name=\"l1\">1122334455667788998</struct-field>\n",
                "        <struct-field name=\"al1\">\n"
                        + "          <item>11223344556677881</item>\n"
                        + "          <item>11223344556677883</item>\n"
                        + "        </struct-field>\n",
                "        <struct-field name=\"i1\">8</struct-field>\n",
                "        <struct-field name=\"d1\">88.790001</struct-field>\n",
                "        <struct-field name=\"as1\">\n"
                        + "          <item>per</item>\n"
                        + "          <item>paal</item>\n"
                        + "        </struct-field>\n",
                "        <struct-field name=\"s1\">string\n"
                        + "españa wssf1.s1[1]</struct-field>\n" };
        String rendered = js.toString();
        int o1Offset = rendered.indexOf(o1);
        assertTrue(-1 < o1Offset);
        int o2Offset = rendered.indexOf(o2);
        assertTrue(-1 < o2Offset);

        checkSubstrings(o1Fields, rendered.substring(o1Offset, o2Offset));
        checkSubstrings(o2Fields, rendered, o2Offset);

    }

    @Test
    public void testWeightedSetFromInspector() {
        Value.ArrayValue top = new Value.ArrayValue();
        top.add(new Value.ArrayValue()
                .add(new Value.ObjectValue()
                     .put("d1", new Value.DoubleValue(87.790001))
                     .put("s1", new Value.StringValue("string\n" + "espa\u00F1a\n" + "wssf1.s1[0]"))
                     .put("al1", new Value.ArrayValue()
                          .add(new Value.LongValue(11223344556677881L))
                          .add(new Value.LongValue(11223344556677883L)))
                     .put("l1", new Value.LongValue(1122334455667788997L))
                     .put("as1", new Value.ArrayValue()
                          .add(new Value.StringValue("per"))
                          .add(new Value.StringValue("paal")))
                     .put("i1", new Value.LongValue(7)))
                .add(new Value.LongValue(10)))
            .add(new Value.ArrayValue()
                 .add(new Value.ObjectValue()
                      .put("d1", new Value.DoubleValue(88.790001))
                      .put("s1", new Value.StringValue("string\n" + "espa\u00F1a wssf1.s1[1]"))
                      .put("al1", new Value.ArrayValue()
                           .add(new Value.LongValue(11223344556677881L))
                           .add(new Value.LongValue(11223344556677883L)))
                      .put("l1", new Value.LongValue(1122334455667788998L))
                      .put("as1", new Value.ArrayValue()
                           .add(new Value.StringValue("per"))
                           .add(new Value.StringValue("paal")))
                      .put("i1", new Value.LongValue(8)))
                 .add(new Value.LongValue(20)));

        JSONString js = new JSONString(top);
        String correct = "\n"
            + "      <item weight=\"10\">\n"
            + "        <struct-field name=\"d1\">87.790001</struct-field>\n"
            + "        <struct-field name=\"s1\">string\n"
            + "espa\u00F1a\n"
            + "wssf1.s1[0]</struct-field>\n"
            + "        <struct-field name=\"al1\">\n"
            + "          <item>11223344556677881</item>\n"
            + "          <item>11223344556677883</item>\n"
            + "        </struct-field>\n"
            + "        <struct-field name=\"l1\">1122334455667788997</struct-field>\n"
            + "        <struct-field name=\"as1\">\n"
            + "          <item>per</item>\n"
            + "          <item>paal</item>\n"
            + "        </struct-field>\n"
            + "        <struct-field name=\"i1\">7</struct-field>\n"
            + "      </item>\n"
            + "      <item weight=\"20\">\n"
            + "        <struct-field name=\"d1\">88.790001</struct-field>\n"
            + "        <struct-field name=\"s1\">string\n"
            + "espa\u00F1a wssf1.s1[1]</struct-field>\n"
            + "        <struct-field name=\"al1\">\n"
            + "          <item>11223344556677881</item>\n"
            + "          <item>11223344556677883</item>\n"
            + "        </struct-field>\n"
            + "        <struct-field name=\"l1\">1122334455667788998</struct-field>\n"
            + "        <struct-field name=\"as1\">\n"
            + "          <item>per</item>\n"
            + "          <item>paal</item>\n"
            + "        </struct-field>\n"
            + "        <struct-field name=\"i1\">8</struct-field>\n"
            + "      </item>\n"
            + "    ";
        assertEquals(correct, js.renderFromInspector());

        top = new Value.ArrayValue();
        top.add(new Value.ArrayValue()
                .add(new Value.StringValue("s1"))
                .add(new Value.LongValue(10)))
            .add(new Value.ArrayValue()
                 .add(new Value.StringValue("s2"))
                 .add(new Value.LongValue(20)));
        js = new JSONString(top);
        correct = "\n" +
                  "      <item weight=\"10\">s1</item>\n" +
                  "      <item weight=\"20\">s2</item>\n" +
                  "    ";
        assertEquals(correct, js.renderFromInspector());
    }

    @Test
    public void testStruct() {
        {
            String json = "{\"as1\":[\"per\",\"paal\"],\"l1\":1122334455667788991,\"d1\":81.790001,\"i1\":1,\"al1\":[11223344556677881,11223344556677883],\"s1\":\"string\\n"
                    + "espa\u00F1a ssf1.s1\"}";
            JSONString js = new JSONString(json);
            String[] renderedFields = {
                    "      <struct-field name=\"l1\">1122334455667788991</struct-field>\n",
                    "      <struct-field name=\"al1\">\n"
                            + "        <item>11223344556677881</item>\n"
                            + "        <item>11223344556677883</item>\n"
                            + "      </struct-field>\n",
                    "      <struct-field name=\"i1\">1</struct-field>\n",
                    "      <struct-field name=\"d1\">81.790001</struct-field>\n",
                    "      <struct-field name=\"as1\">\n"
                            + "        <item>per</item>\n"
                            + "        <item>paal</item>\n"
                            + "      </struct-field>\n",
                    "      <struct-field name=\"s1\">string\n"
                            + "españa ssf1.s1</struct-field>\n" };
            String rendered = js.toString();
            checkSubstrings(renderedFields, rendered);
        }
        {
            Value.ObjectValue top = new Value.ObjectValue();
            top.put("d1", new Value.DoubleValue(81.790001))
                    .put("s1",
                            new Value.StringValue("string\nespa\u00F1a ssf1.s1"))
                    .put("al1",
                            new Value.ArrayValue()
                                    .add(new Value.LongValue(11223344556677881L))
                                    .add(new Value.LongValue(11223344556677883L)))
                    .put("l1", new Value.LongValue(1122334455667788991L))
                    .put("as1",
                            new Value.ArrayValue().add(
                                    new Value.StringValue("per")).add(
                                    new Value.StringValue("paal")))
                    .put("i1", new Value.LongValue(1));
            JSONString js = new JSONString(top);

            String[] renderedFields = {
                    "      <struct-field name=\"d1\">81.790001</struct-field>\n",
                    "      <struct-field name=\"s1\">string\n"
                            + "españa ssf1.s1</struct-field>\n",
                    "      <struct-field name=\"al1\">\n"
                            + "        <item>11223344556677881</item>\n"
                            + "        <item>11223344556677883</item>\n"
                            + "      </struct-field>\n",
                    "      <struct-field name=\"l1\">1122334455667788991</struct-field>\n",
                    "      <struct-field name=\"as1\">\n"
                            + "        <item>per</item>\n"
                            + "        <item>paal</item>\n"
                            + "      </struct-field>\n",
                    "      <struct-field name=\"i1\">1</struct-field>\n" };

            String rendered = js.renderFromInspector();
            checkSubstrings(renderedFields, rendered);
        }
        {
            String json = "{\"as1\":[\"per\",\"paal\"],\"d1\":84.790001,\"i1\":4,\"al1\":[11223344556677881,11223344556677883]}";
            JSONString js = new JSONString(json);
            String[] renderedFields = {
                    "      <struct-field name=\"al1\">\n"
                            + "        <item>11223344556677881</item>\n"
                            + "        <item>11223344556677883</item>\n"
                            + "      </struct-field>\n",
                    "      <struct-field name=\"i1\">4</struct-field>\n",
                    "      <struct-field name=\"d1\">84.790001</struct-field>\n",
                    "      <struct-field name=\"as1\">\n"
                            + "        <item>per</item>\n"
                            + "        <item>paal</item>\n"
                            + "      </struct-field>\n    " };
            String rendered = js.toString();

            checkSubstrings(renderedFields, rendered);
        }
        {
            Value.ObjectValue top = new Value.ObjectValue();
            top.put("d1", new Value.DoubleValue(84.790001))
                    .put("al1",
                            new Value.ArrayValue()
                                    .add(new Value.LongValue(11223344556677881L))
                                    .add(new Value.LongValue(11223344556677883L)))
                    .put("as1",
                            new Value.ArrayValue().add(
                                    new Value.StringValue("per")).add(
                                    new Value.StringValue("paal")))
                    .put("i1", new Value.LongValue(4));
            JSONString js = new JSONString(top);

            String[] renderedFields = {
                    "      <struct-field name=\"d1\">84.790001</struct-field>\n",
                    "      <struct-field name=\"al1\">\n"
                            + "        <item>11223344556677881</item>\n"
                            + "        <item>11223344556677883</item>\n"
                            + "      </struct-field>\n",
                    "      <struct-field name=\"as1\">\n"
                            + "        <item>per</item>\n"
                            + "        <item>paal</item>\n"
                            + "      </struct-field>\n",
                    "      <struct-field name=\"i1\">4</struct-field>\n    " };

            String rendered = js.renderFromInspector();
            checkSubstrings(renderedFields, rendered);

        }
        {
            String json = "{\"s2\":\"string espa\u00F1a\\n"
                    + "ssf5.s2\",\"nss1\":{\"as1\":[\"per\",\"paal\"],\"l1\":1122334455667788995,\"d1\":85.790001,\"i1\":5,\"al1\":[11223344556677881,11223344556677883],\"s1\":\"string\\n"
                    + "espa\u00F1a ssf5.nss1.s1\"}}";
            JSONString js = new JSONString(json);
            String[] renderedFields = {
                    "      <struct-field name=\"nss1\">\n",
                    "      <struct-field name=\"s1\">string\n"
                            + "españa ssf5.nss1.s1</struct-field>\n",
                    "      <struct-field name=\"s2\">string españa\n"
                            + "ssf5.s2</struct-field>\n    " };
            String nss1Fields[] = {
                    "       <struct-field name=\"l1\">1122334455667788995</struct-field>\n",
                    "        <struct-field name=\"al1\">\n"
                            + "          <item>11223344556677881</item>\n"
                            + "          <item>11223344556677883</item>\n"
                            + "        </struct-field>\n",
                    "        <struct-field name=\"i1\">5</struct-field>\n",
                    "        <struct-field name=\"d1\">85.790001</struct-field>\n",
                    "        <struct-field name=\"as1\">\n"
                            + "          <item>per</item>\n"
                            + "          <item>paal</item>\n"
                            + "        </struct-field>\n" };

            String rendered = js.toString();
            checkSubstrings(renderedFields, rendered);
            int nss1Offset = rendered.indexOf(renderedFields[0])
                    + renderedFields[0].length();
            checkSubstrings(nss1Fields, rendered, nss1Offset);
        }
        {
            Value.ObjectValue top = new Value.ObjectValue();
            top.put("s2", "string espa\u00F1a\nssf5.s2").put(
                    "nss1",
                    new Value.ObjectValue()
                            .put("d1", new Value.DoubleValue(85.790001))
                            .put("s1", "string\nespa\u00F1a ssf5.nss1.s1")
                            .put("al1",
                                    new Value.ArrayValue().add(
                                            new Value.LongValue(
                                                    11223344556677881L)).add(
                                            new Value.LongValue(
                                                    11223344556677883L)))
                            .put("l1", 1122334455667788995L)
                            .put("as1",
                                    new Value.ArrayValue().add(
                                            new Value.StringValue("per")).add(
                                            new Value.StringValue("paal")))
                            .put("i1", 5));
            JSONString js = new JSONString(top);

            String f1 = "      <struct-field name=\"s2\">string españa\n"
                    + "ssf5.s2</struct-field>";
            String f2 = "      <struct-field name=\"nss1\">\n";
            String f2_1 = "        <struct-field name=\"d1\">85.790001</struct-field>\n";
            String f2_2 = "        <struct-field name=\"s1\">string\n"
                    + "españa ssf5.nss1.s1</struct-field>\n";
            String f2_3 = "        <struct-field name=\"al1\">\n"
                    + "          <item>11223344556677881</item>\n"
                    + "          <item>11223344556677883</item>\n"
                    + "        </struct-field>\n";
            String f2_4 = "        <struct-field name=\"l1\">1122334455667788995</struct-field>\n";
            String f2_5 = "        <struct-field name=\"as1\">\n"
                    + "          <item>per</item>\n"
                    + "          <item>paal</item>\n"
                    + "        </struct-field>\n";
            String f2_6 = "        <struct-field name=\"i1\">5</struct-field>\n";
            String f2_end = "      </struct-field>\n    ";
            String rendered = js.renderFromInspector();

            assertTrue(-1 < rendered.indexOf(f1));
            int offsetF2;
            assertTrue(-1 < (offsetF2 = rendered.indexOf(f2)));
            offsetF2 += f2.length();
            assertTrue(-1 < rendered.indexOf(f2_1, offsetF2));
            assertTrue(-1 < rendered.indexOf(f2_2, offsetF2));
            assertTrue(-1 < rendered.indexOf(f2_3, offsetF2));
            assertTrue(-1 < rendered.indexOf(f2_4, offsetF2));
            assertTrue(-1 < rendered.indexOf(f2_5, offsetF2));
            assertTrue(-1 < rendered.indexOf(f2_6, offsetF2));
            final int expectedEnd = offsetF2 + f2_1.length() + f2_2.length() + f2_3.length()
                    + f2_4.length() + f2_5.length() + f2_6.length();
            assertEquals(
                    expectedEnd,
                    rendered.indexOf(
                            f2_end,
                            expectedEnd));
        }
        {
            String json = "{\"s2\":\"string espa\u00F1a\\n"
                    + "ssf8.s2\",\"nss1\":{\"as1\":[\"per\",\"paal\"],\"d1\":88.790001,\"i1\":8,\"al1\":[11223344556677881,11223344556677883]}}";
            JSONString js = new JSONString(json);

            String[] renderedFields = {
                    "      <struct-field name=\"nss1\">\n",
                    "      <struct-field name=\"s2\">string españa\n"
                            + "ssf8.s2</struct-field>\n    " };
            String nss1Fields[] = {
                    "        <struct-field name=\"al1\">\n"
                            + "          <item>11223344556677881</item>\n"
                            + "          <item>11223344556677883</item>\n"
                            + "        </struct-field>\n",
                    "        <struct-field name=\"i1\">8</struct-field>\n",
                    "        <struct-field name=\"d1\">88.790001</struct-field>\n",
                    "        <struct-field name=\"as1\">\n"
                            + "          <item>per</item>\n"
                            + "          <item>paal</item>\n"
                            + "        </struct-field>\n" };

            String rendered = js.toString();
            checkSubstrings(renderedFields, rendered);
            int nss1Offset = rendered.indexOf(renderedFields[0])
                    + renderedFields[0].length();
            checkSubstrings(nss1Fields, rendered, nss1Offset);

        }
        {
            Value.ObjectValue top = new Value.ObjectValue();
            top.put("s2", "string espa\u00F1a\nssf8.s2").put(
                    "nss1",
                    new Value.ObjectValue()
                            .put("d1", new Value.DoubleValue(88.790001))
                            .put("al1",
                                    new Value.ArrayValue().add(
                                            new Value.LongValue(
                                                    11223344556677881L)).add(
                                            new Value.LongValue(
                                                    11223344556677883L)))
                            .put("as1",
                                    new Value.ArrayValue().add(
                                            new Value.StringValue("per")).add(
                                            new Value.StringValue("paal")))
                            .put("i1", 8));
            JSONString js = new JSONString(top);
            String rendered = js.renderFromInspector();
            String[] renderedFields = {
                    "      <struct-field name=\"nss1\">\n",
                    "      <struct-field name=\"s2\">string españa\n"
                            + "ssf8.s2</struct-field>\n    " };
            String nss1Fields[] = {
                    "        <struct-field name=\"al1\">\n"
                            + "          <item>11223344556677881</item>\n"
                            + "          <item>11223344556677883</item>\n"
                            + "        </struct-field>\n",
                    "        <struct-field name=\"i1\">8</struct-field>\n",
                    "        <struct-field name=\"d1\">88.790001</struct-field>\n",
                    "        <struct-field name=\"as1\">\n"
                            + "          <item>per</item>\n"
                            + "          <item>paal</item>\n"
                            + "        </struct-field>\n" };

            checkSubstrings(renderedFields, rendered);
            int nss1Offset = rendered.indexOf(renderedFields[0])
                    + renderedFields[0].length();
            checkSubstrings(nss1Fields, rendered, nss1Offset);

        }
    }

    @Test
    public void testMap() {
        String json = "[{\"key\":\"k1\",\"value\":\"v1\"},{\"key\":\"k2\",\"value\":\"v2\"}]";
        JSONString js = new JSONString(json);
        String correct = "\n"
                         + "      <item><key>k1</key><value>v1</value></item>\n"
                         + "      <item><key>k2</key><value>v2</value></item>\n    ";
        assertEquals(correct,js.toString());

        Inspector top = new Value.ArrayValue()
                        .add(new Value.ObjectValue()
                             .put("key", "k1")
                             .put("value", "v1"))
                        .add(new Value.ObjectValue()
                             .put("key", "k2")
                             .put("value", "v2"));
        js = new JSONString(top);
        assertEquals(correct, js.renderFromInspector());
    }

    @Test
    public void testWithData() {
        byte[] d1 = { (byte)0x41, (byte)0x42, (byte)0x43 };
        byte[] d2 = { (byte)0x00, (byte)0x01, (byte)0x00, (byte)0x02 };
        byte[] d3 = { (byte)0x12, (byte)0x34 };
        byte[] d4 = { (byte)0xff, (byte)0x80, (byte)0x7f };
        Inspector top = new Value.ObjectValue()
                        .put("simple", new Value.DataValue(d1))
                        .put("array", new Value.ArrayValue()
                             .add(new Value.DataValue(d2))
                             .add(new Value.DataValue(d3))
                             .add(new Value.DataValue(d4)));
        JSONString js = new JSONString(top);
        String correct = "\n"
                         + "      <struct-field name=\"simple\">"
                         + "<data length=\"3\" encoding=\"hex\">414243</data>"
                         + "</struct-field>\n"
                         + "      <struct-field name=\"array\">\n"
                         + "        <item>"
                         + "<data length=\"4\" encoding=\"hex\">00010002</data>"
                         + "</item>\n"
                         + "        <item>"
                         + "<data length=\"2\" encoding=\"hex\">1234</data>"
                         + "</item>\n"
                         + "        <item>"
                         + "<data length=\"3\" encoding=\"hex\">FF807F</data>"
                         + "</item>\n"
                         + "      </struct-field>\n    ";
        assertEquals(correct, js.renderFromInspector());
    }

    @Test
    public void testArrayOfArray() {
        String json = "[[\"c1\", 0], [\"c2\", 2, 3], [\"c3\", 3, 4, 5], [\"c4\", 4,5,6,7]]";
        JSONString js = new JSONString(json);
        Inspector outer = js.inspect();
        assertEquals(4, outer.entryCount());

        assertEquals(2, outer.entry(0).entryCount());
        assertEquals("c1", outer.entry(0).entry(0).asString());
        assertEquals(0, outer.entry(0).entry(1).asLong());

        assertEquals(3, outer.entry(1).entryCount());
        assertEquals("c2", outer.entry(1).entry(0).asString());
        assertEquals(2, outer.entry(1).entry(1).asLong());
        assertEquals(3, outer.entry(1).entry(2).asLong());

        assertEquals(4, outer.entry(2).entryCount());
        assertEquals("c3", outer.entry(2).entry(0).asString());
        assertEquals(3, outer.entry(2).entry(1).asLong());
        assertEquals(4, outer.entry(2).entry(2).asLong());
        assertEquals(5, outer.entry(2).entry(3).asLong());

        assertEquals(5, outer.entry(3).entryCount());
        assertEquals("c4", outer.entry(3).entry(0).asString());
        assertEquals(4, outer.entry(3).entry(1).asLong());
        assertEquals(5, outer.entry(3).entry(2).asLong());
        assertEquals(6, outer.entry(3).entry(3).asLong());
        assertEquals(7, outer.entry(3).entry(4).asLong());
    }

    @Test
    public void testSimpleArrays() {
        String json = "[1, 2, 3]";
        JSONString js = new JSONString(json);
        String correct = "\n"
                         + "      <item>1</item>\n"
                         + "      <item>2</item>\n"
                         + "      <item>3</item>\n    ";
        assertEquals(correct, js.toString());

        Inspector top = new Value.ArrayValue()
                        .add(1).add(2).add(3);
        js = new JSONString(top);
        assertEquals(correct, js.renderFromInspector());

        json = "[1.0, 2.0, 3.0]";
        js = new JSONString(json);
        correct = "\n"
                  + "      <item>1.0</item>\n"
                  + "      <item>2.0</item>\n"
                  + "      <item>3.0</item>\n    ";
        assertEquals(correct, js.toString());
        top = new Value.ArrayValue()
              .add(1.0).add(2.0).add(3.0);
        js = new JSONString(top);
        assertEquals(correct, js.renderFromInspector());

        json = "[\"a\", \"b\", \"c\"]";
        correct = "\n"
                  + "      <item>a</item>\n"
                  + "      <item>b</item>\n"
                  + "      <item>c</item>\n    ";
        js = new JSONString(json);
        assertEquals(correct, js.toString());

        top = new Value.ArrayValue()
              .add("a").add("b").add("c");
        js = new JSONString(top);
        assertEquals(correct, js.renderFromInspector());
    }

    @Test
    public void testArrayOfStruct() {
        String json = "[{\"as1\":[\"per\",\"paal\"],"
                + "\"l1\":1122334455667788994,\"d1\":74.790001,"
                + "\"i1\":14,\"al1\":[11223344556677881,11223344556677883],\"s1\":\"string\\n"
                + "espa\u00F1a\\n"
                + "asf1[0].s1\"},{\"as1\":[\"per\",\"paal\"],\"l1\":1122334455667788995,\"d1\":75.790001,\"i1\":15,\"al1\":[11223344556677881,11223344556677883],\"s1\":\"string\\n"
                + "espa\u00F1a asf1[1].s1\"}]";
        JSONString js = new JSONString(json);
        String[] o1Fields = {
                "\n      <item>\n",
                "        <struct-field name=\"l1\">1122334455667788994</struct-field>\n",
                "        <struct-field name=\"al1\">\n"
                        + "          <item>11223344556677881</item>\n"
                        + "          <item>11223344556677883</item>\n"
                        + "        </struct-field>\n",
                "        <struct-field name=\"i1\">14</struct-field>\n",
                "        <struct-field name=\"d1\">74.790001</struct-field>\n",
                "        <struct-field name=\"as1\">\n"
                        + "          <item>per</item>\n"
                        + "          <item>paal</item>\n"
                        + "        </struct-field>\n",
                "        <struct-field name=\"s1\">string\n" + "españa\n"
                        + "asf1[0].s1</struct-field>\n" };
        String separator = "      </item>\n" + "      <item>\n";
        String[] o2Fields = {
                "        <struct-field name=\"l1\">1122334455667788995</struct-field>\n",
                "        <struct-field name=\"al1\">\n"
                        + "          <item>11223344556677881</item>\n"
                        + "          <item>11223344556677883</item>\n"
                        + "        </struct-field>\n",
                "        <struct-field name=\"i1\">15</struct-field>\n",
                "        <struct-field name=\"d1\">75.790001</struct-field>\n",
                "        <struct-field name=\"as1\">\n"
                        + "          <item>per</item>\n"
                        + "          <item>paal</item>\n"
                        + "        </struct-field>\n",
                "        <struct-field name=\"s1\">string\n"
                        + "españa asf1[1].s1</struct-field>\n" };
        String rendered = js.toString();

        int o2Offset = rendered.indexOf(separator);
        assertTrue(-1 < o2Offset);

        checkSubstrings(o1Fields, rendered);
        checkSubstrings(o2Fields, rendered, o2Offset);

        Inspector top = new Value.ArrayValue().add(
                new Value.ObjectValue()
                        .put("d1", 74.790001)
                        .put("s1", "string\n" + "espa\u00F1a\n" + "asf1[0].s1")
                        .put("al1",
                                new Value.ArrayValue().add(11223344556677881L)
                                        .add(11223344556677883L))
                        .put("l1", 1122334455667788994L)
                        .put("as1",
                                new Value.ArrayValue().add("per").add("paal"))
                        .put("i1", 14)).add(
                new Value.ObjectValue()
                        .put("d1", 75.790001)
                        .put("s1", "string\n" + "espa\u00F1a asf1[1].s1")
                        .put("al1",
                                new Value.ArrayValue().add(11223344556677881L)
                                        .add(11223344556677883L))
                        .put("l1", 1122334455667788995L)
                        .put("as1",
                                new Value.ArrayValue().add(
                                        new Value.StringValue("per")).add(
                                        new Value.StringValue("paal")))
                        .put("i1", 15));
        js = new JSONString(top);

        rendered = js.renderFromInspector();

        o2Offset = rendered.indexOf(separator);
        assertTrue(-1 < o2Offset);

        checkSubstrings(o1Fields, rendered.substring(0, o2Offset));
        checkSubstrings(o2Fields, rendered, o2Offset);
    }

    private void checkSubstrings(String[] fields, String haystack) {
        for (String field : fields) {
            assertTrue(-1 < haystack.indexOf(field));
        }
    }

    private void checkSubstrings(String[] fields, String haystack, int offset) {
        for (String field : fields) {
            assertTrue(-1 < haystack.indexOf(field, offset));
        }
    }

/*** here is some json for you

     [{"asf":"here is 1st simple string field",
     "map":[{"key":"one key string","value":["one value string","embedded array"]},
     {"key":"two key string","value":["two value string","embedded array"]}],
     "sf2":"here is 2nd simple string field"},
     {"asf":"here is 3rd simple string field",
     "map":[{"key":"three key string","value":["three value string","embedded array"]},
     {"key":"four key string","value":["four value string","embedded array"]}],
     "sf2":"here is 4th simple string field"},
     ]

***/

/*** and here is some corresponding XML

     <item>
     <struct-field name="asf">here is 1st simple string field</struct-field>
     <struct-field name="map">
     <item><key>one key string</key><value>
     <item>one value string</item>
     <item>embedded array</item>
     </value></item>
     <item><key>two key string</key><value>
     <item>two value string</item>
     <item>embedded array</item>
     </value></item>
     </struct-field>
     <struct-field name="sf2">here is 2nd simple string field</struct-field>
     </item>
     <item>
     <struct-field name="asf">here is 3rd simple string field</struct-field>
     <struct-field name="map">
     <item><key>three key string</key><value>
     <item>three value string</item>
     <item>embedded array</item>
     </value></item>
     <item><key>four key string</key><value>
     <item>four value string</item>
     <item>embedded array</item>
     </value></item>
     </struct-field>
     <struct-field name="sf2">here is 4th simple string field</struct-field>
     </item>

***/

    @Test
    public void testArrayOfStructWithMap() {
        String json = "[{\"asf\":\"here is 1st simple string field\",\"map\":[{\"key\":\"one key string\",\"value\":[\"one value string\",\"embedded array\"]},{\"key\":\"two key string\",\"value\":[\"two value string\",\"embedded array\"]}],\"sf2\":\"here is 2nd simple string field\"},{\"asf\":\"here is 3rd simple string field\",\"map\":[{\"key\":\"three key string\",\"value\":[\"three value string\",\"embedded array\"]},{\"key\":\"four key string\",\"value\":[\"four value string\",\"embedded array\"]}],\"sf2\":\"here is 4th simple string field\"}]";


        JSONString js = new JSONString(json);
        String correct = "\n"
                         + "      <item>\n"
                         + "        <struct-field name=\"asf\">here is 1st simple string field</struct-field>\n"
                         + "        <struct-field name=\"map\">\n"
                         + "          <item><key>one key string</key><value>\n"
                         + "            <item>one value string</item>\n"
                         + "            <item>embedded array</item>\n"
                         + "          </value></item>\n"
                         + "          <item><key>two key string</key><value>\n"
                         + "            <item>two value string</item>\n"
                         + "            <item>embedded array</item>\n"
                         + "          </value></item>\n"
                         + "        </struct-field>\n"
                         + "        <struct-field name=\"sf2\">here is 2nd simple string field</struct-field>\n"
                         + "      </item>\n"
                         + "      <item>\n"
                         + "        <struct-field name=\"asf\">here is 3rd simple string field</struct-field>\n"
                         + "        <struct-field name=\"map\">\n"
                         + "          <item><key>three key string</key><value>\n"
                         + "            <item>three value string</item>\n"
                         + "            <item>embedded array</item>\n"
                         + "          </value></item>\n"
                         + "          <item><key>four key string</key><value>\n"
                         + "            <item>four value string</item>\n"
                         + "            <item>embedded array</item>\n"
                         + "          </value></item>\n"
                         + "        </struct-field>\n"
                         + "        <struct-field name=\"sf2\">here is 4th simple string field</struct-field>\n"
                         + "      </item>\n"
                         + "    ";
        assertEquals(correct, js.toString());

        Inspector top = new Value.ArrayValue()
                        .add(new Value.ObjectValue()
                             .put("asf", "here is 1st simple string field")
                             .put("map", new Value.ArrayValue()
                                  .add(new Value.ObjectValue()
                                          .put("key", "one key string")
                                          .put("value", new Value.ArrayValue()
                                                  .add("one value string")
                                                  .add("embedded array")))
                                  .add(new Value.ObjectValue()
                                          .put("key", "two key string")
                                          .put("value", new Value.ArrayValue()
                                                  .add("two value string")
                                                  .add("embedded array"))))
                             .put("sf2", "here is 2nd simple string field"))
                        .add(new Value.ObjectValue()
                             .put("asf", "here is 3rd simple string field")
                             .put("map", new Value.ArrayValue()
                                  .add(new Value.ObjectValue()
                                          .put("key", "three key string")
                                          .put("value", new Value.ArrayValue()
                                                  .add("three value string")
                                                  .add("embedded array")))
                                  .add(new Value.ObjectValue()
                                          .put("key", "four key string")
                                          .put("value", new Value.ArrayValue()
                                                  .add("four value string")
                                                  .add("embedded array"))))
                             .put("sf2", "here is 4th simple string field"));
        js = new JSONString(top);
        assertEquals(correct, js.renderFromInspector());
    }

    @Test
    public void testArrayOfStructWithEmptyMap() {
        String json = "[{\"asf\":\"here is 1st simple string field\",\"map\":[],\"sf2\":\"here is 2nd simple string field\"},{\"asf\":\"here is 3rd simple string field\",\"map\":[],\"sf2\":\"here is 4th simple string field\"}]";


        JSONString js = new JSONString(json);
        String correct = "\n"
                         + "      <item>\n"
                         + "        <struct-field name=\"asf\">here is 1st simple string field</struct-field>\n"
                         + "        <struct-field name=\"map\"></struct-field>\n"
                         + "        <struct-field name=\"sf2\">here is 2nd simple string field</struct-field>\n"
                         + "      </item>\n"
                         + "      <item>\n"
                         + "        <struct-field name=\"asf\">here is 3rd simple string field</struct-field>\n"
                         + "        <struct-field name=\"map\"></struct-field>\n"
                         + "        <struct-field name=\"sf2\">here is 4th simple string field</struct-field>\n"
                         + "      </item>\n"
                         + "    ";
        assertEquals(correct, js.toString());

        Inspector top = new Value.ArrayValue()
                        .add(new Value.ObjectValue()
                             .put("asf", "here is 1st simple string field")
                             .put("map", new Value.ArrayValue())
                             .put("sf2", "here is 2nd simple string field"))
                        .add(new Value.ObjectValue()
                             .put("asf", "here is 3rd simple string field")
                             .put("map", new Value.ArrayValue())
                             .put("sf2", "here is 4th simple string field"));
        js = new JSONString(top);
        assertEquals(correct, js.renderFromInspector());

    }

    private Inspector getSlime1() {
        Slime slime = new Slime();
        slime.setNix();
        return new SlimeAdapter(slime.get());
    }
    private Inspector getSlime2() {
        Slime slime = new Slime();
        slime.setString("foo");
        return new SlimeAdapter(slime.get());
    }
    private Inspector getSlime3() {
        Slime slime = new Slime();
        slime.setLong(123);
        return new SlimeAdapter(slime.get());
    }
    private Inspector getSlime4() {
        Slime slime = new Slime();
        Cursor obj = slime.setObject();
        obj.setLong("foo", 1);
        return new SlimeAdapter(slime.get());
    }
    private Inspector getSlime5() {
        Slime slime = new Slime();
        Cursor arr = slime.setArray();
        arr.addLong(1);
        arr.addLong(2);
        arr.addLong(3);
        return new SlimeAdapter(slime.get());
    }

    @Test
    public void testInspectorToContentMapping() {
        String content1 = new JSONString(getSlime1()).getContent();
        String content2 = new JSONString(getSlime2()).getContent();
        String content3 = new JSONString(getSlime3()).getContent();
        String content4 = new JSONString(getSlime4()).getContent();
        String content5 = new JSONString(getSlime5()).getContent();
        assertEquals("", content1);
        assertEquals("foo", content2);
        assertEquals("123", content3);
        assertEquals("{\"foo\":1}", content4);
        assertEquals("[1,2,3]", content5);
    }

    @Test
    public void testContentToInspectorMapping() {
        Inspector value1 = new JSONString("").inspect();
        Inspector value2 = new JSONString("foo").inspect();
        Inspector value3 = new JSONString("\"foo\"").inspect();
        Inspector value4 = new JSONString("123").inspect();
        Inspector value5 = new JSONString("{\"foo\":1}").inspect();
        Inspector value6 = new JSONString("[1,2,3]").inspect();

        System.out.println("1: " + value1);
        System.out.println("2: " + value2);
        System.out.println("3: " + value3);
        System.out.println("4: " + value4);
        System.out.println("5: " + value5);
        System.out.println("6: " + value6);

        assertEquals(Type.STRING, value1.type());
        assertEquals("", value1.asString());

        assertEquals(value2.type(), Type.STRING);
        assertEquals("foo", value2.asString());

        assertEquals(value3.type(), Type.STRING);
        assertEquals("\"foo\"", value3.asString());

        assertEquals(value4.type(), Type.STRING);
        assertEquals("123", value4.asString());

        assertEquals(value5.type(), Type.OBJECT);
        assertEquals(1L, value5.field("foo").asLong());
        assertEquals("{\"foo\":1}", value5.toString());

        assertEquals(value6.type(), Type.ARRAY);
        assertEquals(1L, value6.entry(0).asLong());
        assertEquals(2L, value6.entry(1).asLong());
        assertEquals(3L, value6.entry(2).asLong());
        assertEquals("[1,2,3]", value6.toString());
    }

}
