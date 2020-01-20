// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;

import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.statistics.ElapsedTime;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An answer from Ping.
 *
 * @author bratseth
 */
public class Pong {

    private final ElapsedTime elapsed = new ElapsedTime();
    private final Optional<Long> activeDocuments;
    private final boolean isBlockingWrites;
    private final Optional<ErrorMessage> error;

    public Pong() {
        this(Optional.empty(), false, Optional.empty());
    }

    public Pong(ErrorMessage error) {
        this(Optional.empty(), false, Optional.of(error));
    }

    public Pong(long activeDocuments) {
        this(Optional.of(activeDocuments), false, Optional.empty());
    }

    public Pong(long activeDocuments, boolean isBlockingWrites) {
        this(Optional.of(activeDocuments), isBlockingWrites, Optional.empty());
    }

    private Pong(Optional<Long> activeDocuments, boolean isBlockingWrites, Optional<ErrorMessage> error) {
        this.activeDocuments = activeDocuments;
        this.isBlockingWrites = isBlockingWrites;
        this.error = error;
    }

    /**
     * @deprecated do not use. Additional errors are ignored.
     */
    @Deprecated
    public void addError(ErrorMessage error) { }

    /**
     * @deprecated use error() instead
     */
    @Deprecated
    public ErrorMessage getError(int i) {
        if (i > 1) throw new IllegalArgumentException("No error at position " + i);
        if (i == 0 && error.isEmpty()) throw new IllegalArgumentException("No error at position " + i);
        return error.get();
    }

    public Optional<ErrorMessage> error() { return error; }

    /** Returns the number of active documents in the backend responding in this Pong, if available */
    public Optional<Long> activeDocuments() { return activeDocuments; }

    /** Returns true if the pinged node is currently blocking write operations due t being full */
    public boolean isBlockingWrites() { return isBlockingWrites; }

    /**
     * Returns Optional.empty()
     *
     * @return empty
     * @deprecated do not use. There is always one pong per node.
     */
    @Deprecated
    public Optional<Integer> activeNodes() {
        return Optional.empty();
    }

    /**
     * Returns a list containing 0 or 1 errors
     *
     * @deprecated use error() instead
     */
    @Deprecated
    public List<ErrorMessage> getErrors() {
        return error.stream().collect(Collectors.toList());
    }

    /** Returns whether there is an error or not */
    public boolean badResponse() { return error.isPresent(); }

    public ElapsedTime getElapsedTime() { return elapsed; }

    /** Returns a string which included the ping info (if any) and any errors added to this */
    @Override
    public String toString() {
        StringBuilder m = new StringBuilder("Ping result");
        activeDocuments.ifPresent(docCount -> m.append(" active docs: ").append(docCount));
        if (isBlockingWrites)
            m.append(" blocking writes: true");
        error.ifPresent(e -> m.append(" error: ").append(error));
        return m.toString();
    }

}
