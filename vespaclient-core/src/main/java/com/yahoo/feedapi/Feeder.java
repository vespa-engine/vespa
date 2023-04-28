// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.vespaxmlparser.FeedOperation;
import com.yahoo.vespaxmlparser.FeedReader;

/**
 * Base class for unpacking document operation streams and pushing to feed
 * access points.
 *
 * @author Thomas Gundersen
 * @author steinar
 */
public abstract class Feeder {

    protected final InputStream stream;
    protected final DocumentTypeManager docMan;
    protected List<String> errors = new LinkedList<>();
    private boolean doAbort = true;
    private boolean createIfNonExistent = false;
    private final VespaFeedSender sender;
    private static final int MAX_ERRORS = 10;

    protected Feeder(DocumentTypeManager docMan, VespaFeedSender sender, InputStream stream) {
        this.docMan = docMan;
        this.sender = sender;
        this.stream = stream;
    }

    public void setAbortOnDocumentError(boolean doAbort) {
        this.doAbort = doAbort;
    }

    public void setCreateIfNonExistent(boolean value) {
        this.createIfNonExistent = value;
    }

    private void addException(Exception e) {
        String message;
        if (e.getMessage() != null) {
            message = e.getMessage().replaceAll("\"", "'");
        } else {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            message = "(no message) " + sw;
        }
        addError("ERROR: " + message);
    }

    private void addError(String error) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(error);
        } else if (errors.size() == MAX_ERRORS) {
            errors.add("Reached maximum limit of errors (" + MAX_ERRORS + "). Not collecting any more.");
        }
    }

    protected abstract FeedReader createReader() throws Exception;

    public List<String> parse() {
        FeedReader reader;

        try {
            reader = createReader();
        } catch (Exception e) {
            addError("ERROR: " + e.getClass().toString() + ": " + e.getMessage().replaceAll("\"", "'"));
            return errors;
        }

        while (!sender.isAborted()) {
            try {
                FeedOperation op = reader.read();
                if (createIfNonExistent) {
                    if (op.getDocumentUpdate() != null) {
                        op.getDocumentUpdate().setCreateIfNonExistent(true);
                    }
                    if (op.getDocumentPut() != null) {
                        op.getDocumentPut().setCreateIfNonExistent(true);
                    }
                }
                if (op.getType() == FeedOperation.Type.INVALID) break; // Done feeding
                sender.sendOperation(op);
            } catch (XMLStreamException | NullPointerException e) {
                addException(e);
                break;
            } catch (Exception e) {
                addException(e);
                if (doAbort) {
                    break;
                }
            }
        }

        return errors;
    }

}
