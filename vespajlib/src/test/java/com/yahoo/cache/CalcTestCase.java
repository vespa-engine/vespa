// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.cache;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Boolean.TRUE;

public class CalcTestCase extends junit.framework.TestCase {

    private SizeCalculator calc;


    public CalcTestCase (String name) {
        super(name);
    }

    public void setUp() {
        calc = new SizeCalculator();
    }

    public void testCalc1() {
        assertEquals(calc.sizeOf(new Object()), 8);
    }

    public void testCalc2() {
        assertEquals(calc.sizeOf(new SixtyFourBooleans()), 8+64);
    }

    public void testBoolean() {
        assertEquals(8+1, calc.sizeOf(TRUE));
    }

    public void testArrayPrimitive() {
        byte[] eightBytes = new byte[]{1,1,1,1,1,1,1,1,};
        assertEquals(16+8, calc.sizeOf(eightBytes));
    }

    public void testArrayObjects() {
        SixtyFourBooleans[] bunchOfBooleans = new SixtyFourBooleans[]{new SixtyFourBooleans(),
                new SixtyFourBooleans(), new SixtyFourBooleans()};
        assertEquals(16+(3*(8+64)+(3*4)), calc.sizeOf(bunchOfBooleans));

    }

    public void testSizeOfList() {
        SixtyFourBooleans sfb = new SixtyFourBooleans();
        List<Object> dupList1 = new ArrayList<>();
        dupList1.add(new Object());
        dupList1.add(sfb);
        dupList1.add(sfb);
        dupList1.add(sfb);
        List<Object> dupList2 = new ArrayList<>();
        dupList2.addAll(dupList1);
        dupList2.add(sfb);
        dupList2.add(sfb);
        dupList2.add(sfb);
        dupList2.add(new Object());
        dupList2.add(new Object());
        assertEquals(calc.sizeOf(dupList2), calc.sizeOf(dupList1)+8+8);
    }

    public void testSizeOfTuple() {
        SixtyFourBooleans[] bunchOfBooleans = new SixtyFourBooleans[]{new SixtyFourBooleans(),
                new SixtyFourBooleans(), new SixtyFourBooleans()};
        SixtyFourBooleans[] bunchOfBooleans2 = new SixtyFourBooleans[]{new SixtyFourBooleans(),
                new SixtyFourBooleans(), new SixtyFourBooleans()};
        assertEquals(16+(3*(8+64)+(3*4)), calc.sizeOf(bunchOfBooleans));
        assertEquals(2* (16+(3*(8+64)+(3*4))), calc.sizeOf(bunchOfBooleans, bunchOfBooleans2));
    }

    /*public void testEmptyArrayList() {
        assertEquals(80, calc.sizeOf(new ArrayList()));
    }*/

    /*public void testFullArrayList() {
        ArrayList arrayList = new ArrayList(10000);

        for (int i = 0; i < 10000; i++) {
            arrayList.add(new Object());
        }

        assertEquals(120040, calc.sizeOf(arrayList));
    }*/

    /*public void testHashMap() {
        assertEquals(120, calc.sizeOf(new HashMap()));

        Byte[] all = new Byte[256];
        for (int i = -128; i < 128; i++) {
            all[i + 128] = new Byte((byte) i);
        }
        assertEquals(5136, calc.sizeOf(all));

        HashMap hm = new HashMap();
        for (int i = -128; i < 128; i++) {
            hm.put("" + i, new Byte((byte) i));
        }
        assertEquals(30776, calc.sizeOf(hm));
    }*/

    /*public void testThousandBooleansObjects() {
        Boolean[] booleans = new Boolean[1000];

        for (int i = 0; i < booleans.length; i++)
            booleans[i] = new Boolean(true);

        assertEquals(20016, calc.sizeOf(booleans));
    }*/

    @SuppressWarnings("unused")
    private static class SixtyFourBooleans {
        boolean a0;
        boolean a1;
        boolean a2;
        boolean a3;
        boolean a4;
        boolean a5;
        boolean a6;
        boolean a7;
        boolean b0;
        boolean b1;
        boolean b2;
        boolean b3;
        boolean b4;
        boolean b5;
        boolean b6;
        boolean b7;
        boolean c0;
        boolean c1;
        boolean c2;
        boolean c3;
        boolean c4;
        boolean c5;
        boolean c6;
        boolean c7;
        boolean d0;
        boolean d1;
        boolean d2;
        boolean d3;
        boolean d4;
        boolean d5;
        boolean d6;
        boolean d7;
        boolean e0;
        boolean e1;
        boolean e2;
        boolean e3;
        boolean e4;
        boolean e5;
        boolean e6;
        boolean e7;
        boolean f0;
        boolean f1;
        boolean f2;
        boolean f3;
        boolean f4;
        boolean f5;
        boolean f6;
        boolean f7;
        boolean g0;
        boolean g1;
        boolean g2;
        boolean g3;
        boolean g4;
        boolean g5;
        boolean g6;
        boolean g7;
        boolean h0;
        boolean h1;
        boolean h2;
        boolean h3;
        boolean h4;
        boolean h5;
        boolean h6;
        boolean h7;
    }
}
