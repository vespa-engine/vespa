// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.sentencepiece;

/**
 * The scoring strategy to use for picking segments
 *
 * @author bratseth
 */
public enum Scoring {

    /** Find the segmentation that has the highest score */
    highestScore,

    /** Find the segmentation that has the fewest segments, resolve ties by score sum */
    fewestSegments

}
