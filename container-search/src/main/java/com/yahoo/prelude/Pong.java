// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;

import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.statistics.ElapsedTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * An answer from Ping.
 *
 * @author Steinar Knutsen
 */
public class Pong {

    private String pingInfo="";
    private final List<ErrorMessage> errors = new ArrayList<>(1);
    private ElapsedTime elapsed = new ElapsedTime();
    private final Optional<Long> activeDocuments;

    public Pong() {
        this.activeDocuments = Optional.empty();
    }

    public Pong(ErrorMessage error) {
        errors.add(error);
        this.activeDocuments = Optional.empty();
    }

    public Pong(long activeDocuments) {
        this.activeDocuments = Optional.of(activeDocuments);
    }

    public void addError(ErrorMessage error) {
        errors.add(error);
    }

    public ErrorMessage getError(int i) {
        return errors.get(i);
    }

    /** Returns the number of active documents in the backend responding in this Pong, if available */
    public Optional<Long> activeDocuments() {
        return activeDocuments;
    }

    /** Returns the number of nodes which responded to this Pong, if available */
    public Optional<Integer> activeNodes() {
        return Optional.empty();
    }

    public List<ErrorMessage> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /** Returns whether there is an error or not */
    public boolean badResponse() {
        return ! errors.isEmpty();
    }

    public ElapsedTime getElapsedTime() {
        return elapsed;
    }

    /** Returns a string which included the ping info (if any) and any errors added to this */
    @Override
    public String toString() {
        StringBuilder m = new StringBuilder("Result of pinging");
        if (pingInfo.length() > 0) {
            m.append(" using ");
            m.append(pingInfo);
        }
        if (errors.size() > 0)
            m.append(" ");
        for (int i = 0; i < errors.size(); i++) {
            m.append(errors.get(i).toString());
            if ( i <errors.size()-1)
                m.append(", ");
        }
        return m.toString();
    }

}
