package com.yahoo.prelude.fastsearch.test.fs4mock;

import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.mplex.FS4Channel;

/**
 * @author bratseth
 */
public class NonWorkingMockFSChannel extends MockFSChannel {

    public NonWorkingMockFSChannel() {
        super(null, 0);
    }
    
    @Override
    public synchronized boolean sendPacket(BasicPacket bPacket) {
        return false;
    }

}
