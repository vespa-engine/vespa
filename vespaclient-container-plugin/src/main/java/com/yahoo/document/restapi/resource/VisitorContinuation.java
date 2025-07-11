// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

/**
 * Wrapper around an opaque visitor continuation token string and its completion
 * progress as a percentage. A continuation representing that visiting is complete
 * (i.e. no more buckets to visit) should have a token value of null and a completion
 * percentage of 100. Conversely, any continuation representing unfinished visiting
 * must have a non-null token value and a completion percentage less than 100.
 *
 * @author vekterli
 */
record VisitorContinuation(String token, double percentFinished) {

    static final VisitorContinuation FINISHED = new VisitorContinuation(null, 100.0);

    boolean hasRemaining() { return token != null; }

}
