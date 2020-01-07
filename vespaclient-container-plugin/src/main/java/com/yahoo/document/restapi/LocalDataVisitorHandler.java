// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.json.JsonWriter;
import com.yahoo.documentapi.DumpVisitorDataHandler;
import com.yahoo.exception.ExceptionUtils;

import java.nio.charset.StandardCharsets;

/**
 * Handling data from visit.
 *
 * @author dybis
 */
class LocalDataVisitorHandler extends DumpVisitorDataHandler {

    StringBuilder commaSeparatedJsonDocuments = new StringBuilder();
    final StringBuilder errors = new StringBuilder();

    private boolean isFirst = true;
    private final Object monitor = new Object();

    String getErrors() {
        return errors.toString();
    }

    String getCommaSeparatedJsonDocuments() {
        return commaSeparatedJsonDocuments.toString();
    }

    @Override
    public void onDocument(Document document, long l) {
        try {
            final String docJson = new String(JsonWriter.toByteArray(document), StandardCharsets.UTF_8.name());
            synchronized (monitor) {
                if (!isFirst) {
                    commaSeparatedJsonDocuments.append(",");
                }
                isFirst = false;
                commaSeparatedJsonDocuments.append(docJson);
            }
        } catch (Exception e) {
            synchronized (monitor) {
                errors.append(ExceptionUtils.getStackTraceAsString(e)).append("\n");
            }
        }
    }

    // TODO: Not sure if we should support removal or not. Do nothing here maybe?
    @Override
    public void onRemove(DocumentId documentId) {
        try {
            final String removeJson = new String(JsonWriter.documentRemove(documentId), StandardCharsets.UTF_8.name());
            synchronized (monitor) {
                if (!isFirst) {
                    commaSeparatedJsonDocuments.append(",");
                }
                isFirst = false;
                commaSeparatedJsonDocuments.append(removeJson);
            }
        } catch (Exception e) {
            synchronized (monitor) {
                errors.append(ExceptionUtils.getStackTraceAsString(e)).append("\n");
            }
        }
    }

}
