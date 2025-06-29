// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.builder.xml;

import com.yahoo.text.Utf8;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author hmusum
 */
public class XmlErrorHandlingTest {

    @Test
    void requireExceptionWithSourceAndFilenameAndLineNumber() {
        try {
            XmlHelper.getDocument(Utf8.createReader("src/test/cfg/application/invalid-services-syntax/services.xml"), "services.xml");
        } catch (Exception e) {
            assertEquals("Invalid XML in services.xml: The element type \"config\" must be terminated by the matching end-tag \"</config>\". [7:5]",
                    e.getMessage());
        }
    }


    @Test
    void requireExceptionWithLineNumber() {
        try {
            XmlHelper.getDocumentBuilder().parse(
                    new InputSource(Utf8.createReader("src/test/cfg/application/invalid-services-syntax/services.xml")));
        } catch (Exception e) {
            assertEquals("Invalid XML (unknown source): The element type \"config\" must be terminated by the matching end-tag \"</config>\". [7:5]",
                    e.getMessage());
        }
    }

}
