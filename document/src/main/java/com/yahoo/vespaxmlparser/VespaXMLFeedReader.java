// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.TestAndSetCondition;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * XML parser for Vespa document XML.
 *
 * Parses an entire document "feed", which consists of a vespafeed element containing
 * zero or more instances of documents, updates or removes.
 *
 * Standard usage is to create an Operation object and call read(Operation) until
 * operation.getType() returns OperationType.INVALID.
 *
 * If you are looking to parse only a single document or update, use VespaXMLDocumentReader
 * or VespaXMLUpdateReader respectively.
 */
public class VespaXMLFeedReader extends VespaXMLReader implements FeedReader {

    /**
     * Creates a reader that reads from the given file.
     */
    public VespaXMLFeedReader(String fileName, DocumentTypeManager docTypeManager) throws Exception {
        super(fileName, docTypeManager);
        readInitial();
    }

    /**
     * Creates a reader that reads from the given stream.
     */
    public VespaXMLFeedReader(InputStream stream, DocumentTypeManager docTypeManager) throws Exception {
        super(stream, docTypeManager);
        readInitial();
    }

    /**
     * Skips the initial "vespafeed" tag.
     */
    private void readInitial() throws Exception {
        boolean found = false;

        while (reader.hasNext()) {
            int type = reader.next();
            if (type == XMLStreamReader.START_ELEMENT) {
                if ("vespafeed".equals(reader.getName().toString())) {
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            throw newDeserializeException("Feed information must be contained within a \"vespafeed\" element");
        }
    }

    /**
     * <p>Reads all operations from the XML stream and puts into a list. Note
     * that if the XML stream is large, this may cause out of memory errors, so
     * make sure to use this only with small streams.</p>
     *
     * @return The list of all read operations.
     */
    public List<FeedOperation> readAll() throws Exception {
        List<FeedOperation> list = new ArrayList<>();
        while (true) {
            FeedOperation op = read();
            if (op.getType() == FeedOperation.Type.INVALID) {
                return list;
            } else {
                list.add(op);
            }
        }
    }

    /* (non-Javadoc)
     * @see com.yahoo.vespaxmlparser.FeedReader#read(com.yahoo.vespaxmlparser.VespaXMLFeedReader.Operation)
     */
    @Override
    public FeedOperation read() throws Exception {
        String startTag = null;
        try {
            while (reader.hasNext()) {
                int type = reader.next();

                if (type == XMLStreamReader.START_ELEMENT) {
                    startTag = reader.getName().toString();

                    if ("document".equals(startTag)) {
                        VespaXMLDocumentReader documentReader = new VespaXMLDocumentReader(reader, docTypeManager);
                        DocumentPut put = new DocumentPut(new Document(documentReader));
                        put.setCondition(TestAndSetCondition.fromConditionString(documentReader.getCondition()));
                        return new DocumentFeedOperation(put);
                    } else if ("update".equals(startTag)) {
                        VespaXMLUpdateReader updateReader = new VespaXMLUpdateReader(reader, docTypeManager);
                        DocumentUpdate update = new DocumentUpdate(updateReader);
                        update.setCondition(TestAndSetCondition.fromConditionString(updateReader.getCondition()));
                        return new DocumentUpdateFeedOperation(update);
                    } else if ("remove".equals(startTag)) {
                        DocumentId documentId = null;

                        Optional<String> condition = Optional.empty();
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            final String attributeName = reader.getAttributeName(i).toString();
                            if ("documentid".equals(attributeName) || "id".equals(attributeName)) {
                                documentId = new DocumentId(reader.getAttributeValue(i));
                            } else if ("condition".equals(attributeName)) {
                                condition = Optional.of(reader.getAttributeValue(i));
                            }
                        }

                        if (documentId == null) {
                            throw newDeserializeException("Missing \"documentid\" attribute for remove operation");
                        }
                        DocumentRemove remove = new DocumentRemove(documentId);
                        remove.setCondition(TestAndSetCondition.fromConditionString(condition));
                        return new RemoveFeedOperation(remove);
                    } else {
                        throw newDeserializeException("Element \"" + startTag + "\" not allowed in this context");
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw(e);
            // Skip to end of current tag with other exceptions.
        } catch (Exception e) {
            try {
                if (startTag != null) {
                    skipToEnd(startTag);
                }
            } catch (Exception ignore) {
            }

            throw(e);
        }
        return FeedOperation.INVALID;
    }

}
