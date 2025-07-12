// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.linguistics;

import ai.vespa.sampling.ProbabilisticSampleRate;
import com.yahoo.document.DocumentId;
import com.yahoo.document.annotation.Annotation;
import com.yahoo.document.annotation.AnnotationTypes;
import com.yahoo.document.annotation.Span;
import com.yahoo.document.annotation.SpanList;
import com.yahoo.document.annotation.SpanTree;
import com.yahoo.document.annotation.SpanTrees;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.yahoo.language.LinguisticsCase.toLowerCase;

/**
 * This annotates strings that are to be indexed with the tokens to index,
 * as produced by the give linguistics implementation.
 * Using annotations lets us provide the tokens to index without mutating
 * the original string which we need to store.
 * The annotations are placed in an annotation tree named "linguistics".
 *
 * @author Simon Thoresen Hult
 */
public class LinguisticsAnnotator {

    private static final Logger log = Logger.getLogger(LinguisticsAnnotator.class.getName());

    static {
        class RateLimitingLogFilter implements Filter {
            // A very conservative sampling rate to avoid spamming the logs during reindexing
            final ProbabilisticSampleRate sampleRate = ProbabilisticSampleRate.withSystemDefaults(0.1);
            final Filter prevFilter;
            RateLimitingLogFilter(Filter prevFilter) { this.prevFilter = prevFilter; }
            @Override public boolean isLoggable(LogRecord lr) {
                return sampleRate.shouldSample() && (prevFilter == null || prevFilter.isLoggable(lr));
            }
        }
        log.setFilter(new RateLimitingLogFilter(log.getFilter()));
    }

    private final Linguistics factory;
    private final AnnotatorConfig config;

    private static class TermOccurrences {

        final Map<String, Integer> termOccurrences = new HashMap<>();
        final int maxOccurrences;

        public TermOccurrences(int maxOccurrences) {
            this.maxOccurrences = maxOccurrences;
        }

        boolean termCountBelowLimit(String term) {
            String lowerCasedTerm = toLowerCase(term);
            int occurrences = termOccurrences.getOrDefault(lowerCasedTerm, 0);
            if (occurrences >= maxOccurrences) return false;

            termOccurrences.put(lowerCasedTerm, occurrences + 1);
            return true;
        }

    }

    /**
     * Constructs a new instance of this annotator.
     *
     * @param factory the linguistics factory to use when annotating
     * @param config  the linguistics config to use
     */
    public LinguisticsAnnotator(Linguistics factory, AnnotatorConfig config) {
        this.factory = factory;
        this.config = config;
    }

    /**
     * Annotates the given string with the appropriate linguistics annotations.
     *
     * @param text the text to annotate
     * @return whether anything was annotated
     */
    public boolean annotate(StringFieldValue text, DocumentId docId, boolean isReindexingOperation) {
        if (text.getSpanTree(SpanTrees.LINGUISTICS) != null) return true;  // Already annotated with LINGUISTICS.

        Tokenizer tokenizer = factory.getTokenizer();
        String input = (text.getString().length() <= config.getMaxTokenizeLength())
                       ? text.getString()
                       : Text.substringByCodepoints(text.getString(), 0, config.getMaxTokenizeLength());

        if (isLikelyBinaryData(input, docId, isReindexingOperation)) return false;

        Iterable<Token> tokens = tokenizer.tokenize(input, config.asLinguisticsParameters());
        TermOccurrences termOccurrences = new TermOccurrences(config.getMaxTermOccurrences());
        SpanTree tree = new SpanTree(SpanTrees.LINGUISTICS);
        for (Token token : tokens)
            addAnnotationSpan(text.getString(), tree.spanList(), token, config.getStemMode(), config.getLowercase(),
                              termOccurrences, config.getMaxTokenLength());

        if (tree.numAnnotations() == 0) return false;
        text.setSpanTree(tree);
        return true;
    }

    /** For unit testing only */
    boolean annotate(StringFieldValue text) { return annotate(text, null, false); }

    /** Creates a TERM annotation which has the term as annotation (only) if it is different from the original. */
    public static Annotation termAnnotation(String term, String originalTerm) {
        if (term.equals(originalTerm))
            return new Annotation(AnnotationTypes.TERM);
        else
            return new Annotation(AnnotationTypes.TERM, new StringFieldValue(term));
    }

