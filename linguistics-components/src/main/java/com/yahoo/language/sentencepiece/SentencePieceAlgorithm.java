// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.sentencepiece;

/**
 * SentencePiece algorithm implementation
 *
 * @author bratseth
 */
class SentencePieceAlgorithm {

    // TODO: Support characters beyond BMP

    static final char spaceSymbol = '▁';

    private final boolean collapseUnknowns;
    private final Scoring scoring;

    SentencePieceAlgorithm(boolean collapseUnknowns, Scoring scoring) {
        this.collapseUnknowns = collapseUnknowns;
        this.scoring = scoring;
    }

    public <RESULTTYPE> void segment(String input, ResultBuilder<RESULTTYPE> resultBuilder, Model model) {
        SegmentEnd[] segmentEnds = new SegmentEnd[input.length() + 1];
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
                    addSegment(TokenType.unknown, 0, start, start + 1, model.minScore - 10.0f, segmentEnds);
                }
            }
            start++;
        }
        resultBuilder.build(input, segmentEnds, collapseUnknowns);
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

    final class SegmentEnd {

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

}
