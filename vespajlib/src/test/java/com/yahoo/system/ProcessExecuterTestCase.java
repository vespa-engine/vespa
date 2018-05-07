// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.system;

import com.yahoo.collections.Pair;
import com.yahoo.io.IOUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ProcessExecuterTestCase {

    @Test
    public void testIt() throws IOException {
        IOUtils.writeFile("tmp123.txt","hello\nworld",false);
        ProcessExecuter exec=new ProcessExecuter();
        assertEquals(new Pair<>(0, "hello\nworld"), exec.exec("cat tmp123.txt"));
        assertEquals(new Pair<>(0, "hello\nworld"), exec.exec(new String[]{"cat", "tmp123.txt"}));
        new File("tmp123.txt").delete();
    }
    
}