    private static void addAnnotation(Span here, String term, String orig, TermOccurrences termOccurrences,
                                      int maxTokenLength) {
        if (term.length() > maxTokenLength) return;
        if (termOccurrences.termCountBelowLimit(term))
            here.annotate(termAnnotation(term, orig));
    }

    private static void addAnnotationSpan(String input, SpanList parent, Token token, StemMode mode,
                                          boolean lowercase, TermOccurrences termOccurrences, int maxTokenLength) {
        if ( ! token.isSpecialToken()) {
            if (token.getNumComponents() > 0) {
                for (int i = 0; i < token.getNumComponents(); ++i) {
                    addAnnotationSpan(input, parent, token.getComponent(i), mode, lowercase, termOccurrences, maxTokenLength);
                }
                return;
            }
            if ( ! token.isIndexable()) return;
        }
        if (token.getOffset() >= input.length()) {
            throw new IllegalArgumentException(token + " has offset " + token.getOffset() + ", which is outside the " +
                                               "bounds of the input string '" + input + "'");
        }
        if (token.getOffset() + token.getOrig().length() > input.length()) {
            throw new IllegalArgumentException(token + " has offset " + token.getOffset() + ", which makes it overflow " +
                                               "the bounds of the input string; " + input);
        }
        if (mode == StemMode.ALL) {
            Span where = parent.span((int)token.getOffset(), token.getOrig().length());
            String indexableOriginal = lowercase ? toLowerCase(token.getOrig()) : token.getOrig();
            String term = token.getTokenString();
            if (term != null) {
                addAnnotation(where, term, token.getOrig(), termOccurrences, maxTokenLength);
                if ( ! term.equals(indexableOriginal))
                    addAnnotation(where, indexableOriginal, token.getOrig(), termOccurrences, maxTokenLength);
            }
            for (int i = 0; i < token.getNumStems(); i++) {
                String stem = token.getStem(i);
                if (! (stem.equals(indexableOriginal) || stem.equals(term)))
                    addAnnotation(where, stem, token.getOrig(), termOccurrences, maxTokenLength);
            }
        } else {
            String term = token.getTokenString();
            if (term == null || term.trim().isEmpty()) return;
            if (term.length() > maxTokenLength) return;
            if (termOccurrences.termCountBelowLimit(term))
                parent.span((int)token.getOffset(), token.getOrig().length()).annotate(termAnnotation(term, token.getOrig()));
        }
    }

    /**
     * Use the ratio of Unicode replacement characters as heuristic if the text is likely representing binary data
     *
     * @see <a href="https://en.wikipedia.org/wiki/Specials_(Unicode_block)#Replacement_character">...</a>
     * @throws IllegalArgumentException if text is likely binary data and not a reindexing operation
     */
    private boolean isLikelyBinaryData(String text, DocumentId docId, boolean isReindexingOperation) {
        var maxRatio = config.getMaxReplacementCharactersRatio();
        var maxCharacters = config.getMaxReplacementCharacters();
        // Skip if both thresholds are disabled
        if (maxRatio >= 1 && (maxCharacters < 0 || maxCharacters == Integer.MAX_VALUE)) return false;
        var replacementCharCount = text.chars().filter(c -> c == 0xFFFD).count();
        if (replacementCharCount > maxCharacters && replacementCharCount > text.length() * maxRatio) {
            var reason = ("some text of length %d is classified as binary data as it contains %d Unicode replacement characters. " +
                    "(max-replacement-character-ratio=%d%%, max-replacement-characters=%d)")
                    .formatted(text.length(), replacementCharCount, (int)Math.round(maxRatio * 100) , maxCharacters);
            var docIdString = (docId != null) ? "%s".formatted(docId.toString()) : "<unknown>";
            if (isReindexingOperation) {
                log.warning("Skipping tokenization of '%s' while reindexing: %s. ".formatted(docIdString, reason));
                return true;
            } else {
                throw new IllegalArgumentException("Invalid document '%s': %s".formatted(docIdString, reason));
            }
        }
        return false;
    }
}
