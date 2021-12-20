// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.14
 */
public class LowercaseTestCase {

    @Test
    public void testAZ() {
        {
            String lowercase = Lowercase.toLowerCase("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
            assertEquals("abcdefghijklmnopqrstuvwxyz", lowercase);
        }
        {
            String lowercase = Lowercase.toLowerCase("abcdefghijklmnopqrstuvwxyz");
            assertEquals("abcdefghijklmnopqrstuvwxyz", lowercase);
        }
        {
            String lowercase = Lowercase.toLowerCase("AbCDEfGHIJklmnoPQRStuvwXyz");
            assertEquals("abcdefghijklmnopqrstuvwxyz", lowercase);
        }

        {
            String lowercase = Lowercase.toLowerCase("@+#");
            assertEquals("@+#", lowercase);
        }
        {
            String lowercase = Lowercase.toLowerCase("[]");
            assertEquals("[]", lowercase);
        }
        {
            String lowercase = Lowercase.toLowerCase("{}");
            assertEquals("{}", lowercase);
        }
        {
            String lowercase = Lowercase.toLowerCase("\u00cd\u00f4");
            assertEquals("\u00ed\u00f4", lowercase);
        }
    }

    @Test
    public void test7bitAscii() {
        for(char c = 0; c < 128; c++) {
            char [] carray = {c};
            String s = new String(carray);
            assertEquals(Lowercase.toLowerCase(s), s.toLowerCase(Locale.ENGLISH));
            assertEquals(Lowercase.toUpperCase(s), s.toUpperCase(Locale.ENGLISH));
        }
    }

    @Test
    @Ignore
    public void performance() {
        for (int i=0; i < 2; i++) {
            benchmark(i);
        }
    }

    private void benchmark(int i) {
        Lowercase.toLowerCase("warmup");
        String lowercaseInput = "abcdefghijklmnopqrstuvwxyz" + i;
        String uppercaseInput = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + i;
        String mixedcaseInput = "AbCDEfGHIJklmnoPQRStuvwXyz" + i;

        System.err.println("Lowercase input: ");
        testPerformance(lowercaseInput);

        System.err.println("Uppercase input: ");
        testPerformance(uppercaseInput);

        System.err.println("Mixed-case input: ");
        testPerformance(mixedcaseInput);
    }

    private void testPerformance(String input) {
        final int NUM = 100000000;
        long elapsedTimeOwnImpl;
        long ownCount = 0;
        long javaCount = 0;
        {
            long startTimeOwnImpl = System.currentTimeMillis();
            for (int i = 0; i < NUM; i++) {
                ownCount += Lowercase.toLowerCase(input).length();
            }
            elapsedTimeOwnImpl = System.currentTimeMillis() - startTimeOwnImpl;
            System.err.println("Own implementation: " + elapsedTimeOwnImpl);
        }

        long elapsedTimeJava;
        {
            long startTimeJava = System.currentTimeMillis();
            for (int i = 0; i < NUM; i++) {
                javaCount += input.toLowerCase(Locale.ENGLISH).length();
            }
            elapsedTimeJava = System.currentTimeMillis() - startTimeJava;
            System.err.println("Java's implementation: " + elapsedTimeJava);
        }

        long diff = elapsedTimeJava - elapsedTimeOwnImpl;
        double diffPercentage = (((double) diff) / ((double) elapsedTimeJava)) * 100.0;
        System.err.println("Own implementation is " + diffPercentage + " % faster. owncount=" + ownCount + " javaCount=" + javaCount);
    }
}
