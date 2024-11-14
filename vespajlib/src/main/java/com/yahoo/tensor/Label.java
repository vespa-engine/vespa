// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

/**
 * A label for a tensor dimension.
 * It handles both mapped dimensions with string labels and indexed dimensions with numeric labels.
 * For mapped dimensions, a negative numeric label is assigned by LabelCache.
 * For indexed dimension, the index itself is used as a positive numeric label.
 * 
 * @author glebashnik
 */
public interface Label {
    long asNumeric();
    String asString();
}
