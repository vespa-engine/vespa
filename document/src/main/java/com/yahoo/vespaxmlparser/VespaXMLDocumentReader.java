// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.serialization.DocumentReader;

import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;

/**
 * XML parser that reads Vespa documents from an XML stream.
 *
 * @author thomasg
 */
public class VespaXMLDocumentReader extends VespaXMLFieldReader implements DocumentReader {

    /**
     * Creates a reader that reads from the given file.
     */
    public VespaXMLDocumentReader(String fileName, DocumentTypeManager docTypeManager) throws Exception {
        super(fileName, docTypeManager);
    }

    /**
     * Creates a reader that reads from the given stream.
     */
    public VespaXMLDocumentReader(InputStream stream, DocumentTypeManager docTypeManager) throws Exception {
        super(stream, docTypeManager);
    }

    /**
     * Creates a reader that reads using the given reader. This is useful if the document is part of a greater
     * XML stream.
     */
    public VespaXMLDocumentReader(XMLStreamReader reader, DocumentTypeManager docTypeManager) {
        super(reader, docTypeManager);
    }

    /**
     * Reads one document from the stream. Function assumes that the current element in the stream is
     * the start tag for the document.
     *
     * @param document the document to be read
     */
    public void read(Document document) {
        read(null, document);
    }
}
