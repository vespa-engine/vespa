package ai.vespa.language.chunker;

import com.yahoo.language.process.Chunker;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ChunkerTester {

    private final Chunker chunker;

    public ChunkerTester(Chunker chunker) {
        this.chunker = chunker;
    }

    public void assertChunks(String text, String ... expectedChunks) {
        assertChunks(text, List.of(), expectedChunks);
    }

    public void assertChunks(String text, List<String> arguments, String ... expectedChunks) {
        var context = new Chunker.Context("test", arguments, Map.of());
        List<Chunker.Chunk> chunks = chunker.chunk(text, context);
        assertEquals("Unexpected number of chunks. Actual chunks:\n" +
                     chunks.stream().map(Chunker.Chunk::text).collect(Collectors.joining("\n")),
                     expectedChunks.length, chunks.size());
        for (int i = 0; i < expectedChunks.length; i++) {
            assertEquals("Chunk " + i, expectedChunks[i], chunks.get(i).text());
        }
    }

}
