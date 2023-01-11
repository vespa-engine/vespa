// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import org.w3c.dom.Document;

import javax.xml.transform.TransformerException;
import java.io.Reader;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;

/**
 * Utilities for tests
 *
 * @author hmusum
 */
public class TestBase {

    static void assertDocument(String expected, Document output) {
        try {
            assertEquals(Xml.documentAsString(Xml.getDocument(new StringReader(expected)), true),
                         Xml.documentAsString(output, true));
        }
        catch (TransformerException e) {
            throw new AssertionError(e);
        }
    }

    public static void assertDocument(String expectedDocument, Reader document) {
        Document output = Xml.getDocument(document);
        assertDocument(expectedDocument, output);
    }
}
