// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.system;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CommandLineParserTestCase {

    @Test
    public void testParse1() {
        String[] args = new String[] {"-d", "-f", "hello.txt"};
        CommandLineParser parser = new CommandLineParser(args);
        parser.addLegalBinarySwitch("-f");
        parser.addLegalUnarySwitch("-d");
        parser.parse();
        assertNull(parser.getBinarySwitches().get("-g"));
        assertFalse(parser.getUnarySwitches().contains("-e"));
        assertEquals(parser.getBinarySwitches().get("-f"), "hello.txt");
        assertTrue(parser.getUnarySwitches().contains("-d"));
        assertFalse(parser.getArguments().contains("-d"));
        assertFalse(parser.getArguments().contains("-f"));
        assertFalse(parser.getArguments().contains("-hello.txt"));
        assertEquals(parser.getArguments().size(), 0);
    }

    @Test
    public void testParse2() {
        String[] args = new String[] {"-d", "-f", "hello.txt", "-XX", "myName", "-o", "output file", "myLastField"};
        CommandLineParser parser = new CommandLineParser("progname", args);
        parser.setArgumentExplanation("Bla bla1");
        parser.setExtendedHelpText("Bla bla blaaaaaaa bla2");
        parser.addLegalBinarySwitch("-f");
        parser.addLegalBinarySwitch("-o");
        parser.addLegalUnarySwitch("-d");
        parser.addLegalUnarySwitch("-XX");
        parser.parse();
        assertNull(parser.getBinarySwitches().get("-g"));
        assertFalse(parser.getUnarySwitches().contains("-e"));
        assertEquals(parser.getBinarySwitches().get("-f"), "hello.txt");
        assertTrue(parser.getUnarySwitches().contains("-d"));
        assertTrue(parser.getUnarySwitches().contains("-XX"));
        assertEquals(parser.getBinarySwitches().get("-o"), "output file");
        assertTrue(parser.getArguments().contains("myName"));
        assertTrue(parser.getArguments().contains("myLastField"));
        assertEquals(parser.getUnarySwitches().size(), 2);
        assertEquals(parser.getBinarySwitches().size(), 2);
        assertEquals(parser.getArguments().size(), 2);
        assertEquals(parser.getArguments().get(0), "myName");
        assertEquals(parser.getArguments().get(1), "myLastField");
        assertEquals(parser.getUnarySwitches().get(0), "-d");
        assertEquals(parser.getUnarySwitches().get(1), "-XX");

        try {
            parser.usageAndThrow();
            fail("usageAndThrow didn't throw");
        } catch (Exception e) {
            assertTrue(e.getMessage().replaceAll("\n", "").matches(".*bla1.*"));
            assertTrue(e.getMessage().replaceAll("\n", "").matches(".*bla2.*"));
        }
    }

    @Test
    public void testIllegal() {
        String[] args = new String[] {"-d", "-f", "hello.txt", "-XX", "myName", "-o", "output file", "myLastField"};
        CommandLineParser parser = new CommandLineParser(args);
        parser.addLegalBinarySwitch("-f");
        parser.addLegalBinarySwitch("-o");
        parser.addLegalUnarySwitch("-d");
        try {
            parser.parse();
            fail("Parse of cmd line with illegal arg worked");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("\nusage"));
        }

        args = new String[] {"-d", "-f", "hello.txt", "-XX", "myName", "-o", "output file", "myLastField"};
        parser = new CommandLineParser(args);
        parser.addLegalBinarySwitch("-f");
        parser.addLegalUnarySwitch("-d");
        parser.addLegalUnarySwitch("-XX");
        try {
            parser.parse();
            fail("Parse of cmd line with illegal arg worked");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("\nusage"));
        }
    }

    @Test
    public void testRequired() {
        String[] args1 = new String[] {"-d", "-f", "hello.txt", "-XX", "myName", "-o", "output file", "myLastField"};
        String[] args2 = new String[] {"-XX", "myName", "-o", "output file", "myLastField"};
        CommandLineParser parser = new CommandLineParser(args1);
        parser.addLegalBinarySwitch("-f", "test1");
        parser.addRequiredBinarySwitch("-o", "test2");
        parser.addLegalUnarySwitch("-d", "test3");
        parser.addLegalUnarySwitch("-XX", "test4");
        parser.parse();

        parser = new CommandLineParser(args2);
        parser.addRequiredBinarySwitch("-o", "test2");
        parser.addLegalUnarySwitch("-XX", "test4");
        parser.parse();
        assertEquals(parser.getBinarySwitches().size(),1);
        assertEquals(parser.getUnarySwitches().size(),1);

        parser = new CommandLineParser(args2);
        parser.addLegalUnarySwitch("-XX", "test4");
        parser.addRequiredBinarySwitch("-f", "test5");
        parser.addRequiredBinarySwitch("-o", "test6");
        try {
            parser.parse();
            fail("Illegal cmd line parsed");
        } catch (Exception e) {
            assertTrue(e.getMessage().startsWith("\nusage"));
        }

        args1 = new String[] {"-d"};
        parser = new CommandLineParser(args1);
        parser.addRequiredUnarySwitch("-d", "(required, there are so many bugs)");
        try {
            parser.addLegalBinarySwitch("-d");
            fail("Switch clobber didn't throw");
        } catch (Exception e) {
            assertTrue(e.getMessage().matches(".*already.*"));
        }
        parser.parse();
        assertEquals(parser.getUnarySwitches().get(0), "-d");
    }

}
