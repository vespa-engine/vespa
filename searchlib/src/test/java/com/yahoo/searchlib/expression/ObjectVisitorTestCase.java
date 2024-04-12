// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.ObjectDumper;
import org.junit.Test;


import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class ObjectVisitorTestCase {

    @Test
    public void testObjectDumper() {
        assertDump("test: <NULL>\n", null);
        assertDump("test: 1\n", 1);
        assertDump("test: 'foo'\n", "foo");
        assertDump("""
                        test: List {
                            [0]: 'foo'
                            [1]: 69
                            [2]: <NULL>
                        }
                        """,
                   Arrays.asList("foo", 69, null));
        assertDump("""
                        test: String[] {
                            [0]: 'foo'
                            [1]: 'bar'
                            [2]: 'baz'
                        }
                        """,
                   new String[] { "foo", "bar", "baz" });
        assertDump("""
                        test: IntegerResultNode {
                            classId: 16491
                            value: 5
                        }
                        """,
                   new IntegerResultNode(5));
        assertDump("""
                        test: FixedWidthBucketFunctionNode {
                            classId: 16461
                            result: <NULL>
                            args: List {
                                [0]: AttributeNode {
                                    classId: 16439
                                    result: <NULL>
                                    attribute: 'foo'
                                }
                            }
                            width: IntegerResultNode {
                                classId: 16491
                                value: 5
                            }
                        }
                        """,
                   new FixedWidthBucketFunctionNode(new IntegerResultNode(5), new AttributeNode("foo")));
    }

    private void assertDump(String expected, Object obj) {
        ObjectDumper dump = new ObjectDumper();
        dump.visit("test", obj);
        assertEquals(expected, dump.toString());
    }

}
