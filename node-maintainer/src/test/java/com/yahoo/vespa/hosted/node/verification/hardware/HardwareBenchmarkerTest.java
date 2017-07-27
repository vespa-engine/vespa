package com.yahoo.vespa.hosted.node.verification.hardware;

import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import org.junit.Before;

import static org.junit.Assert.*;

public class HardwareBenchmarkerTest {

    private MockCommandExecutor mockCommandExecutor;

    @Before
    public void setup(){
        mockCommandExecutor = new MockCommandExecutor();

    }



}