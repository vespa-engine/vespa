// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.w3c.dom.Document;

import java.io.Reader;
import java.io.StringReader;

import static org.junit.Assert.assertTrue;

/**
 * Utilities for tests
 *
 * @author hmusum
 */
public class TestBase {
    static {
        XMLUnit.setIgnoreWhitespace(true);
    }

    static void assertDocument(String expected, Document output) {
        Document expectedDoc = Xml.getDocument(new StringReader(expected));
        Diff diff = new Diff(expectedDoc, output);
        assertTrue(diff.toString(), diff.identical());
    }

    public static void assertDocument(String expectedDocument, Reader document) {
        Document output = Xml.getDocument(document);
        assertDocument(expectedDocument, output);
    }
}
