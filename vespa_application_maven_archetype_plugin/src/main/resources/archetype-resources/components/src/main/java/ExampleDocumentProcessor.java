// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ${package};

import com.yahoo.document.*;
import com.yahoo.docproc.*;

import com.yahoo.example.ExampleConfig;
import com.yahoo.document.datatypes.StringFieldValue;

/**
 * An example document processor.
 *
 * @author  Joe Developer
 */
public class ExampleDocumentProcessor extends DocumentProcessor {
    private final String message;
    private final String field = "message";

    public ExampleDocumentProcessor(ExampleConfig config) {
        message = config.message();
    }


    public Progress process(Processing processing) {
        for (DocumentOperation op : processing.getDocumentOperations()) {
            if (op instanceof DocumentPut) {
                DocumentPut put = (DocumentPut) op;
                Document document = put.getDocument();
                document.setFieldValue(field, new StringFieldValue(message));
            } else if (base instanceof DocumentUpdate) {
                DocumentUpdate update = (DocumentUpdate) base;
                //TODO do something to 'update' here
            } else if (base instanceof DocumentRemove) {
                DocumentRemove remove = (DocumentRemove) base;
                //TODO do something to 'remove' here
            }
        }
        return Progress.DONE;
    }

}
