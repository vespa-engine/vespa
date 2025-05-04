package ai.vespa.language.chunker;

import com.yahoo.language.Language;
import com.yahoo.language.process.CharacterClasses;
import com.yahoo.language.process.Chunker;
import com.yahoo.text.UnicodeString;

import java.util.ArrayList;
import java.util.List;

/**
 * A chunker which splits a text into chunks at the first word break after a given target chunk length
 * (or precisely at that length, for CJK languages).
 *
 * @author bratseth
 */
public class FixedLengthChunker implements Chunker {

    private static final int defaultChunkLength = 500;

    private final CharacterClasses characters = new CharacterClasses();

    @Override
    public List<Chunk> chunk(String inputText, Context context) {
        int chunkLength = context.arguments().isEmpty() ? defaultChunkLength : asInteger(context.arguments().get(0));
        boolean isCjk = context.getLanguage().isCjk();
        return context.computeCachedValueIfAbsent(new CacheKey(this, inputText, chunkLength, isCjk),
                                                  () -> computeChunks(inputText, chunkLength, isCjk));
    }

    private List<Chunk> computeChunks(String inputText, int chunkLength, boolean isCjk) {
        var text = new UnicodeString(inputText);
        List<Chunk> chunks = new ArrayList<>();
        var currentChunk = new StringBuilder();
        int currentLength = 0;
        for (int i = 0; i < text.length();) {
            int currentChar = text.codePointAt(i);
            currentChunk.appendCodePoint(currentChar);
            if (++currentLength >= chunkLength && (isCjk || !characters.isLetterOrDigit(currentChar))) {
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

    private record CacheKey(FixedLengthChunker chunker, String inputText, int chunkLength, boolean isCjk) {}

}
