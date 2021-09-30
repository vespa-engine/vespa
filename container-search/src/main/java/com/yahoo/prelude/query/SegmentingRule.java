// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

/**
 * If a term has to be resegmented, and the result is more than one word, this
 * is how the result should be handled in the query tree. For Western languages
 * the default is creating a phrase, but for business reasons, some East Asian
 * languages use an AND instead.
 *
 * @author Steinar Knutsen
 */
public enum SegmentingRule {

    LANGUAGE_DEFAULT, PHRASE, BOOLEAN_AND;

}
