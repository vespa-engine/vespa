package ai.vespa.language.chunker;

import com.yahoo.language.process.CharacterClasses;
import com.yahoo.language.process.Chunker;
import com.yahoo.text.UnicodeString;

import java.util.ArrayList;
import java.util.List;

/**
 * A chunker which splits a text into chunks at the first double non-letter/digit character after a given
 * target chunk length measured in characters (or precisely at that length, for CJK languages).
 *
 * If there are no double non-letter/digit characters within 5% above the target length,
 * the chunk split will be at the first single non-letter/digit character.
 *
 * If there are no double non-letter/digit characters within 10% above the target length,
 * the chunk split will be at that position, so the absolute max chunk length will be 10% above the target
 * length.
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
        final int targetLength;
        final boolean isCjk;

        final int softMaxLength;
        final int hardMaxLength;

        final List<Chunk> chunks = new ArrayList<>();
        int index = 0;

        public ChunkComputer(String text, int chunkLength, boolean isCjk) {
            this.text = new UnicodeString(text);
            this.targetLength = chunkLength;
            this.isCjk = isCjk;

            this.softMaxLength = (int)Math.round(targetLength * 1.05);
            this.hardMaxLength = (int)Math.round(targetLength * 1.10);
        }

        List<Chunk> chunk() {
            StringBuilder currentChunk = new StringBuilder();
            int currentLength = 0;
            while (index < text.length()) {
                int currentChar = text.codePointAt(index);
                currentChunk.appendCodePoint(currentChar);
                if (endOfChunk(++currentLength)) {
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

        private boolean endOfChunk(int currentLength) {
            if (currentLength < targetLength) return false;
            if (isCjk) return true;
            if (currentLength < softMaxLength) return !isLetter(index) && !isLetter(nextIndex());
            if (currentLength < hardMaxLength) return !isLetter(index);
            return true;
        }

        int charAt(int index) {
            return text.codePointAt(index);
        }

        boolean isLetter(int index) {
            if (index >= text.length()) return false;
            return characters.isLetterOrDigit(charAt(index));
        }

        int nextIndex() {
            return text.nextIndex(index);
        }

    }

}
