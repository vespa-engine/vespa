package ai.vespa.language.chunker;

import com.yahoo.language.process.CharacterClasses;
import com.yahoo.language.process.Chunker;
import com.yahoo.text.UnicodeString;

import java.util.ArrayList;
import java.util.List;

/**
 * A chunker which splits a text into chunks at the first non-word/letter character after a given target chunk length
 * measured in codepoints (or precisely at that length, for CJK languages).
 *
 * @author bratseth
 */
public class FixedLengthChunker implements Chunker {

    private static final int defaultChunkLength = 1000;

    private final CharacterClasses characters = new CharacterClasses();

    @Override
    public List<Chunk> chunk(String inputText, Context context) {
        int chunkLength = context.arguments().isEmpty() ? defaultChunkLength : asInteger(context.arguments().get(0));
        boolean isCjk = context.getLanguage().isCjk();
        return context.computeCachedValueIfAbsent(new CacheKey(this, inputText, chunkLength, isCjk),
                                                  () -> new ChunkComputer(inputText, chunkLength, isCjk).chunk());
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

    /**
     * Computer with the scope of chunking a single input text.
     */
    private class ChunkComputer {

        final UnicodeString text;
        final int chunkLength;
        final boolean isCjk;

        final List<Chunk> chunks = new ArrayList<>();
        int index = 0;

        public ChunkComputer(String text, int chunkLength, boolean isCjk) {
            this.text = new UnicodeString(text);
            this.chunkLength = chunkLength;
            this.isCjk = isCjk;
        }

        List<Chunk> chunk() {
            StringBuilder currentChunk = new StringBuilder();
            int currentLength = 0;
            while (index < text.length()) {
                int currentChar = text.codePointAt(index);
                currentChunk.appendCodePoint(currentChar);
                if (++currentLength >= chunkLength && (isCjk || (!isLetter(index)))) {
                    chunks.add(new Chunk(currentChunk.toString()));
                    currentChunk.setLength(0);
                    currentLength = 0;
                }
                index = nextIndex();
            }
            if (currentLength > 0)
                chunks.add(new Chunk(currentChunk.toString()));
            return chunks;
        }

        int charAt(int index) {
            return text.codePointAt(index);
        }

        boolean isLetter(int index) {
            return characters.isLetterOrDigit(charAt(index));
        }

        int nextIndex() {
            return text.nextIndex(index);
        }

    }

}
