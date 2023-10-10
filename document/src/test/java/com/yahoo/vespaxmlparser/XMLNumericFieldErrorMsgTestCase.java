// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.serialization.DeserializationException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.29
 */
public class XMLNumericFieldErrorMsgTestCase {

    private static DocumentTypeManager setupTypes() {
        DocumentTypeManager dtm = new DocumentTypeManager();
        DocumentType docType = new DocumentType("doctype");
        docType.addField("bytefield", DataType.BYTE);
        docType.addField("intfield", DataType.INT);
        docType.addField("longfield", DataType.LONG);
        docType.addField("floatfield", DataType.FLOAT);
        docType.addField("doublefield", DataType.DOUBLE);
        dtm.register(docType);
        return dtm;
    }

    @Test
    public void requireDescriptiveErrorMsgForFloats() throws Exception {
        DocumentTypeManager dtm = setupTypes();
        try {
            VespaXMLDocumentReader documentReader = new VespaXMLDocumentReader(
                    new ByteArrayInputStream(("<document id=\"id:ns:doctype::bar\" type=\"doctype\">" +
                                              "  <floatfield></floatfield>" +
                                              "</document>").getBytes(StandardCharsets.UTF_8)), dtm);
            new Document(documentReader);
            fail("Sorry mac");
        } catch (DeserializationException e) {
            assertTrue(e.getMessage().contains("Field 'floatfield': Invalid float \"\""));
        }
    }

    @Test
    public void requireDescriptiveErrorMsgForDoubles() throws Exception {
        DocumentTypeManager dtm = setupTypes();
        try {
            VespaXMLDocumentReader documentReader = new VespaXMLDocumentReader(
                    new ByteArrayInputStream(("<document id=\"id:ns:doctype::bar\" type=\"doctype\">" +
                                              "  <doublefield></doublefield>" +
                                              "</document>").getBytes(StandardCharsets.UTF_8)), dtm);
            new Document(documentReader);
            fail("Sorry mac");
        } catch (DeserializationException e) {
            assertTrue(e.getMessage().contains("Field 'doublefield': Invalid double \"\""));
        }
    }

    @Test
    public void requireDescriptiveErrorMsgForLongs() throws Exception {
        DocumentTypeManager dtm = setupTypes();
        try {
            VespaXMLDocumentReader documentReader = new VespaXMLDocumentReader(
                    new ByteArrayInputStream(("<document id=\"id:ns:doctype::bar\" type=\"doctype\">" +
                                              "  <longfield></longfield>" +
                                              "</document>").getBytes(StandardCharsets.UTF_8)), dtm);
            new Document(documentReader);
            fail("Sorry mac");
        } catch (DeserializationException e) {
            assertTrue(e.getMessage().contains("Field 'longfield': Invalid long \"\""));
        }
    }

    @Test
    public void requireDescriptiveErrorMsgForIntegers() throws Exception {
        DocumentTypeManager dtm = setupTypes();
        try {
            VespaXMLDocumentReader documentReader = new VespaXMLDocumentReader(
                    new ByteArrayInputStream(("<document id=\"id:ns:doctype::bar\" type=\"doctype\">" +
                                              "  <intfield></intfield>" +
                                              "</document>").getBytes(StandardCharsets.UTF_8)), dtm);
            new Document(documentReader);
            fail("Sorry mac");
        } catch (DeserializationException e) {
            assertTrue(e.getMessage().contains("Field 'intfield': Invalid integer \"\""));
        }
    }

    @Test
    public void requireDescriptiveErrorMsgForBytes() throws Exception {
        DocumentTypeManager dtm = setupTypes();
        try {
            VespaXMLDocumentReader documentReader = new VespaXMLDocumentReader(
                    new ByteArrayInputStream(("<document id=\"id:ns:doctype::bar\" type=\"doctype\">" +
                                              "  <bytefield></bytefield>" +
                                              "</document>").getBytes(StandardCharsets.UTF_8)), dtm);
            new Document(documentReader);
            fail("Sorry mac");
        } catch (DeserializationException e) {
            assertTrue(e.getMessage().contains("Field 'bytefield': Invalid byte \"\""));
        }
    }



}

