// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.yahoo.fs4.PongPacket;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.statistics.ElapsedTime;

/**
 * An answer from Ping.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class Pong {

    private String pingInfo="";
    private List<ErrorMessage> errors = new ArrayList<>(1);
    private List<PongPacket> pongPackets = new ArrayList<>(1);
    private ElapsedTime elapsed = new ElapsedTime();

    public Pong() {
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
    public void addPongPacket(PongPacket pongPacket) {
        pongPackets.add(pongPacket);
    }
    public PongPacket getPongPacket(int i) {
        return pongPackets.get(i);
    }
    public int getPongPacketsSize() {
        return pongPackets.size();
    }
    /** Merge all information from another pong into this */
    public void merge(Pong pong) {
        if (pong.badResponse()) {
            errors.addAll(pong.getErrors());
        }
        pongPackets.addAll(pong.getPongPackets());
    }
    public List<ErrorMessage> getErrors() {
        return Collections.unmodifiableList(errors);
    }
    public List<PongPacket> getPongPackets() {
        return Collections.unmodifiableList(pongPackets);
    }
    /** @return whether there is an error or not */
    public boolean badResponse() {
        return !errors.isEmpty();
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
    public @Override String toString() {
        StringBuffer m=new StringBuffer("Result of pinging");
        if (pingInfo.length() > 0) {
            m.append(" using ");
            m.append(pingInfo);
        }
        m.append(" ");
        for (int i=0; i<errors.size(); i++) {
            m.append(errors.get(i).toString());
            if (i<errors.size()-1)
                m.append(", ");
        }
        return m.toString();
    }

}
