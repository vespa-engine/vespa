// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fsa.test;

import com.yahoo.fsa.FSA;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.nio.BufferUnderflowException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author geirst
 */
public class FSADataTestCase {

    private static class Worker extends Thread {
        FSA.State state;
        String word;
        String data;
        long numRuns;
        long numExceptions;
        long numAsserts;
        public Worker(FSA fsa, String word, String data, long numRuns) {
            state = fsa.getState();
            this.word = word;
            this.data = data;
            this.numRuns = numRuns;
            this.numExceptions = 0;
            this.numAsserts = 0;
        }
        public void run() {
            for (long i = 0; i < numRuns; ++i) {
                state.start();
                state.delta(word);
                try {
                    String data = state.dataString();
                    if (!this.data.equals(data)) {
                        ++numAsserts;
                    }
                } catch (BufferUnderflowException e) {
                    ++numExceptions;
                }
            }
            System.out.println("Worker(" + word + "): numExceptions(" + numExceptions + "), numAsserts(" + numAsserts + ")");
        }
    };

    private FSA fsa;

    @Before
    public void setUp() throws IOException {
        fsa = new FSA(new FileInputStream("src/test/fsa/test-data.fsa"));
    }

    @Test
    public void testBasic() {
        FSA.State state = fsa.getState();
        state.delta("aa");
        assertTrue(state.isFinal());
        assertEquals("aa data", state.dataString());

        state.start();
        state.delta("bbbb");
        assertTrue(state.isFinal());
        assertEquals("bbbb data", state.dataString());

        state.start();
        state.delta("c");
        assertTrue(state.isFinal());
        assertEquals("c data", state.dataString());

        state.start();
        state.delta("dddddd");
        assertTrue(state.isFinal());
        assertEquals("dddddd data", state.dataString());
    }

    @Test
    public void testMultipleThreads() {
        long numRuns = 10000;
        List<Worker> workers = new ArrayList<Worker>();
        workers.add(new Worker(fsa, "aa", "aa data", numRuns));
        workers.add(new Worker(fsa, "bbbb", "bbbb data", numRuns));
        workers.add(new Worker(fsa, "c", "c data", numRuns));
        workers.add(new Worker(fsa, "dddddd", "dddddd data", numRuns));
        for (int i = 0; i < workers.size(); ++i) {
            workers.get(i).start();
        }
        try {
            for (int i = 0; i < workers.size(); ++i) {
                workers.get(i).join();
            }
        } catch (InterruptedException e) {
            assertTrue(false);
        }
        for (int i = 0; i < workers.size(); ++i) {
            assertEquals(0, workers.get(i).numExceptions);
            assertEquals(0, workers.get(i).numAsserts);
        }
    }

}
