// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

import com.yahoo.search.grouping.Continuation;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.Relevance;
import com.yahoo.text.Utf8;
import com.yahoo.text.XMLWriter;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class HitRendererTestCase {

    @Test
    public void requireThatGroupListsRenderAsExpected() {
        assertRender(new GroupList("foo"), "<grouplist label=\"foo\"></grouplist>\n");
        assertRender(new GroupList("b\u00e6z"), "<grouplist label=\"b\u00e6z\"></grouplist>\n");

        GroupList lst = new GroupList("foo");
        lst.continuations().put("bar.key", new MyContinuation("bar.val"));
        lst.continuations().put("baz.key", new MyContinuation("baz.val"));
        assertRender(lst, "<grouplist label=\"foo\">\n" +
                          "<continuation id=\"bar.key\">bar.val</continuation>\n" +
                          "<continuation id=\"baz.key\">baz.val</continuation>\n" +
                          "</grouplist>\n");
    }

    @Test
    public void requireThatGroupIdsRenderAsExpected() {
        assertRender(newGroup(new DoubleId(6.9)),
                     "<group relevance=\"1.0\">\n" +
                     "<id type=\"double\">6.9</id>\n" +
                     "</group>\n");
        assertRender(newGroup(new LongId(69L)),
                     "<group relevance=\"1.0\">\n" +
                     "<id type=\"long\">69</id>\n" +
                     "</group>\n");
        assertRender(newGroup(new NullId()),
                     "<group relevance=\"1.0\">\n" +
                     "<id type=\"null\"/>\n" +
                     "</group>\n");
        assertRender(newGroup(new RawId(Utf8.toBytes("foo"))),
                     "<group relevance=\"1.0\">\n" +
                     "<id type=\"raw\">[102, 111, 111]</id>\n" +
                     "</group>\n");
        assertRender(newGroup(new StringId("foo")),
                     "<group relevance=\"1.0\">\n" +
                     "<id type=\"string\">foo</id>\n" +
                     "</group>\n");
        assertRender(newGroup(new StringId("b\u00e6z")),
                     "<group relevance=\"1.0\">\n" +
                     "<id type=\"string\">b\u00e6z</id>\n" +
                     "</group>\n");
        assertRender(newGroup(new DoubleBucketId(6.9, 9.6)),
                     "<group relevance=\"1.0\">\n" +
                     "<id type=\"double_bucket\">\n<from>6.9</from>\n<to>9.6</to>\n</id>\n" +
                     "</group>\n");
        assertRender(newGroup(new LongBucketId(6L, 9L)),
                     "<group relevance=\"1.0\">\n" +
                     "<id type=\"long_bucket\">\n<from>6</from>\n<to>9</to>\n</id>\n" +
                     "</group>\n");
        assertRender(newGroup(new StringBucketId("bar", "baz")),
                     "<group relevance=\"1.0\">\n" +
                     "<id type=\"string_bucket\">\n<from>bar</from>\n<to>baz</to>\n</id>\n" +
                     "</group>\n");
        assertRender(newGroup(new StringBucketId("b\u00e6r", "b\u00e6z")),
                     "<group relevance=\"1.0\">\n" +
                     "<id type=\"string_bucket\">\n<from>b\u00e6r</from>\n<to>b\u00e6z</to>\n</id>\n" +
                     "</group>\n");
        assertRender(newGroup(new RawBucketId(Utf8.toBytes("bar"), Utf8.toBytes("baz"))),
                     "<group relevance=\"1.0\">\n" +
                     "<id type=\"raw_bucket\">\n<from>[98, 97, 114]</from>\n<to>[98, 97, 122]</to>\n</id>\n" +
                     "</group>\n");
    }

    @Test
    public void requireThatGroupsRenderAsExpected() {
        Group group = newGroup(new StringId("foo"));
        group.setField("foo", "bar");
        group.setField("baz", "cox");
        assertRender(group, "<group relevance=\"1.0\">\n" +
                            "<id type=\"string\">foo</id>\n" +
                            "<output label=\"foo\">bar</output>\n" +
                            "<output label=\"baz\">cox</output>\n" +
                            "</group>\n");

        group = newGroup(new StringId("foo"));
        group.setField("foo", "b\u00e6r");
        group.setField("b\u00e5z", "cox");
        assertRender(group, "<group relevance=\"1.0\">\n" +
                            "<id type=\"string\">foo</id>\n" +
                            "<output label=\"foo\">b\u00e6r</output>\n" +
                            "<output label=\"b\u00e5z\">cox</output>\n" +
                            "</group>\n");
    }

    @Test
    public void requireThatRootGroupsRenderAsExpected() {
        RootGroup group = new RootGroup(0, new MyContinuation("69"));
        group.setField("foo", "bar");
        group.setField("baz", "cox");
        assertRender(group, "<group relevance=\"1.0\">\n" +
                            "<id type=\"root\"/>\n" +
                            "<continuation id=\"this\">69</continuation>\n" +
                            "<output label=\"foo\">bar</output>\n" +
                            "<output label=\"baz\">cox</output>\n" +
                            "</group>\n");

        group = new RootGroup(0, new MyContinuation("96"));
        group.setField("foo", "b\u00e6r");
        group.setField("b\u00e5z", "cox");
        assertRender(group, "<group relevance=\"1.0\">\n" +
                            "<id type=\"root\"/>\n" +
                            "<continuation id=\"this\">96</continuation>\n" +
                            "<output label=\"foo\">b\u00e6r</output>\n" +
                            "<output label=\"b\u00e5z\">cox</output>\n" +
                            "</group>\n");
    }

    @Test
    public void requireThatHitListsRenderAsExpected() {
        assertRender(new HitList("foo"), "<hitlist label=\"foo\"></hitlist>\n");
        assertRender(new HitList("b\u00e6z"), "<hitlist label=\"b\u00e6z\"></hitlist>\n");

        HitList lst = new HitList("foo");
        lst.continuations().put("bar.key", new MyContinuation("bar.val"));
        lst.continuations().put("baz.key", new MyContinuation("baz.val"));
        assertRender(lst, "<hitlist label=\"foo\">\n" +
                          "<continuation id=\"bar.key\">bar.val</continuation>\n" +
                          "<continuation id=\"baz.key\">baz.val</continuation>\n" +
                          "</hitlist>\n");
}

    private static Group newGroup(GroupId id) {
        return new Group(id, new Relevance(1));
    }

    @SuppressWarnings("deprecation")
    private static void assertRender(HitGroup hit, String expectedXml) {
        StringWriter str = new StringWriter();
        XMLWriter out = new XMLWriter(str, 0, -1);
        try {
            HitRenderer.renderHeader(hit, out);
            while (out.openTags().size() > 0) {
                out.closeTag();
            }
        } catch (IOException e) {
            fail();
        }
        assertEquals(expectedXml, str.toString());
    }

    private static class MyContinuation extends Continuation {

        final String str;

        MyContinuation(String str) {
            this.str = str;
        }

        @Override
        public String toString() {
            return str;
        }
    }
}
