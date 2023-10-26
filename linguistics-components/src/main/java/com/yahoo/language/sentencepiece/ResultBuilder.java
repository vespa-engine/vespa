// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.sentencepiece;

/**
 * Builds a result from a sentencepiece tokenization by being called for each segment in reverse
 *
 * @param <RESULTTYPE> the type of result this produces
 * @author bratseth
 */
abstract class ResultBuilder<RESULTTYPE> {

    private final RESULTTYPE result;

    ResultBuilder(RESULTTYPE result) {
        this.result = result;
    }

    /** Called for each segment, starting from the last and working backwards */
    abstract void add(int start, int end, SentencePieceAlgorithm.SegmentEnd[] segmentEnds);

    RESULTTYPE result() {return result;}

    void build(String input, SentencePieceAlgorithm.SegmentEnd[] segmentEnds, boolean collapseUnknowns) {
        if (collapseUnknowns) {
            int segmentEnd = input.length();
            int collapsedSegmentEnd = segmentEnd;
            while (segmentEnd > 0) {
                if (segmentEnds[segmentEnd].type != TokenType.unknown ) {
                    if (collapsedSegmentEnd != segmentEnd) { // We have deferred an unknown collapsed segment
                        add(segmentEnd, collapsedSegmentEnd, segmentEnds);
                    }
                    add(segmentEnds[segmentEnd].segmentStart, segmentEnd, segmentEnds);
                    collapsedSegmentEnd = segmentEnds[segmentEnd].segmentStart;
                }
                segmentEnd = segmentEnds[segmentEnd].segmentStart;
            }
        }
        else {
            int segmentEnd = input.length();
            while (segmentEnd > 0) {
                add(segmentEnds[segmentEnd].segmentStart, segmentEnd, segmentEnds);
                segmentEnd = segmentEnds[segmentEnd].segmentStart;
            }
        }
    }

}
