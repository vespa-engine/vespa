// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * @author arnej27959
 */
public class ObjectDumperTestCase {

    @Test
    public void testSimple() {
        String s1 = "test";

        ObjectDumper defOD = new ObjectDumper();
        ObjectDumper oneOD = new ObjectDumper(1);

        defOD.visit("s1", s1);
        oneOD.visit("s2", s1);

        assertEquals("s1: 'test'\n", defOD.toString());
        assertEquals("s2: 'test'\n", oneOD.toString());
    }

    @Test
    public void testBig() {
        BigIdClass b = new BigIdClass();
        ObjectDumper oneOD = new ObjectDumper(1);

        oneOD.visit("biggie", b);

        assertEquals(
"biggie: BigIdClass {\n"+
" classId: 42\n"+
" : <NULL>\n"+
" one: <NULL>\n"+
" two: FooBarIdClass {\n"+
"  classId: 17\n"+
"  foo: 'def-foo'\n"+
"  bar: 42\n"+
"  lst: List {\n"+
"   [0]: 17\n"+
"   [1]: 42\n"+
"   [2]: 666\n"+
"  }\n"+
" }\n"+
" FooBarIdClass {\n"+
"  classId: 17\n"+
"  foo: 'def-foo'\n"+
"  bar: 42\n"+
"  lst: List {\n"+
"   [0]: 17\n"+
"   [1]: 42\n"+
"   [2]: 666\n"+
"  }\n"+
" }\n"+
" myArrayOne: byte[] {\n"+
"  [0]: 1\n"+
"  [1]: 2\n"+
"  [2]: 3\n"+
"  [3]: 4\n"+
"  [4]: 5\n"+
" }\n"+
"}\n", oneOD.toString());

        ObjectDumper defOD = new ObjectDumper();
        defOD.visit("", b);
        assertEquals(b.toString(), b.toString());
    }

    @Test
    public void testOne() {
        SomeIdClass s3 = new SomeIdClass();

        ObjectDumper defOD = new ObjectDumper();
        ObjectDumper oneOD = new ObjectDumper(1);

        defOD.visit("s3", s3);
        oneOD.visit("s4", s3);

        assertEquals("s3: SomeIdClass {\n    classId: 1234321\n}\n", defOD.toString());
        assertEquals("s4: SomeIdClass {\n classId: 1234321\n}\n", oneOD.toString());
    }

    @Test
    public void testTwo() {
        FooBarIdClass s5 = new FooBarIdClass();

        ObjectDumper defOD = new ObjectDumper();
        ObjectDumper oneOD = new ObjectDumper(1);

        defOD.visit("s5", s5);
        oneOD.visit("s6", s5);

        assertEquals("s5: FooBarIdClass {\n"+
                     "    classId: 17\n"+
                     "    foo: 'def-foo'\n"+
                     "    bar: 42\n"+
                     "    lst: List {\n"+
                     "        [0]: 17\n"+
                     "        [1]: 42\n"+
                     "        [2]: 666\n"+    
                     "    }\n"+
                     "}\n", defOD.toString());
        assertEquals("s6: FooBarIdClass {\n"+
                     " classId: 17\n"+
                     " foo: 'def-foo'\n"+
                     " bar: 42\n"+
                     " lst: List {\n"+
                     "  [0]: 17\n"+
                     "  [1]: 42\n"+
                     "  [2]: 666\n"+    
                     " }\n"+
                     "}\n", oneOD.toString());

    }

    @Test
    public void testRegistry() {
        assertEquals(17, FooBarIdClass.classId);
        int x = Identifiable.registerClass(17, FooBarIdClass.class);
        assertEquals(17, x);
        try {
            x = Identifiable.registerClass(17, SomeIdClass.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(),
"Can not register class 'class com.yahoo.vespa.objects.SomeIdClass' with id 17,"+
" because it already maps to class 'class com.yahoo.vespa.objects.FooBarIdClass'.");
        }
        assertEquals(17, x);

        Identifiable s7 = Identifiable.createFromId(17);
        ObjectDumper defOD = new ObjectDumper();
        defOD.visit("s7", s7);
        assertEquals("s7: FooBarIdClass {\n"+
                     "    classId: 17\n"+
                     "    foo: 'def-foo'\n"+
                     "    bar: 42\n"+
                     "    lst: List {\n"+
                     "        [0]: 17\n"+
                     "        [1]: 42\n"+
                     "        [2]: 666\n"+
                     "    }\n"+
                     "}\n", defOD.toString());

        assertNull(Identifiable.createFromId(717273));
    }

}
