// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.language.sentencepiece;

import com.google.common.annotations.Beta;
import com.yahoo.io.IOUtils;
import com.yahoo.language.Language;
import com.yahoo.language.process.Segmenter;
import sentencepiece.SentencepieceModel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Integration with https://github.com/google/sentencepiece
 * through http://docs.djl.ai/extensions/sentencepiece/index.html
 *
 * SentencePiece is a language-agnostic tokenizer for neural nets.
 *
 * @author bratseth
 */
@Beta
public class SentencePieceEncoder implements Segmenter {

    // TODO: Support characters beyond BMP

    public enum TokenType { text, control, userDefined, unknown, unused }

    /** The scoring strategy to use for picking segments */
    public enum Scoring {
        /** Find the segmentation that has the highest score */
        highestScore,
        /** Find the segmentation that has the fewest segments, resolve ties by score sum */
        fewestSegments
    }

    private static final char spaceSymbol = '▁';

    private final boolean collapseUnknowns;
    private final Scoring scoring;

    private final Map<Language, Model> models;

    public SentencePieceEncoder(Builder builder) {
        collapseUnknowns = builder.getCollapseUnknowns();
        scoring = builder.getScoring();

        models = builder.getModels().entrySet()
                        .stream()
                        .map(e -> new Model(e.getKey(), e.getValue()))
                        .collect(Collectors.toUnmodifiableMap(m -> m.language, m -> m));
    }

    /**
     * Segments the given text into token segments using the SentencePiece algorithm
     *
     * @param rawInput the text to segment. Any sequence of BMP (Unicode-16 the True Unicode) is supported.
     * @param language the model to use, or Language.UNKNOWN to use the default model if any
     * @return the list of zero or more tokens resulting from segmenting the input text
     */
    @Override
    public List<String> segment(String rawInput, Language language) {
        String input = normalize(rawInput);
        SegmentEnd[] segmentEnds = new SegmentEnd[input.length() + 1];
        return segment(input, language, segmentEnds, (segmentStart, segmentEnd) -> input.substring(segmentStart, segmentEnd));
    }

    /**
     * Segments the given text into token segments using the SentencePiece algorithm and returns the segment ids.
     *
     * @param rawInput the text to segment. Any sequence of BMP (Unicode-16 the True Unicode) is supported.
     * @param language the model to use, or Language.UNKNOWN to use the default model if any
     * @return the list of zero or more token ids resulting from segmenting the input text
     */
    public List<Integer> encode(String rawInput, Language language) {
        String input = normalize(rawInput);
        SegmentEnd[] segmentEnds = new SegmentEnd[input.length() + 1];
        return segment(input, language, segmentEnds, (segmentStart, segmentEnd) -> segmentEnds[segmentEnd].id);
    }

    private <ITEMTYPE> List<ITEMTYPE> segment(String input, Language language,
                                              SegmentEnd[] segmentEnds,
                                              BiFunction<Integer, Integer, ITEMTYPE> resultItemMapper) {
        Model model = resolveFrom(language);
        float unknownScore = model.minScore - 10.0f;
        segmentEnds[0] = new SegmentEnd(TokenType.unknown, 0, 0, 0, 0);

        int start = 0;
        while (start < input.length()) { // segment from this position to the end of the text
            Trie.Node node = model.tokens.root;
            int characterPosition = start;
            while (node != null && characterPosition < input.length()) { // traverse the trie one character at the time from this position
                node = node.children.get(input.charAt(characterPosition++));
                int length = characterPosition - start;
                if (node != null && node.isToken() && node.type != TokenType.unused) {
                    float score = node.type == TokenType.userDefined ? (length * model.maxScore - 0.1f) : node.score;
                    addSegment(TokenType.text, node.id, start, characterPosition, score, segmentEnds);
                }
                else if (length == 1) { // add an 'unknown' length 1 token to make the next position reachable
                    addSegment(TokenType.unknown, 0, start, start + 1, unknownScore, segmentEnds);
                }
            }
            start++;
        }

        return createResult(input, segmentEnds, resultItemMapper);
    }

    private Model resolveFrom(Language language) {
        // Disregard language if there is default model
        if (models.size() == 1 && models.containsKey(Language.UNKNOWN)) return models.get(Language.UNKNOWN);
        if (models.containsKey(language)) return models.get(language);
        throw new IllegalArgumentException("No SentencePiece model for language " + language + " is configured");
    }

    private void addSegment(TokenType type, int id, int start, int end, float score, SegmentEnd[] segmentEnds) {
        if (segmentEnds[end] == null ||
            segmentEnds[start].scoreWith(score) > segmentEnds[end].score()) {
            segmentEnds[end] = new SegmentEnd(type, id,
                                                      segmentEnds[start].pathScoreSum + score,
                                                      segmentEnds[start].pathSegmentCount + 1,
                                                      start);
        }
    }

