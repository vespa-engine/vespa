// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit test for the LogMessage TimeComparator
 *
 * @author vlarsen
 *
 */
public class TimeComparatorTestCase {
	private String msgstr1 = "1098709000\t"
        + "nalle.puh.com\t"
        + "23234\t"
        + "serviceName\t"
        + "tst\t"
        + "info\t"
        + "this is a test\n";
	private LogMessage msg1;
	
	private String msgstr2 = "1098709021\t"
        + "nalle.puh.com\t"
        + "23234\t"
        + "serviceName\t"
        + "tst\t"
        + "info\t"
        + "this is a test\n";
	private LogMessage msg2;

    @Test
    public void testAscendingOrder() {
		try {
			msg1 = LogMessage.parseNativeFormat(msgstr1.trim());
			msg2 = LogMessage.parseNativeFormat(msgstr2.trim());
		} catch (InvalidLogFormatException e) {
			fail();
		}
		Set<LogMessage> msglist = new TreeSet<LogMessage>(new LogMessageTimeComparator());
		msglist.add(msg1);
		msglist.add(msg2);

		Iterator<LogMessage> it = msglist.iterator();
		assertEquals(msg1, it.next());
		assertEquals(msg2, it.next());
	}

    @Test
    public void testDescendingOrder() {
		try {
			msg1 = LogMessage.parseNativeFormat(msgstr1.trim());
			msg2 = LogMessage.parseNativeFormat(msgstr2.trim());
		} catch (InvalidLogFormatException e) {
			fail();
		}
		Set<LogMessage> msglist = new TreeSet<LogMessage>(new LogMessageTimeComparator(false));
		msglist.add(msg1);
		msglist.add(msg2);

		// note, last gets fetched first
		Iterator<LogMessage> it = msglist.iterator();
		assertEquals(msg2, it.next());
		assertEquals(msg1, it.next());
	}
}
