// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;

import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.statistics.ElapsedTime;

import java.util.Optional;

/**
 * An answer from Ping.
 *
 * @author bratseth
 */
public class Pong {

    private final ElapsedTime elapsed = new ElapsedTime();
    private final Long activeDocuments;
    private final Long targetActiveDocuments;
    private final boolean isBlockingWrites;
    private final ErrorMessage error;

    public Pong() {
        this(null, null, false, null);
    }

    public Pong(ErrorMessage error) {
        this(null, null, false, error);
    }

    public Pong(long activeDocuments, long targetActiveDocuments) {
        this(activeDocuments, targetActiveDocuments, false, null);
    }

    public Pong(long activeDocuments, long targetActiveDocuments, boolean isBlockingWrites) {
        this(activeDocuments, targetActiveDocuments, isBlockingWrites, null);
    }

    private Pong(Long activeDocuments, Long targetActiveDocuments, boolean isBlockingWrites, ErrorMessage error) {
        this.activeDocuments = activeDocuments;
        this.targetActiveDocuments = targetActiveDocuments;
        this.isBlockingWrites = isBlockingWrites;
        this.error = error;
    }

    public Optional<ErrorMessage> error() { return Optional.ofNullable(error); }

    /** Returns the number of active documents in the backend responding in this Pong, if available */
    public Optional<Long> activeDocuments() { return Optional.ofNullable(activeDocuments); }

    /** Returns the number of target active documents in the backend responding in this Pong, if available */
    public Optional<Long> targetActiveDocuments() { return Optional.ofNullable(targetActiveDocuments); }

    /** Returns true if the pinged node is currently blocking write operations due to being full */
    public boolean isBlockingWrites() { return isBlockingWrites; }

    /** Returns whether there is an error or not */
    public boolean badResponse() { return error != null; }

    public ElapsedTime getElapsedTime() { return elapsed; }

    /** Returns a string which included the ping info (if any) and any errors added to this */
    @Override
    public String toString() {
        StringBuilder m = new StringBuilder("Ping result");
        activeDocuments().ifPresent(docCount -> m.append(" active docs: ").append(docCount));
        targetActiveDocuments().ifPresent(docCount -> m.append(" target active docs: ").append(docCount));
        if (isBlockingWrites)
            m.append(" blocking writes: true");
        error().ifPresent(e -> m.append(" error: ").append(error));
        return m.toString();
    }

}
