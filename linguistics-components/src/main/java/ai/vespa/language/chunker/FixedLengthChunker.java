package ai.vespa.language.chunker;

import com.yahoo.language.process.Chunker;
import com.yahoo.text.UnicodeString;

import java.util.ArrayList;
import java.util.List;

/**
 * A chunker which splits a text into chunks of a fixed character length.
 *
 * @author bratseth
 */
public class FixedLengthChunker implements Chunker {

    private final int defaultChunkLength = 500;

    @Override
    public List<Chunk> chunk(String inputText, Context context) {
        int chunkLength = context.arguments().isEmpty() ? defaultChunkLength : asInteger(context.arguments().get(0));
        var text = new UnicodeString(inputText);
        List<Chunk> chunks = new ArrayList<>();
        var currentChunk = new StringBuilder();
        int currentLength = 0;
        for (int i = 0; i < text.length();) {
            currentChunk.appendCodePoint(text.codePointAt(i));
            if (++currentLength == chunkLength) {
                chunks.add(new Chunk(currentChunk.toString()));
                currentChunk.setLength(0);
                currentLength = 0;
            }
            i = text.nextIndex(i);
        }
        if (currentLength > 0)
            chunks.add(new Chunk(currentChunk.toString()));
        return chunks;
    }

    private int asInteger(String s) {
        try {
            return Integer.parseInt(s);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a chunk length integer argument to " +
                                               "the fixed-length chunker, got '" + s + "'");
        }
    }

}