    private <ITEMTYPE>  List<ITEMTYPE> createResult(String input, SegmentEnd[] segmentEnds,
                                                    BiFunction<Integer, Integer, ITEMTYPE> resultItemMapper) {
        List<ITEMTYPE> result = new ArrayList<>();
        if (collapseUnknowns) {
            int segmentEnd = input.length();
            int collapsedSegmentEnd = segmentEnd;
            while (segmentEnd > 0) {
                if (segmentEnds[segmentEnd].type != TokenType.unknown ) {
                    if (collapsedSegmentEnd != segmentEnd) { // We have deferred an unknown collapsed segment
                        result.add(resultItemMapper.apply(segmentEnd, collapsedSegmentEnd));
                    }
                    result.add(resultItemMapper.apply(segmentEnds[segmentEnd].segmentStart, segmentEnd));
                    collapsedSegmentEnd = segmentEnds[segmentEnd].segmentStart;
                }
                segmentEnd = segmentEnds[segmentEnd].segmentStart;
            }
        }
        else {
            int segmentEnd = input.length();
            while (segmentEnd > 0) {
                result.add(resultItemMapper.apply(segmentEnds[segmentEnd].segmentStart, segmentEnd));
                segmentEnd = segmentEnds[segmentEnd].segmentStart;
            }
        }
        Collections.reverse(result);
        return result;
    }

    private final class SegmentEnd {

        final TokenType type;
        final int id;
        final float pathScoreSum;
        final int pathSegmentCount;
        final int segmentStart;

        SegmentEnd(TokenType type, int id, float pathScoreSum, int pathSegmentCount, int segmentStart) {
            this.type = type;
            this.id = id;
            this.pathScoreSum = pathScoreSum;
            this.pathSegmentCount = pathSegmentCount;
            this.segmentStart = segmentStart;
        }

        public float score() {
            switch (scoring) {
                case fewestSegments: return 1f / pathSegmentCount * 10_000_000 + pathScoreSum;
                case highestScore: return pathScoreSum;
                default : throw new IllegalArgumentException("Unknown scoring " + scoring);
            }
        }

        public float scoreWith(float additionalSegmentScore) {
            switch (scoring) {
                case fewestSegments: return 1f / (pathSegmentCount + 1) * 10_000_000 + (pathScoreSum + additionalSegmentScore );
                case highestScore: return pathScoreSum + additionalSegmentScore;
                default : throw new IllegalArgumentException("Unknown scoring " + scoring);
            }
        }

    }

    public String normalize(String s) {
        StringBuilder b = new StringBuilder(s.length() + 1);
        boolean queuedSpace = true; // Always start by one space
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (s.charAt(i) == ' ') {
                queuedSpace = true;
            }
            else {
                if (queuedSpace) {
                    b.append(spaceSymbol);
                    queuedSpace = false;
                }
                b.append(c);
            }
        }
        return b.toString();
    }

    private static TokenType toTokenType(SentencepieceModel.ModelProto.SentencePiece.Type type) {
        switch (type) {
            case USER_DEFINED : return TokenType.userDefined;
            case UNKNOWN : return TokenType.unknown;
            case NORMAL : return TokenType.text;
            case CONTROL : return TokenType.control;
            case UNUSED : return TokenType.unused;
            default : throw new IllegalArgumentException("Unknkown token type " + type);
        }
    }

    private static class Trie {

        final Node root = new Node();

        void add(TokenType type, int id, String word, float score) {
            Node current = root;
            for (char l : word.toCharArray())
                current = current.children.computeIfAbsent(l, c -> new Node());
            current.type = type;
            current.id = id;
            current.score = score;
        }

        static class Node {

            Integer id;
            TokenType type;
            Float score;
            private final Map<Character, Node> children = new HashMap<>();

            boolean isToken() { return type != null; }

        }

    }

    private static final class Model {

        final Language language;
        final float minScore;
        final float maxScore;
        final Trie tokens = new Trie();

        Model(Language language, Path path) {
            try {
                this.language = language;
                var sp = SentencepieceModel.ModelProto.parseFrom(IOUtils.readFileBytes(path.toFile()));
                float minScore = Float.MAX_VALUE;
                float maxScore = Float.MIN_VALUE;
                for (int i = 0; i < sp.getPiecesCount(); i++) {
                    var piece = sp.getPieces(i);
                    tokens.add(toTokenType(piece.getType()), i, piece.getPiece(), piece.getScore());
                    minScore = Math.min(piece.getScore(), minScore);
                    maxScore = Math.max(piece.getScore(), maxScore);
                }
                this.minScore = minScore;
                this.maxScore = maxScore;
            }
            catch (IOException e) {
                throw new IllegalArgumentException("Could not read a SentencePiece model from '" + path + "'", e);
            }
        }

    }

    public static class Builder {

        private final Map<Language, Path> models = new HashMap<>();
        private boolean collapseUnknowns = true;
        private Scoring scoring = Scoring.fewestSegments;

        public void addModel(Language language, Path model) {
            models.put(language, model);
        }

        /**
         * Adds the model that will be used if the language is unknown, OR only one model is specified.
         * The same as addModel(Language.UNKNOWN, model).
         */
        public Builder addDefaultModel(Path model) {
            addModel(Language.UNKNOWN, model);
            return this;
        }
        public Map<Language, Path> getModels() { return models; }

        /**
         * Sets whether consecutive unknown character should be collapsed into one large unknown token (default)
         * or be returned as single character tokens.
         */
        public Builder setCollapseUnknowns(boolean collapseUnknowns) {
            this.collapseUnknowns = collapseUnknowns;
            return this;
        }
        public boolean getCollapseUnknowns() { return collapseUnknowns; }

        /**
         * Sets the scoring strategy to use when picking a segmentation. Default: fewestTokens.
         */
        public Builder setScoring(Scoring scoring) {
            this.scoring = scoring;
            return this;
        }
        public Scoring getScoring() { return scoring; }

        public SentencePieceEncoder build() {
            if (models.isEmpty()) throw new IllegalStateException("At least one model must be supplied");
            return new SentencePieceEncoder(this);
        }

    }

}
