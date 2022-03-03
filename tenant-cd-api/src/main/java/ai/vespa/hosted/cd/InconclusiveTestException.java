// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;


/**
 * Signals that a test method can not yield a conclusive result at this time, and must be retried later.
 *
 * @author jonmv
 */
public class InconclusiveTestException extends RuntimeException {

    public InconclusiveTestException() { super(); }

    public InconclusiveTestException(String message) { super(message); }

}
