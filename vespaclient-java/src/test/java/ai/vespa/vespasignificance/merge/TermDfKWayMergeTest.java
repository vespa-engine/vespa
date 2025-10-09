package ai.vespa.vespasignificance.merge;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.rmi.UnexpectedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TermDfKWayMergeTest {
    private static TermDfKWayMerge.Cursor cursorFrom(String s) {
        return new TermDfKWayMerge.Cursor(new BufferedReader(new StringReader(s)));
    }

    @Test
    void parsesTermAndDf() throws Exception {
        var c = cursorFrom("apple\t123\nbanana\t456\n");
        assertTrue(c.advance());
        assertEquals("apple", c.term);
        assertEquals(123L, c.df);

        assertTrue(c.advance());
        assertEquals("banana", c.term);
        assertEquals(456L, c.df);

        assertFalse(c.advance()); // EOF
    }

    @Test
    void trimsWhitespaceAndSkipsBlankLines() throws Exception {
        var c = cursorFrom("\n   \npear\t  789   \n\n");
        assertTrue(c.advance());
        assertEquals("pear", c.term);
        assertEquals(789L, c.df);
        assertFalse(c.advance()); // nothing more after blanks
    }

    @Test
    void throwsOnLineWithoutTab() {
        var c = cursorFrom("notabline\n");
        UnexpectedException ex = assertThrows(UnexpectedException.class, c::advance);
        assertTrue(ex.getMessage().contains("Invalid term line"));
    }

    @Test
    void returnsFalseOnCompletelyEmptyInput() throws Exception {
        var c = cursorFrom("\n \n\t \n");
        assertFalse(c.advance());
    }

}
