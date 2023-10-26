// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import org.junit.Test;

import static java.lang.Character.MAX_SURROGATE;
import static java.lang.Character.MIN_SURROGATE;

/**
 * @author Einar M R Rosenvinge
 * @since 5.1.14
 */
public class StringFieldValueTestCase {

    @Test
    public void requireThatCharWorks() {
        new StringFieldValue("\t");
        new StringFieldValue("\r");
        new StringFieldValue("\n");
        for (int c = 0x20; c < MIN_SURROGATE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = MAX_SURROGATE + 1; c < 0xFDD0; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0xFDE0; c < 0xFFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0x10000; c < 0x1FFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0x20000; c < 0x2FFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0x30000; c < 0x3FFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0x40000; c < 0x4FFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0x50000; c < 0x5FFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0x60000; c < 0x6FFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0x70000; c < 0x7FFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0x80000; c < 0x8FFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0x90000; c < 0x9FFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0xA0000; c < 0xAFFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0xB0000; c < 0xBFFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0xC0000; c < 0xCFFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0xD0000; c < 0xDFFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0xE0000; c < 0xEFFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0xF0000; c < 0xFFFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
        for (int c = 0x100000; c < 0x10FFFE; c++) {
            new StringFieldValue(new String(Character.toChars(c)));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails0() {
        new StringFieldValue("\u0000");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails1() {
        new StringFieldValue("\u0001");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails2() {
        new StringFieldValue("\u0002");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails3() {
        new StringFieldValue("\u0003");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails4() {
        new StringFieldValue("\u0004");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails5() {
        new StringFieldValue("\u0005");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails6() {
        new StringFieldValue("\u0006");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails7() {
        new StringFieldValue("\u0007");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsB() {
        new StringFieldValue("\u000B");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsC() {
        new StringFieldValue("\u000C");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsE() {
        new StringFieldValue("\u000E");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsF() {
        new StringFieldValue("\u000F");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails10() {
        new StringFieldValue("\u0010");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails11() {
        new StringFieldValue("\u0011");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails12() {
        new StringFieldValue("\u0012");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails13() {
        new StringFieldValue("\u0013");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails14() {
        new StringFieldValue("\u0014");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails15() {
        new StringFieldValue("\u0015");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails16() {
        new StringFieldValue("\u0016");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails17() {
        new StringFieldValue("\u0017");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails18() {
        new StringFieldValue("\u0018");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails19() {
        new StringFieldValue("\u0019");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails1A() {
        new StringFieldValue("\u001A");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails1B() {
        new StringFieldValue("\u001B");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails1C() {
        new StringFieldValue("\u001C");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails1D() {
        new StringFieldValue("\u001D");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails1E() {
        new StringFieldValue("\u001E");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails1F() {
        new StringFieldValue("\u001F");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDD0() {
        new StringFieldValue("\uFDD0");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDD1() {
        new StringFieldValue("\uFDD1");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDD2() {
        new StringFieldValue("\uFDD2");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDD3() {
        new StringFieldValue("\uFDD3");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDD4() {
        new StringFieldValue("\uFDD4");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDD5() {
        new StringFieldValue("\uFDD5");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDD6() {
        new StringFieldValue("\uFDD6");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDD7() {
        new StringFieldValue("\uFDD7");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDD8() {
        new StringFieldValue("\uFDD8");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDD9() {
        new StringFieldValue("\uFDD9");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDDA() {
        new StringFieldValue("\uFDDA");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDDB() {
        new StringFieldValue("\uFDDB");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDDC() {
        new StringFieldValue("\uFDDC");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDDD() {
        new StringFieldValue("\uFDDD");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDDE() {
        new StringFieldValue("\uFDDE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFDDF() {
        new StringFieldValue("\uFDDF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFFFE() {
        new StringFieldValue("\uFFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFFFF() {
        new StringFieldValue("\uFFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails1FFFE() {
        new StringFieldValue("\uD83F\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails1FFFF() {
        new StringFieldValue("\uD83F\uDFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails2FFFE() {
        new StringFieldValue("\uD87F\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails2FFFF() {
        new StringFieldValue("\uD87F\uDFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails3FFFE() {
        new StringFieldValue("\uD8BF\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails3FFFF() {
        new StringFieldValue("\uD8BF\uDFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails4FFFE() {
        new StringFieldValue("\uD8FF\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails4FFFF() {
        new StringFieldValue("\uD8FF\uDFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails5FFFE() {
        new StringFieldValue("\uD93F\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails5FFFF() {
        new StringFieldValue("\uD93F\uDFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails6FFFE() {
        new StringFieldValue("\uD97F\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails6FFFF() {
        new StringFieldValue("\uD97F\uDFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails7FFFE() {
        new StringFieldValue("\uD9BF\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails7FFFF() {
        new StringFieldValue("\uD9BF\uDFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails8FFFE() {
        new StringFieldValue("\uD9FF\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails8FFFF() {
        new StringFieldValue("\uD9FF\uDFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails9FFFE() {
        new StringFieldValue("\uDA3F\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails9FFFF() {
        new StringFieldValue("\uDA3F\uDFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsAFFFE() {
        new StringFieldValue("\uDA7F\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsAFFFF() {
        new StringFieldValue("\uDA7F\uDFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsBFFFE() {
        new StringFieldValue("\uDABF\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsBFFFF() {
        new StringFieldValue("\uDABF\uDFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsCFFFE() {
        new StringFieldValue("\uDAFF\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsCFFFF() {
        new StringFieldValue("\uDAFF\uDFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsDFFFE() {
        new StringFieldValue("\uDB3F\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsDFFFF() {
        new StringFieldValue("\uDB3F\uDFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsEFFFE() {
        new StringFieldValue("\uDB7F\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsEFFFF() {
        new StringFieldValue("\uDB7F\uDFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFFFFE() {
        new StringFieldValue("\uDBBF\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFailsFFFFF() {
        new StringFieldValue("\uDBBF\uDFFF");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails10FFFE() {
        new StringFieldValue("\uDBFF\uDFFE");
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireThatControlCharFails10FFFF() {
        new StringFieldValue("\uDBFF\uDFFF");
    }
}
