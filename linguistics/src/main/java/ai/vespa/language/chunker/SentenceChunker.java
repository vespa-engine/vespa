// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.language.chunker;

import com.yahoo.language.process.CharacterClasses;
import com.yahoo.language.process.Chunker;
import com.yahoo.text.UnicodeString;

import java.util.ArrayList;
import java.util.List;

/**
 * A chunker which splits a text into sentences.
 *
 * @author bratseth
 */
public class SentenceChunker implements Chunker {

    private final CharacterClasses characters = new CharacterClasses();

    @Override
    public List<Chunk> chunk(String inputText, Context context) {
        return context.computeCachedValueIfAbsent(new CacheKey(this, inputText), () -> computeChunks(inputText));
    }

    private List<Chunk> computeChunks(String inputText) {
        var text = new UnicodeString(inputText);
        List<Chunk> chunks = new ArrayList<>();
        var currentChunk = new StringBuilder();
        boolean currentHasContent = false;
        for (int i = 0; i < text.length(); ) {
            int currentChar = text.codePointAt(i);
            currentChunk.appendCodePoint(currentChar);
            if (currentHasContent && characters.isSentenceEnd(currentChar) && !characters.isSentenceEnd(text.nextCodePoint(i))) {
                chunks.add(new Chunk(currentChunk.toString()));
                currentChunk.setLength(0);
                currentHasContent = false;
            }
            else {
                currentHasContent |= ( characters.isLetterOrDigit(currentChar) || characters.isSymbol(currentChar) );
            }
            i = text.nextIndex(i);
        }
        if ( ! currentChunk.isEmpty())
            chunks.add(new Chunk(currentChunk.toString()));
        return chunks;
    }

    private record CacheKey(SentenceChunker chunker, String inputText) {};

}
