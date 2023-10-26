// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.serialization.DeserializationException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * @author thomasg
 */
public class VespaXMLReader {
    DocumentTypeManager docTypeManager;
    XMLStreamReader reader;

    public VespaXMLReader(String fileName, DocumentTypeManager docTypeManager) throws Exception {
        this(new FileInputStream(fileName), docTypeManager);
    }

    public VespaXMLReader(InputStream stream, DocumentTypeManager docTypeManager) throws Exception {
        this.docTypeManager = docTypeManager;
        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        xmlInputFactory.setProperty("javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
        reader = xmlInputFactory.createXMLStreamReader(stream);
    }

    public VespaXMLReader(XMLStreamReader reader, DocumentTypeManager docTypeManager) {
        this.docTypeManager = docTypeManager;
        this.reader = reader;
    }

    protected RuntimeException newDeserializeException(String message) {
        return new DeserializationException(message + " (at line " + reader.getLocation().getLineNumber() + ", column " + reader.getLocation().getColumnNumber() + ")");
    }

    protected RuntimeException newException(Exception e) {
        return new DeserializationException(e.getMessage() + " (at line " + reader.getLocation().getLineNumber() + ", column " + reader.getLocation().getColumnNumber() + ")", e);
    }

    protected void skipToEnd(String tagName) throws XMLStreamException {
        while (reader.hasNext()) {
            if (reader.getEventType() == XMLStreamReader.END_ELEMENT && tagName.equals(reader.getName().toString())) {
                return;
            }
            reader.next();
        }
        throw new DeserializationException("Missing end tag for element '" + tagName + "'" + reader.getLocation());
    }

    public static boolean isBase64EncodingAttribute(String attributeName, String attributeValue) {
        return "binaryencoding".equals(attributeName) &&
               "base64".equalsIgnoreCase(attributeValue);
    }

    public static boolean isBase64EncodedElement(XMLStreamReader reader) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if (isBase64EncodingAttribute(reader.getAttributeName(i).toString(),
                                          reader.getAttributeValue(i)))
            {
                return true;
            }
        }
        return false;
    }
}
