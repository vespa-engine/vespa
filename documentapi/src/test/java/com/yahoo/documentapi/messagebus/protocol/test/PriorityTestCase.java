// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol.Priority;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class PriorityTestCase {

    @Test
    public void requireThat51PriorityValuesAreCorrect() throws IOException {
        String path = "test/crosslanguagefiles/5.1-Priority.txt";
        BufferedReader in = new BufferedReader(new FileReader(path));

        List<Priority> expected = new LinkedList<Priority>(Arrays.asList(Priority.values()));
        String str;
        while ((str = in.readLine()) != null) {
            String arr[] = str.split(":", 2);
            Priority pri = Priority.valueOf(arr[0]);
            assertEquals(pri.toString(), pri.getValue(), Integer.valueOf(arr[1]).intValue());
            assertTrue("Unexpected priority '" + str + "'.", expected.remove(pri));
        }
        assertTrue("Expected priorities " + expected + ".", expected.isEmpty());
    }

    @Test
    public void requireThatUnknownPriorityThrowsException() {
        try {
            DocumentProtocol.getPriority(Priority.HIGHEST.getValue() - 1);
            fail();
        } catch (IllegalArgumentException e) {

        }
        try {
            DocumentProtocol.getPriority(Priority.LOWEST.getValue() + 1);
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    public void requireThatUnknownPriorityNameThrowsException() {
        try {
            DocumentProtocol.getPriorityByName("FOO");
            fail();
        } catch (IllegalArgumentException e) {

        }
    }
}
