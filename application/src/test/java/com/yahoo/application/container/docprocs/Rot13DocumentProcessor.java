// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.docprocs;

import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.datatypes.StringFieldValue;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Einar M R Rosenvinge
 */
public class Rot13DocumentProcessor extends DocumentProcessor {
    private static final String FIELD_NAME = "title";

    private AtomicInteger counter = new AtomicInteger(1);

    public static String rot13(String s) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'm' || c >= 'A' && c <= 'M') {
                c += 13;
            } else if (c >= 'n' && c <= 'z' || c >= 'N' && c <= 'Z') {
                c -= 13;
            }
            output.append(c);
        }
        return output.toString();
    }

    @Override
    public Progress process(Processing processing) {
        int oldVal = counter.getAndIncrement();
        if ((oldVal % 3) != 0) {
            return Progress.LATER;
        }

        for (DocumentOperation op : processing.getDocumentOperations()) {
            if (op instanceof DocumentPut) {
                Document document = ((DocumentPut)op).getDocument();

                StringFieldValue oldTitle = (StringFieldValue) document.getFieldValue(FIELD_NAME);
                if (oldTitle != null) {
                    document.setFieldValue(FIELD_NAME, rot13(oldTitle.getString()));
                }
            }
        }
        return Progress.DONE;
    }
}
