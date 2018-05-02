// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.ObjectDumper;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen
 */
public class ObjectVisitorTestCase {

    @Test
    public void testObjectDumper() {
        assertDump("test: <NULL>\n", null);
        assertDump("test: 1\n", 1);
        assertDump("test: 'foo'\n", "foo");
        assertDump("test: List {\n" +
                   "    [0]: 'foo'\n" +
                   "    [1]: 69\n" +
                   "    [2]: <NULL>\n" +
                   "}\n",
                   Arrays.asList("foo", 69, null));
        assertDump("test: String[] {\n" +
                   "    [0]: 'foo'\n" +
                   "    [1]: 'bar'\n" +
                   "    [2]: 'baz'\n" +
                   "}\n",
                   new String[] { "foo", "bar", "baz" });
        assertDump("test: IntegerResultNode {\n" +
                   "    classId: 16491\n" +
                   "    value: 5\n" +
                   "}\n",
                   new IntegerResultNode(5));
        assertDump("test: FixedWidthBucketFunctionNode {\n" +
                   "    classId: 16461\n" +
                   "    result: <NULL>\n" +
                   "    args: List {\n" +
                   "        [0]: AttributeNode {\n" +
                   "            classId: 16439\n" +
                   "            result: <NULL>\n" +
                   "            attribute: 'foo'\n" +
                   "        }\n" +
                   "    }\n" +
                   "    width: IntegerResultNode {\n" +
                   "        classId: 16491\n" +
                   "        value: 5\n" +
                   "    }\n" +
                   "}\n",
                   new FixedWidthBucketFunctionNode(new IntegerResultNode(5), new AttributeNode("foo")));
    }

    private void assertDump(String expected, Object obj) {
        ObjectDumper dump = new ObjectDumper();
        dump.visit("test", obj);
        assertEquals(expected, dump.toString());
    }

}
