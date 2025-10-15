// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.merge;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests methods in {@link TermDfKWayMerge}
 *
 * @author johsol
 */
public class TermDfKWayMergeTest {

    private static TermDfKWayMerge.Cursor cursorFrom(String s) {
        return new TermDfKWayMerge.Cursor(new BufferedReader(new StringReader(s)));
    }

    private static String mergeStrings(long minKeep, String... inputs) throws IOException {
        List<BufferedReader> readers = Arrays.stream(inputs)
                .map(s -> new BufferedReader(new StringReader(s)))
                .toList();
        StringWriter sw = new StringWriter();
        try (BufferedWriter bw = new BufferedWriter(sw)) {
            TermDfRowSink sink = (term, df) -> {
               bw.write(term);
               bw.write('\t');
               bw.write(Long.toString(df));
               bw.write('\n');
            };
            TermDfKWayMerge.merge(readers, sink, minKeep);
        }
        return sw.toString();
    }

    @Test
    void parsesTermAndDf() throws Exception {
        var c = cursorFrom("apple\t123\nbanana\t456\n");
        assertTrue(c.advance());
        assertEquals("apple", c.term);
        assertEquals(123L, c.documentFrequency);

        assertTrue(c.advance());
        assertEquals("banana", c.term);
        assertEquals(456L, c.documentFrequency);

        assertFalse(c.advance()); // EOF
    }

    @Test
    void trimsWhitespaceAndSkipsBlankLines() throws Exception {
        var c = cursorFrom("\n   \npear\t  789   \n\n");
        assertTrue(c.advance());
        assertEquals("pear", c.term);
        assertEquals(789L, c.documentFrequency);
        assertFalse(c.advance()); // nothing more after blanks
    }

    @Test
    void throwsOnLineWithoutTab() {
        var c = cursorFrom("notabline\n");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, c::advance);
    }

    @Test
    void throwsOnNonNumericDf() {
        var c = cursorFrom("orange\tNaN\n");
        assertThrows(IllegalArgumentException.class, c::advance);
    }

    @Test
    void returnsFalseOnCompletelyEmptyInput() throws Exception {
        var c = cursorFrom("\n \n\t \n");
        assertFalse(c.advance());
    }

    @Test
    void mergesTwoSortedInputsAndKeepsGlobalOrder() throws Exception {
        String in1 = "apple\t1\ncarrot\t2\n";
        String in2 = "banana\t3\nzebra\t4\n";

        String out = mergeStrings(0, in1, in2);

        // Output must be globally sorted by term
        assertEquals(
                "apple\t1\n" +
                        "banana\t3\n" +
                        "carrot\t2\n" +
                        "zebra\t4\n",
                out
        );
    }

    @Test
    void sumsSameTermAcrossFiles() throws Exception {
        String in1 = "apple\t1\nbanana\t5\n";
        String in2 = "apple\t2\ncarrot\t7\n";
        String in3 = "apple\t3\nbanana\t1\n";

        String out = mergeStrings(0, in1, in2, in3);

        assertEquals(
                "apple\t6\n" +   // 1+2+3
                        "banana\t6\n" +  // 5+1
                        "carrot\t7\n",
                out
        );
    }

    @Test
    void respectsMinKeepInclusive() throws Exception {
        String in1 = "apple\t1\nbanana\t2\n";
        String in2 = "apple\t1\nbanana\t1\n";

        // apple sum=2, banana sum=3
        String out = mergeStrings(2, in1, in2);

        assertEquals(
                "apple\t2\n" +   // kept (>= 2)
                        "banana\t3\n",   // kept
                out
        );
    }

    @Test
    void dropsBelowMinKeep() throws Exception {
        String in1 = "a\t1\nb\t2\n";
        String in2 = "a\t0\nb\t0\n";

        String out = mergeStrings(3, in1, in2); // a=1, b=2 -> both dropped

        assertEquals("", out);
    }

    @Test
    void handlesEmptyReadersGracefully() throws Exception {
        String empty = "\n \n";
        String in = "alpha\t10\nbeta\t20\n";

        String out = mergeStrings(0, empty, in, empty);

        assertEquals(
                "alpha\t10\n" +
                        "beta\t20\n",
                out
        );
    }

    @Test
    void stableWithSingleReader() throws Exception {
        String in = "ant\t1\nbee\t2\ncat\t3\n";
        String out = mergeStrings(0, in);
        assertEquals(in, out);
    }

}
