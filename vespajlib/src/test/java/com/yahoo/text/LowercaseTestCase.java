// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Locale;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.14
 */
public class LowercaseTestCase {

    @Test
    public void testAZ() {
        {
            String lowercase = Lowercase.toLowerCase("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
            assertThat(lowercase, equalTo("abcdefghijklmnopqrstuvwxyz"));
        }
        {
            String lowercase = Lowercase.toLowerCase("abcdefghijklmnopqrstuvwxyz");
            assertThat(lowercase, equalTo("abcdefghijklmnopqrstuvwxyz"));
        }
        {
            String lowercase = Lowercase.toLowerCase("AbCDEfGHIJklmnoPQRStuvwXyz");
            assertThat(lowercase, equalTo("abcdefghijklmnopqrstuvwxyz"));
        }

        {
            String lowercase = Lowercase.toLowerCase("@+#");
            assertThat(lowercase, equalTo("@+#"));
        }
        {
            String lowercase = Lowercase.toLowerCase("[]");
            assertThat(lowercase, equalTo("[]"));
        }
        {
            String lowercase = Lowercase.toLowerCase("{}");
            assertThat(lowercase, equalTo("{}"));
        }
        {
            String lowercase = Lowercase.toLowerCase("\u00cd\u00f4");
            assertThat(lowercase, equalTo("\u00ed\u00f4"));
        }
    }

    @Test
    @Ignore
    public void performance() {
        Lowercase.toLowerCase("warmup");
        String lowercaseInput = "abcdefghijklmnopqrstuvwxyz";
        String uppercaseInput = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String mixedcaseInput = "AbCDEfGHIJklmnoPQRStuvwXyz";

        System.err.println("Lowercase input: ");
        testPerformance(lowercaseInput);

        System.err.println("Uppercase input: ");
        testPerformance(uppercaseInput);

        System.err.println("Mixed-case input: ");
        testPerformance(mixedcaseInput);
    }

    private void testPerformance(String input) {
        final int NUM = 10000000;
        long elapsedTimeOwnImpl;
        {
            long startTimeOwnImpl = System.currentTimeMillis();
            for (int i = 0; i < NUM; i++) {
                Lowercase.toLowerCase(input);
            }
            elapsedTimeOwnImpl = System.currentTimeMillis() - startTimeOwnImpl;
            System.err.println("Own implementation: " + elapsedTimeOwnImpl);
        }

        long elapsedTimeJava;
        {
            long startTimeJava = System.currentTimeMillis();
            for (int i = 0; i < NUM; i++) {
                input.toLowerCase(Locale.ENGLISH);
            }
            elapsedTimeJava = System.currentTimeMillis() - startTimeJava;
            System.err.println("Java's implementation: " + elapsedTimeJava);
        }

        long diff = elapsedTimeJava - elapsedTimeOwnImpl;
        double diffPercentage = (((double) diff) / ((double) elapsedTimeJava)) * 100.0;
        System.err.println("Own implementation is " + diffPercentage + " % faster.");

    }
}
