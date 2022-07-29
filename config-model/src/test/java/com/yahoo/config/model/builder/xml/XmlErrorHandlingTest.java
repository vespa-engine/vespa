// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.builder.xml;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import java.io.FileReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author hmusum
 */
public class XmlErrorHandlingTest {

    @Test
    void requireExceptionWithSourceAndFilenameAndLineNumber() {
        try {
            XmlHelper.getDocument(new FileReader("src/test/cfg/application/invalid-services-syntax/services.xml"), "services.xml");
        } catch (Exception e) {
            assertEquals("Invalid XML in services.xml: The element type \"config\" must be terminated by the matching end-tag \"</config>\". [7:5]",
                    e.getMessage());
        }
    }


    @Test
    void requireExceptionWithLineNumber() {
        try {
            XmlHelper.getDocumentBuilder().parse(
                    new InputSource(new FileReader("src/test/cfg/application/invalid-services-syntax/services.xml")));
        } catch (Exception e) {
            assertEquals("Invalid XML (unknown source): The element type \"config\" must be terminated by the matching end-tag \"</config>\". [7:5]",
                    e.getMessage());
        }
    }

}
