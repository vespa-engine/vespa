package ai.vespa.vespasignificance.export;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testing that lines are parsed correctly, without process.
 *
 * @author johsol
 */
public class ParseDumpWordsTest {

    @Test
    void parsesWellFormedAndSkipsMalformed() {
        String data = """
                hello\t123
                #bad line
                world\t42
                malformed\t
                empty\t
                number\tnotanumber
                \t1
                """;

        List<VespaIndexInspectClient.TermDocumentFrequency> rows = VespaIndexInspectClient.streamDumpWords(new StringReader(data)).toList();
        assertEquals(2, rows.size());
        assertEquals(new VespaIndexInspectClient.TermDocumentFrequency("hello", 123L), rows.get(0));
        assertEquals(new VespaIndexInspectClient.TermDocumentFrequency("world", 42L), rows.get(1));
    }

    static final class CloseTrackingReader extends StringReader {
        boolean closed = false;
        CloseTrackingReader(String s) { super(s); }
        @Override public void close() {
            closed = true;
            super.close();
        }
    }

    @Test
    void closesReaderOnStreamClose() {
        var reader = new CloseTrackingReader("a\t1\n");
        var stream = VespaIndexInspectClient.streamDumpWords(reader);
        assertEquals(1L, stream.count());
        stream.close();
        assertTrue(reader.closed);
    }

}
