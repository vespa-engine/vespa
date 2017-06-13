package com.yahoo.prelude.fastsearch.test.fs4mock;

import com.yahoo.fs4.BasicPacket;

/**
 * @author bratseth
 */
public class NonWorkingMockFSChannel extends MockFSChannel {

    @Override
    public synchronized boolean sendPacket(BasicPacket bPacket) {
        return false;
    }

}
