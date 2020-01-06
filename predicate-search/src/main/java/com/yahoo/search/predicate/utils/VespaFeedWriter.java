// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.utils;

import com.google.common.html.HtmlEscapers;
import com.yahoo.document.predicate.Predicate;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * @author Magnar Nedland
 */
public class VespaFeedWriter extends BufferedWriter {

    private String namespace;
    private String documentType;

    VespaFeedWriter(Writer writer, String namespace, String documentType) throws IOException {
        super(writer);
        this.namespace = namespace;
        this.documentType = documentType;

        this.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
        this.append("<vespafeed>\n");
    }

    @Override
    public void close() throws IOException {
        this.append("</vespafeed>\n");
        super.close();
    }

    public void writePredicateDocument(int id, String fieldName, Predicate predicate) {
        try {
            this.append(String.format("<document documenttype=\"%2$s\" documentid=\"id:%1$s:%2$s::%3$d\">\n",
                    namespace, documentType, id));
            this.append("<" + fieldName + ">" + HtmlEscapers.htmlEscaper().escape(predicate.toString()) + "</" + fieldName + ">\n");
            this.append("</document>\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
