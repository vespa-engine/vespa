// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.yahoo.fs4.PongPacket;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.statistics.ElapsedTime;

/**
 * An answer from Ping.
 *
 * @author Steinar Knutsen
 */
public class Pong {

    private String pingInfo="";
    private final List<ErrorMessage> errors = new ArrayList<>(1);
    private final Optional<PongPacket> pongPacket;
    private ElapsedTime elapsed = new ElapsedTime();

    public Pong() {
        this.pongPacket = Optional.empty();
    }
    
    public Pong(ErrorMessage error) {
        errors.add(error);
        this.pongPacket = Optional.empty();
    }
    
    public Pong(PongPacket pongPacket) {
        this.pongPacket = Optional.of(pongPacket);
    }
    
    public void addError(ErrorMessage error) {
        errors.add(error);
    }

    public ErrorMessage getError(int i) {
        return errors.get(i);
    }

    public int getErrorSize() {
        return errors.size();
    }

    /** Returns the package causing this to exist, or empty if none */
    public Optional<PongPacket> getPongPacket() { return pongPacket; }

    public List<ErrorMessage> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /** Returns whether there is an error or not */
    public boolean badResponse() {
        return ! errors.isEmpty();
    }

    /** Sets information about the ping used to produce this. This is included when returning the tostring of this. */
    public void setPingInfo(String pingInfo) {
        if (pingInfo==null)
            pingInfo="";
        this.pingInfo=pingInfo;
    }

    /** Returns information about the ping use, or "" (never null) if none */
    public String getPingInfo() { return pingInfo; }

    public ElapsedTime getElapsedTime() {
        return elapsed;
    }

    /** Returns a string which included the ping info (if any) and any errors added to this */
    @Override
    public String toString() {
        StringBuffer m = new StringBuffer("Result of pinging");
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
